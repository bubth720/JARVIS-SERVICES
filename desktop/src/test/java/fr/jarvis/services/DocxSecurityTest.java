package fr.jarvis.services;

import static org.junit.jupiter.api.Assertions.*;

import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.shared.api.Contracts.Failure;
import java.io.*;
import java.nio.charset.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

class DocxSecurityTest {
  private static byte[] archive(String xml, Charset encoding) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(out)) {
      z.putNextEntry(new ZipEntry("word/document.xml"));
      z.write(xml.getBytes(encoding));
      z.closeEntry();
    }
    return out.toByteArray();
  }

  private static String document(String prefix, String body) {
    return prefix
        + "<w:document"
        + " xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>"
        + body
        + "</w:t></w:r></w:p></w:body></w:document>";
  }

  @Test
  void utf16AndBuiltInEntitiesRemainSupported() throws Exception {
    assertEquals(
        "café & local\n",
        Documents.readDocx(
            archive(
                document("<?xml version=\"1.0\" encoding=\"UTF-16\"?>", "café &amp; local"),
                StandardCharsets.UTF_16)));
  }

  @Test
  void dtdIsRejectedForUtf8AndUtf16() throws Exception {
    for (Charset charset : new Charset[] {StandardCharsets.UTF_8, StandardCharsets.UTF_16}) {
      String header = "<?xml version=\"1.0\" encoding=\"" + charset.name() + "\"?>";
      String xml =
          document(
              header + "<!DOCTYPE w:document [<!ENTITY x SYSTEM 'file:///never-read'>]>", "&x;");
      SAXException e =
          assertThrows(SAXException.class, () -> Documents.readDocx(archive(xml, charset)));
      assertTrue(e.getMessage().contains("DTD_FORBIDDEN"));
    }
  }

  @Test
  void malformedXmlIsRejected() throws Exception {
    assertThrows(
        SAXException.class, () -> Documents.readDocx(archive("<broken>", StandardCharsets.UTF_8)));
  }

  @Test
  void extractedTextIsBounded() throws Exception {
    Failure e =
        assertThrows(
            Failure.class,
            () ->
                Documents.readDocx(
                    archive(
                        document("", "x".repeat(Documents.MAX_TEXT + 1)), StandardCharsets.UTF_8)));
    assertEquals("TEXT_TOO_LARGE", e.code);
  }
}
