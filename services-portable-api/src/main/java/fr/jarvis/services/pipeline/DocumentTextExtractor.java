package fr.jarvis.services.pipeline;

import fr.jarvis.services.modules.documents.DocumentContent;
import fr.jarvis.services.modules.guard.AccessGuard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Read-only extraction port. No import, Store writes, IPC or permissions are inferred. */
public final class DocumentTextExtractor {
  @FunctionalInterface public interface PdfPages { List<String> extractPages(byte[] bytes) throws Exception; }
  public static final class Page {
    public final String sourceId, sourceName, sourceSha256, text, textSha256;
    /** PDF page is one-based; zero means unpaginated text, never an invented page. */
    public final int page;
    public Page(String name, String sourceSha256, int page, String text) {
      if (name == null || name.isEmpty() || name.length() > 200 || page < 0 || page > 100
          || sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}") || text == null
          || text.length() > DocumentContent.MAX_TEXT) throw new Failure("INVALID_SOURCE");
      this.sourceId = sourceSha256; this.sourceName = name; this.sourceSha256 = sourceSha256;
      this.page = page; this.text = text; this.textSha256 = AccessGuard.digest(text);
    }
  }
  private final PdfPages pdf;
  public DocumentTextExtractor(PdfPages pdf) { this.pdf = pdf; }
  public List<Page> extract(String name, byte[] source, Task task) {
    task.check();
    if (name == null || name.isEmpty() || name.length() > 200 || source == null
        || source.length > DocumentContent.MAX_BYTES) throw new Failure("INPUT_TOO_LARGE");
    byte[] bytes = source.clone(); String digest = AccessGuard.digest(bytes);
    String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    try {
      List<String> texts;
      if (ext.equals("pdf")) {
        if (pdf == null) throw new Failure("PDF_UNAVAILABLE");
        texts = pdf.extractPages(bytes);
        if (texts == null || texts.isEmpty() || texts.size() > 100) throw new Failure("INVALID_PDF");
      } else if (ext.equals("docx")) texts = Collections.singletonList(DocumentContent.readDocx(bytes));
      else if (Arrays.asList("txt", "md", "csv").contains(ext)) {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        texts = Collections.singletonList(text);
      } else throw new Failure("UNSUPPORTED_FORMAT");
      List<Page> pages = new ArrayList<>(); long chars = 0;
      for (int i = 0; i < texts.size(); i++) {
        task.check(); String text = texts.get(i); if (text == null) throw new Failure("INVALID_SOURCE");
        chars += text.length(); if (chars > DocumentContent.MAX_TEXT) throw new Failure("TEXT_TOO_LARGE");
        pages.add(new Page(name, digest, ext.equals("pdf") ? i + 1 : 0, text));
      }
      if (pages.stream().allMatch(p -> p.text.trim().isEmpty())) throw new Failure(ext.equals("pdf") ? "OCR_REQUIRED" : "EMPTY_DOCUMENT");
      return Collections.unmodifiableList(pages);
    } catch (Failure e) { throw e; }
    catch (Exception e) { throw new Failure("EXTRACTION_FAILED"); }
  }
}
