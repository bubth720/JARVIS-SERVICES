package fr.jarvis.services.modules.documents;
import fr.jarvis.services.shared.api.Contracts.Failure;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
/** Common bounded stream codecs, extracted from the historical Documents implementation. */
public final class DocumentContent {
  private DocumentContent() {}
  public static final int MAX_BYTES = 2 * 1024 * 1024, MAX_TEXT = 250_000;
  public static String readDocx(byte[] bytes) throws Exception {
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (!entry.getName().equals("word/document.xml")) continue;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = zip.read(buffer)) != -1) {
          if (out.size() + n > MAX_BYTES) throw new Failure("INPUT_TOO_LARGE");
          out.write(buffer, 0, n);
        }
        return DocxXml.extract(out.toByteArray());
      }
    }
    throw new Failure("INVALID_DOCX");
  }

  public static byte[] docx(String text) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(out)) {
        zip(
            zip,
            "[Content_Types].xml",
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default"
                + " Extension=\"rels\""
                + " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default"
                + " Extension=\"xml\" ContentType=\"application/xml\"/><Override"
                + " PartName=\"/word/document.xml\""
                + " ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
        zip(
            zip,
            "_rels/.rels",
            "<Relationships"
                + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship"
                + " Id=\"rId1\""
                + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\""
                + " Target=\"word/document.xml\"/></Relationships>");
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        zip(
            zip,
            "word/document.xml",
            "<w:document"
                + " xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t"
                + " xml:space=\"preserve\">"
                + escaped
                + "</w:t></w:r></w:p></w:body></w:document>");
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new Failure("EXPORT_FAILED");
    }
  }

  private static void zip(ZipOutputStream zip, String name, String text) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(text.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
