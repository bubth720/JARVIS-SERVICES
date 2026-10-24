package fr.jarvis.services.pipeline;

import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.util.*;
import java.util.regex.*;

/** Read-only projection of selected extracted pages; no persistent corpus or concurrent index. */
public final class SelectedDocumentSearch {
  private SelectedDocumentSearch() {}
  public static List<EvidenceReportBuilder.Citation> search(
      List<DocumentTextExtractor.Page> pages, String query, Task task) {
    if (query == null || query.isEmpty() || query.length() > 500 || pages.size() > 100)
      throw new Failure("INVALID_INPUT");
    Pattern literal = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    List<EvidenceReportBuilder.Citation> hits = new ArrayList<>();
    for (DocumentTextExtractor.Page page : pages) {
      task.check();
      Matcher match = literal.matcher(page.text);
      if (match.find()) {
        int end = Math.min(page.text.length(), match.start() + 600);
        if (end < page.text.length() && Character.isHighSurrogate(page.text.charAt(end - 1))) end--;
        EvidenceReportBuilder.Citation citation = new EvidenceReportBuilder.Citation(page, match.start(), end);
        citation.verify(page); hits.add(citation);
        if (hits.size() == 50) break;
      }
    }
    task.check();
    return Collections.unmodifiableList(hits);
  }
}
