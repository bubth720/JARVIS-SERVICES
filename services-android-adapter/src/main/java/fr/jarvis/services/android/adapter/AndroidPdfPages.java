package fr.jarvis.services.android.adapter;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import fr.jarvis.services.modules.documents.DocumentContent;
import fr.jarvis.services.pipeline.DocumentTextExtractor;
import fr.jarvis.services.shared.api.Contracts.Failure;
import java.util.*;

/** Same bounded PDFBox page extraction used by the existing Android diagnostic. No OCR. */
final class AndroidPdfPages implements DocumentTextExtractor.PdfPages {
  public List<String> extractPages(byte[] bytes) throws Exception {
    if (bytes.length > DocumentContent.MAX_BYTES) throw new Failure("INPUT_TOO_LARGE");
    try (PDDocument document = PDDocument.load(bytes)) {
      int count = document.getNumberOfPages();
      if (count < 1 || count > 100) throw new Failure("INVALID_PDF");
      List<String> pages = new ArrayList<>();
      PDFTextStripper stripper = new PDFTextStripper();
      long chars = 0;
      for (int page = 1; page <= count; page++) {
        stripper.setStartPage(page); stripper.setEndPage(page);
        String text = stripper.getText(document); chars += text.length();
        if (chars > DocumentContent.MAX_TEXT) throw new Failure("TEXT_TOO_LARGE");
        pages.add(text);
      }
      return pages;
    }
  }
}
