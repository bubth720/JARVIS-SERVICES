package fr.jarvis.services.android;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import fr.jarvis.services.modules.documents.Documents;

public final class AndroidPdfText implements Documents.PdfText, fr.jarvis.services.pipeline.DocumentTextExtractor.PdfPages {
  public String extract(byte[] bytes) throws Exception {
    try (PDDocument d = PDDocument.load(bytes)) {
      if (d.getNumberOfPages() > 100) throw new IllegalArgumentException("Page limit");
      return new PDFTextStripper().getText(d);
    }
  }
  public java.util.List<String> extractPages(byte[] bytes) throws Exception {
    if (bytes == null || bytes.length > Documents.MAX_BYTES) throw new IllegalArgumentException("Byte limit");
    try (PDDocument d = PDDocument.load(bytes)) {
      if (d.getNumberOfPages() < 1 || d.getNumberOfPages() > 100) throw new IllegalArgumentException("Page limit");
      java.util.List<String> pages = new java.util.ArrayList<>();
      int chars = 0;
      for (int i = 1; i <= d.getNumberOfPages(); i++) {
        PDFTextStripper stripper = new PDFTextStripper(); stripper.setStartPage(i); stripper.setEndPage(i);
        String text = stripper.getText(d); chars += text.length();
        if (chars > Documents.MAX_TEXT) throw new IllegalArgumentException("Text limit");
        pages.add(text);
      }
      return java.util.Collections.unmodifiableList(pages);
    }
  }
}
