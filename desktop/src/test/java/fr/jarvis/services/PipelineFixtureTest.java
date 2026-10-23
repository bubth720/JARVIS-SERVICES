package fr.jarvis.services;

import fr.jarvis.services.pipeline.*;
import fr.jarvis.services.platform.*;
import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PipelineFixtureTest {
  @TempDir Path root;
  JdbcDatabase db; Guard guard; Store store; Documents documents; ApprovalGate approvals;
  Guard.Context host = new Guard.Context("synthetic-pipeline", new HashSet<>(Arrays.asList("confirm", "documents.read", "documents.export", "data.read")));
  DocumentTextExtractor extractor = new DocumentTextExtractor(new PdfBoxText());
  @BeforeEach void setup() {
    db = new JdbcDatabase(root.resolve("fixture.db")); store = new Store(db); guard = new Guard(root, store);
    documents = new Documents(guard, store, new PdfBoxText()); approvals = new ApprovalGate(store, guard);
  }
  @AfterEach void close() { db.close(); }
  byte[] pdf() throws Exception {
    try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      for (String text : List.of("Source alpha exact citation", "Source beta second page")) {
        PDPage page = new PDPage(); doc.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
          content.beginText(); content.setFont(PDType1Font.HELVETICA, 12); content.newLineAtOffset(50, 750); content.showText(text); content.endText();
        }
      }
      doc.save(out); return out.toByteArray();
    }
  }
  DocumentPipeline pipeline() { return new DocumentPipeline(guard, extractor, new DataTransformEngine(new Data())); }
  ToolInvocation approveExport(String name, String format, String text) {
    ToolInvocation original = new ToolInvocation(UUID.randomUUID().toString(), ExportWriter.DESCRIPTOR.id, "1.0.0", Map.of("path", name, "format", format, "text", text), 3000, null);
    String receipt = approvals.confirm(host, ExportWriter.DESCRIPTOR, original);
    return new ToolInvocation(original.requestId, original.toolId, original.version, original.arguments, 3000, receipt);
  }
  @Test void pdfAndCsvProduceVerifiedMarkdownAndDocx() throws Exception {
    byte[] original = pdf(); String originalDigest = Guard.digest(original);
    DocumentPipeline.Result result = pipeline().run(host, List.of(
        new DocumentPipeline.Source("fixture.pdf", original),
        new DocumentPipeline.Source("fixture.csv", "name,value\n a ,0.10\na,0.10\nb,0.20\n".getBytes(StandardCharsets.UTF_8))),
        List.of(new DataTransformEngine.Operation(DataTransformEngine.Kind.NORMALIZE, null, null),
          new DataTransformEngine.Operation(DataTransformEngine.Kind.DEDUPLICATE, null, null),
          new DataTransformEngine.Operation(DataTransformEngine.Kind.STATS, "value", null)), new Task(5000));
    assertEquals(3, result.report.citations.size()); assertEquals(1, result.report.citations.get(0).page);
    assertEquals(2, result.report.citations.get(1).page);
    assertEquals("0.30", result.tables.get(0).summaries.get(0).get("sum"));
    assertTrue(result.report.markdown.contains("Source alpha"));
    assertTrue(result.report.markdown.contains("aucune génération LLM"));
    assertEquals(originalDigest, Guard.digest(original));
    ExportWriter writer = new ExportWriter(documents, guard, approvals);
    writer.write(host, approveExport("report.md", "md", result.report.markdown), new Task(3000));
    writer.write(host, approveExport("report.docx", "docx", result.report.markdown), new Task(3000));
    assertEquals(result.report.markdown, Files.readString(root.resolve("report.md")));
    assertTrue(Documents.readDocx(Files.readAllBytes(root.resolve("report.docx"))).contains("Source beta"));
    assertTrue(result.progress.stream().anyMatch(p -> "complete".equals(p.get("phase"))));
  }
  @Test void sourceCitationCannotClaimMissingText() throws Exception {
    List<DocumentTextExtractor.Page> pages = extractor.extract("a.pdf", pdf(), new Task(3000));
    EvidenceReportBuilder.Report report = new EvidenceReportBuilder().build(pages, List.of());
    report.citations.get(0).verify(pages.get(0));
    DocumentTextExtractor.Page forged = new DocumentTextExtractor.Page("a.pdf", pages.get(0).sourceSha256, 1, "different text");
    assertThrows(Failure.class, () -> report.citations.get(0).verify(forged));
  }
  @Test void malformedUtf8AndPdfAreRejectedWithoutPersistence() {
    assertThrows(Failure.class, () -> extractor.extract("invalid.txt", new byte[]{(byte)0xc3,0x28}, new Task(3000)));
    assertThrows(Failure.class, () -> extractor.extract("invalid.pdf", new byte[]{1,2,3}, new Task(3000)));
    assertEquals(0, store.count("documents"));
  }
  @Test void maliciousDocxXmlIsRejected() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
      zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
      zip.write("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///forbidden'>]><w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:body><w:p><w:r><w:t>&e;</w:t></w:r></w:p></w:body></w:document>".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    DocumentPipeline.JobFailure failure = assertThrows(DocumentPipeline.JobFailure.class,
        () -> pipeline().run(host, List.of(new DocumentPipeline.Source("malicious.docx", out.toByteArray())), List.of(), new Task(3000)));
    assertEquals(DocumentPipeline.Stage.EXTRACT, failure.stage);
    assertEquals(0, store.count("documents"));
  }
  @Test void cancellationAndMissingPermissionsAreRefused() {
    Task cancelled = new Task(3000); cancelled.cancel();
    DocumentPipeline.JobFailure failure = assertThrows(DocumentPipeline.JobFailure.class,
        () -> pipeline().run(host, List.of(new DocumentPipeline.Source("a.txt", "text".getBytes(StandardCharsets.UTF_8))), List.of(), cancelled));
    assertEquals("CANCELLED", failure.code);
    assertThrows(Failure.class, () -> pipeline().run(new Guard.Context("denied", Set.of()),
        List.of(new DocumentPipeline.Source("a.txt", "text".getBytes(StandardCharsets.UTF_8))), List.of(), new Task(3000)));
  }
  @Test void atomicExportDoesNotReplaceOriginalAndRefusesReplay() throws Exception {
    ExportWriter writer = new ExportWriter(documents, guard, approvals);
    Files.writeString(root.resolve("existing.md"), "original");
    assertThrows(Failure.class, () -> writer.write(host, approveExport("existing.md", "md", "changed"), new Task(3000)));
    assertEquals("original", Files.readString(root.resolve("existing.md")));
    ToolInvocation invocation = approveExport("fresh.md", "md", "new"); writer.write(host, invocation, new Task(3000));
    assertThrows(Failure.class, () -> writer.write(host, invocation, new Task(3000)));
    assertEquals("new", Files.readString(root.resolve("fresh.md")));
    try (java.util.stream.Stream<Path> paths = Files.list(root)) { assertFalse(paths.anyMatch(p -> p.getFileName().toString().startsWith(".pipeline-"))); }
  }
  @Test void safePathsAndExactApprovalRemainMandatory() {
    ExportWriter writer = new ExportWriter(documents, guard, approvals);
    assertThrows(Failure.class, () -> writer.write(host, approveExport("../outside.md", "md", "test"), new Task(3000)));
    ToolInvocation unsigned = new ToolInvocation(UUID.randomUUID().toString(), ExportWriter.DESCRIPTOR.id, "1.0.0", Map.of("path", "fresh.md", "format", "md", "text", "test"), 3000, null);
    assertThrows(Failure.class, () -> writer.write(host, unsigned, new Task(3000)));
    assertFalse(Files.exists(root.resolve("fresh.md")));
  }
  @Test void unicodeSegmentsRemainExactAndCsvRejectsInvalidNumbers() {
    String raw = "a".repeat(599) + "😀e\u0301";
    DocumentPipeline.Result r = pipeline().run(host, List.of(new DocumentPipeline.Source("unicode.txt", raw.getBytes(StandardCharsets.UTF_8))), List.of(), new Task(3000));
    StringBuilder reconstructed = new StringBuilder(); for (DocumentPipeline.Fragment f : r.fragments) reconstructed.append(f.exactQuote());
    assertEquals(raw, reconstructed.toString()); assertFalse(Character.isHighSurrogate(r.fragments.get(0).exactQuote().charAt(598)));
    assertThrows(Failure.class, () -> new DataTransformEngine(new Data()).transform("value\nno-number\n",
        List.of(new DataTransformEngine.Operation(DataTransformEngine.Kind.STATS, "value", null)), new Task(3000)));
  }
}
