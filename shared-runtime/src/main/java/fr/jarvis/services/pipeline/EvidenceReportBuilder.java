package fr.jarvis.services.pipeline;

import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import java.util.*;

/** Citations bind exact substrings of the extracted source, not paraphrases or model output. */
public final class EvidenceReportBuilder {
  public static final class Citation {
    public final String sourceId, name, sourceSha256, textSha256, quote;
    public final int page, start, end;
    Citation(DocumentTextExtractor.Page source, int start, int end) {
      if (start < 0 || end <= start || end > source.text.length() || end - start > 600)
        throw new Failure("INVALID_CITATION");
      this.sourceId = source.sourceId; this.name = source.sourceName; this.sourceSha256 = source.sourceSha256;
      this.textSha256 = source.textSha256; this.page = source.page; this.start = start; this.end = end;
      this.quote = source.text.substring(start, end);
    }
    public void verify(DocumentTextExtractor.Page source) {
      if (!sourceId.equals(source.sourceId) || page != source.page || !sourceSha256.equals(source.sourceSha256)
          || !textSha256.equals(Guard.digest(source.text)) || end > source.text.length()
          || !quote.equals(source.text.substring(start, end))) throw new Failure("INVALID_CITATION");
    }
  }
  public static final class Report {
    public final List<Citation> citations; public final String markdown;
    Report(List<Citation> citations, String markdown) {
      this.citations = Collections.unmodifiableList(citations); this.markdown = markdown;
    }
  }
  public Report build(List<DocumentTextExtractor.Page> pages, List<DataTransformEngine.Result> tables) {
    if (pages == null || pages.size() > 100) throw new Failure("SOURCE_LIMIT");
    List<Citation> citations = new ArrayList<>(); StringBuilder md = new StringBuilder("# Rapport documentaire local\n\nExtraits vérifiés et transformations déterministes ; aucune génération LLM.\n");
    for (DocumentTextExtractor.Page page : pages) {
      if (page.text.trim().isEmpty()) continue;
      int end = Math.min(600, page.text.length());
      if (end < page.text.length() && Character.isHighSurrogate(page.text.charAt(end - 1))) end--;
      Citation c = new Citation(page, 0, end); c.verify(page); citations.add(c);
      md.append("\n## Source ").append(citations.size()).append(" : ").append(escape(page.sourceName)).append("\n")
          .append("SHA-256 : ").append(page.sourceSha256).append(" ; page : ").append(page.page == 0 ? "non paginé" : page.page)
          .append(" ; offsets UTF-16 : ").append(c.start).append("–").append(c.end).append("\n\n")
          .append("> ").append(c.quote.replace("\r", "").replace("\n", "\n> ")).append("\n");
    }
    for (DataTransformEngine.Result result : tables) {
      md.append("\n## Transformation CSV\n\nLignes : ").append(result.table.rows.size()).append("\n")
          .append("Opérations : ").append(result.table.history.toString()).append("\n\n")
          .append("```csv\n").append(result.table.csv().replace("```", "` ` `")).append("```\n");
      for (Map<String,String> summary : result.summaries) md.append(summary.toString()).append("\n");
    }
    if (md.length() > Documents.MAX_TEXT) throw new Failure("REPORT_TOO_LARGE");
    return new Report(citations, md.toString());
  }
  private static String escape(String text) { return text.replace("\n", " ").replace("\r", " ").replace("#", "\\#").replace("[", "\\[").replace("]", "\\]"); }
}
