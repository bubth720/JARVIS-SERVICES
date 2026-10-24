package fr.jarvis.services.finalization;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.pipeline.*;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.util.*;

/** Optional retrieval adapter over selected source revisions; no second durable corpus. */
public final class ConsultationService {
  public static final class Passage {
    public final String sourceSha256, textSha256, name, quote;
    public final int page, start, end;

    public Passage(DocumentTextExtractor.Page source, int start, int end) {
      if (start < 0 || end <= start || end > source.text.length() || end - start > 600)
        throw new Failure("INVALID_CITATION");
      sourceSha256 = source.sourceSha256;
      textSha256 = source.textSha256;
      name = source.sourceName;
      page = source.page;
      this.start = start;
      this.end = end;
      quote = source.text.substring(start, end);
    }
  }

  public interface SearchPort {
    List<Passage> search(String query, List<DocumentTextExtractor.Page> selected, Task task);
  }

  /** Independent qualification implementation; operates only on caller-selected pages. */
  public static final class SelectedFilesSearch implements SearchPort {
    public List<Passage> search(String query, List<DocumentTextExtractor.Page> pages, Task task) {
      List<Passage> out = new ArrayList<>();
      for (DocumentTextExtractor.Page page : pages) {
        task.check();
        java.util.regex.Matcher match =
            java.util.regex.Pattern.compile(
                    java.util.regex.Pattern.quote(query),
                    java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE)
                .matcher(page.text);
        int at = query.isEmpty() ? 0 : match.find() ? match.start() : -1;
        if (at < 0 || page.text.isEmpty()) continue;
        int start = Math.max(0, at - 80), end = Math.min(page.text.length(), start + 600);
        if (start > 0 && Character.isLowSurrogate(page.text.charAt(start))) start--;
        if (end < page.text.length() && Character.isHighSurrogate(page.text.charAt(end - 1))) end--;
        out.add(new Passage(page, start, end));
        if (out.size() == 20) break;
      }
      return out;
    }
  }

  private final Guard guard;
  private final SearchPort port;

  public ConsultationService(Guard guard, SearchPort optionalAdapter) {
    this.guard = guard;
    port = optionalAdapter == null ? new SelectedFilesSearch() : optionalAdapter;
  }

  public List<Passage> consult(
      Context host, String query, List<DocumentTextExtractor.Page> pages, Task task) {
    guard.require(host, "knowledge.read");
    if (query == null || query.length() > 500 || pages == null || pages.size() > 100)
      throw new Failure("INVALID_INPUT");
    List<Passage> found =
        port.search(query, Collections.unmodifiableList(new ArrayList<>(pages)), task);
    if (found == null || found.size() > 20) throw new Failure("INVALID_CITATION");
    for (Passage hit : found) {
      task.check();
      boolean verified = false;
      for (DocumentTextExtractor.Page p : pages)
        if (hit != null
            && hit.sourceSha256.equals(p.sourceSha256)
            && hit.textSha256.equals(p.textSha256)
            && hit.name.equals(p.sourceName)
            && hit.page == p.page
            && hit.start >= 0
            && hit.end <= p.text.length()
            && hit.end > hit.start
            && hit.quote.equals(p.text.substring(hit.start, hit.end))) verified = true;
      if (!verified) throw new Failure("INVALID_CITATION");
    }
    return Collections.unmodifiableList(new ArrayList<>(found));
  }

  public String report(List<Passage> passages) {
    StringBuilder out =
        new StringBuilder("Synthèse extractive sourcée — aucune interprétation automatique.\n");
    if (passages.isEmpty()) out.append("Aucun passage correspondant.\n");
    for (Passage p : passages)
      out.append("\nSource : ")
          .append(p.name)
          .append(" ; SHA-256 : ")
          .append(p.sourceSha256)
          .append(" ; page : ")
          .append(p.page == 0 ? "non paginé" : p.page)
          .append(" ; offsets UTF-16 : ")
          .append(p.start)
          .append("–")
          .append(p.end)
          .append("\n")
          .append(p.quote)
          .append("\n");
    DocumentService.bounded(out.toString());
    return out.toString();
  }
}
