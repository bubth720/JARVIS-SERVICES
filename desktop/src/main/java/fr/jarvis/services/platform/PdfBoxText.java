package fr.jarvis.services.platform;

import fr.jarvis.services.modules.documents.Documents;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public final class PdfBoxText implements Documents.PdfText, fr.jarvis.services.pipeline.DocumentTextExtractor.PdfPages {
  public String extract(byte[] bytes) throws Exception {
    try (PDDocument document = PDDocument.load(bytes)) {
      if (document.getNumberOfPages() > 100) throw new IllegalArgumentException("Page limit");
      return new PDFTextStripper().getText(document);
    }
  }
  public java.util.List<String> extractPages(byte[] bytes) throws Exception {
    if (bytes == null || bytes.length > Documents.MAX_BYTES) throw new IllegalArgumentException("Byte limit");
    try (PDDocument document = PDDocument.load(bytes)) {
      if (document.getNumberOfPages() < 1 || document.getNumberOfPages() > 100) throw new IllegalArgumentException("Page limit");
      java.util.List<String> pages = new java.util.ArrayList<>();
      int chars = 0;
      for (int i = 1; i <= document.getNumberOfPages(); i++) {
        PDFTextStripper stripper = new PDFTextStripper(); stripper.setStartPage(i); stripper.setEndPage(i);
        String text = stripper.getText(document); chars += text.length();
        if (chars > Documents.MAX_TEXT) throw new IllegalArgumentException("Text limit");
        pages.add(text);
      }
      return java.util.Collections.unmodifiableList(pages);
    }
  }
}
