package fr.jarvis.services.android;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import fr.jarvis.services.modules.documents.Documents;

public final class AndroidPdfText implements Documents.PdfText {
  public String extract(byte[] bytes) throws Exception {
    try (PDDocument d = PDDocument.load(bytes)) {
      if (d.getNumberOfPages() > 100) throw new IllegalArgumentException("Page limit");
      return new PDFTextStripper().getText(d);
    }
  }
}
