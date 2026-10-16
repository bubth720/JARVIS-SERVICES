package fr.jarvis.services.platform;
import fr.jarvis.services.modules.documents.Documents;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
public final class PdfBoxText implements Documents.PdfText {
    public String extract(byte[] bytes)throws Exception {try(PDDocument document=PDDocument.load(bytes)){if(document.getNumberOfPages()>100)throw new IllegalArgumentException("Page limit");return new PDFTextStripper().getText(document);}}
}
