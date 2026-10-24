package fr.jarvis.services;

import static org.junit.jupiter.api.Assertions.*;

import fr.jarvis.services.finalization.*;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.memory.Memory;
import fr.jarvis.services.pipeline.*;
import fr.jarvis.services.platform.*;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import fr.jarvis.services.workflow.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class FinalServicesTest {
  @TempDir Path root;
  JdbcDatabase db;
  LocalJobs jobs;
  Guard.Context host =
      new Guard.Context(
          "test-local",
          Set.of(
              "confirm",
              "documents.import",
              "documents.read",
              "documents.export",
              "knowledge.read",
              "data.read",
              "analyst.read",
              "actions.run",
              "memory.read",
              "memory.write"));

  Task task() {
    return new Task(30000);
  }

  @BeforeEach
  void open() {
    db = new JdbcDatabase(root.resolve("journal.sqlite"));
    PdfBoxText pdf = new PdfBoxText();
    jobs = new LocalJobs(root.resolve("files"), db, pdf, pdf);
  }

  @AfterEach
  void close() {
    jobs.close();
    db.close();
  }

  void reopen() {
    close();
    open();
    jobs.recover(host);
  }

  void complete(String id) {
    assertEquals("SUCCEEDED", jobs.advance(host, id, null).state);
    WorkflowEngine.Progress approval = jobs.advance(host, id, null);
    assertEquals("WAITING_APPROVAL", approval.state);
    String receipt = jobs.approve(host, approval.approvalRequest);
    assertEquals("SUCCEEDED", jobs.advance(host, id, receipt).state);
  }

  Path output(String id) {
    return root.resolve("files").resolve((String) jobs.job(host, id).get("output"));
  }

  byte[] pdf() throws Exception {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream c = new PDPageContentStream(document, page)) {
        c.beginText();
        c.setFont(PDType1Font.HELVETICA, 12);
        c.newLineAtOffset(30, 700);
        c.showText("Budget approved: 123.45 EUR. Reference FINAL-01.");
        c.endText();
      }
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  String document(byte[] bytes, String name) {
    return jobs.prepareDocument(host, name, bytes, "Budget", Map.of(), null, null, task());
  }

  static final List<DataTransformEngine.Operation> CLEAN =
      List.of(
          new DataTransformEngine.Operation(DataTransformEngine.Kind.NORMALIZE, null, null),
          new DataTransformEngine.Operation(DataTransformEngine.Kind.DEDUPLICATE, null, null),
          new DataTransformEngine.Operation(DataTransformEngine.Kind.SORT_NUMERIC, "amount", null));
  static final Map<String, TableService.Column> SCHEMA =
      Map.of("amount", new TableService.Column(TableService.Type.DECIMAL, "EUR"));

  @Test
  void pdfToSourcedDocxUsesRealFilesAndPreservesOriginal() throws Exception {
    byte[] bytes = pdf();
    Path original = root.resolve("original.pdf");
    Files.write(original, bytes);
    String before = Guard.digest(Files.readAllBytes(original));
    String id = document(Files.readAllBytes(original), "original.pdf");
    assertFalse(Files.exists(output(id)));
    complete(id);
    String read = Documents.readDocx(Files.readAllBytes(output(id)));
    assertTrue(read.contains("123.45 EUR"));
    assertTrue(read.contains(before));
    assertTrue(read.contains("page : 1"));
    assertEquals(before, Guard.digest(Files.readAllBytes(original)));
  }

  @Test
  void csvCleanupDiffExactAnalysisAndJsonRoundtrip() throws Exception {
    byte[] source = "name,amount\n a ,0.10\n a ,0.10\n b ,0.20\n".getBytes(StandardCharsets.UTF_8);
    Path input = root.resolve("input.csv");
    Files.write(input, source);
    String id =
        jobs.prepareTable(
            host, "input.csv", Files.readAllBytes(input), CLEAN, SCHEMA, "csv", task());
    Map<String, Object> job = jobs.job(host, id);
    assertTrue(job.get("difference").toString().contains("removed_rows=1"));
    complete(id);
    String csv = Files.readString(output(id));
    assertEquals(
        "0.30", new fr.jarvis.services.modules.data.Data().csv(csv).stats("amount").get("sum"));
    TableService service = new TableService();
    String json = service.convert(csv, "csv", "json").json();
    assertEquals(csv, service.convert(json, "json", "csv").csv());
    assertArrayEquals(source, Files.readAllBytes(input));
  }

  @Test
  void successfulVerificationIsNotReplayedAfterDatabaseRestart() {
    String id = jobs.prepareText(host, "restart safe", task());
    assertEquals("SUCCEEDED", jobs.advance(host, id, null).state);
    long sequence = ((Number) jobs.states.snapshot(id).get("sequence")).longValue();
    reopen();
    assertEquals("WAITING_APPROVAL", jobs.advance(host, id, null).state);
    assertEquals(sequence + 1, ((Number) jobs.states.snapshot(id).get("sequence")).longValue());
    WorkflowEngine.Progress request = jobs.advance(host, id, null);
    String receipt = jobs.approve(host, request.approvalRequest);
    assertEquals("SUCCEEDED", jobs.advance(host, id, receipt).state);
    String digest = jobs.job(host, id).get("encoded_digest").toString();
    assertEquals("SUCCEEDED", jobs.advance(host, id, null).state);
    assertEquals(digest, jobs.job(host, id).get("encoded_digest"));
  }

  @Test
  void inFlightStepRecoversUnknownAndNeverReplays() {
    String id = jobs.prepareText(host, "uncertain", task());
    jobs.states.transition(id, "verify", Set.of("PENDING"), "RUNNING", "RUNNING");
    reopen();
    assertEquals("UNKNOWN", jobs.advance(host, id, null).state);
    assertFalse(Files.exists(output(id)));
  }

  @Test
  void unconfirmedOrUnauthorizedActionIsRefusedAndAudited() {
    String id = jobs.prepareText(host, "permission", task());
    Guard.Context denied = new Guard.Context("denied", Set.of());
    assertThrows(Failure.class, () -> jobs.advance(denied, id, null));
    assertTrue(jobs.store.all("audit").stream().anyMatch(r -> "denied".equals(r.get("status"))));
    assertFalse(Files.exists(output(id)));
  }

  @Test
  void changedSourceIsRefusedBeforeConfirmation() throws Exception {
    String id = jobs.prepareText(host, "original", task());
    Files.write(
        root.resolve("files").resolve((String) jobs.job(host, id).get("source")), new byte[] {1});
    assertEquals("FAILED", jobs.advance(host, id, null).state);
    assertFalse(Files.exists(output(id)));
  }

  @Test
  void changedPreviewCannotBeApproved() {
    String id = jobs.prepareText(host, "exact", task());
    jobs.advance(host, id, null);
    WorkflowEngine.Progress request = jobs.advance(host, id, null);
    Map<String, Object> row = jobs.store.get("final_jobs", id);
    row.put("text", "tampered");
    jobs.store.put("final_jobs", id, row);
    assertThrows(Failure.class, () -> jobs.approve(host, request.approvalRequest));
  }

  @Test
  void confirmationCannotBeReusedAndExistingOutputIsNotOverwritten() throws Exception {
    String id = jobs.prepareText(host, "fresh", task());
    jobs.advance(host, id, null);
    WorkflowEngine.Progress request = jobs.advance(host, id, null);
    String receipt = jobs.approve(host, request.approvalRequest);
    Files.writeString(output(id), "original unrelated");
    assertEquals("UNKNOWN", jobs.advance(host, id, receipt).state);
    assertEquals("original unrelated", Files.readString(output(id)));
    assertEquals("UNKNOWN", jobs.advance(host, id, receipt).state);
  }

  @Test
  void templateAndLiteralReplacementAreBoundedAndEscapeXml() throws Exception {
    DocumentService d = new DocumentService(new PdfBoxText());
    String filled =
        d.template("Bonjour {{name}} / {{amount}}", Map.of("name", "A & B", "amount", "0.30 EUR"));
    String changed = d.replace(filled, "0.30", "0.40");
    String id = jobs.prepareText(host, changed, task());
    complete(id);
    assertEquals(changed + "\n", Documents.readDocx(Files.readAllBytes(output(id))));
    assertThrows(Failure.class, () -> d.template("{{missing}}", Map.of()));
    assertThrows(Failure.class, () -> d.template("{{name}}", Map.of("name", "x", "unused", "y")));
    assertThrows(Failure.class, () -> d.replace("hello", "", "x"));
  }

  @Test
  void invalidXmlCharactersAreRefused() {
    assertThrows(Failure.class, () -> jobs.prepareText(host, "invalid\u0000", task()));
    assertThrows(Failure.class, () -> DocumentService.bounded("\ud800"));
  }

  @Test
  void unitsTypesAndInexactMeanAreExplicit() {
    TableService s = new TableService();
    assertThrows(Failure.class, () -> s.transform("amount\n12kg\n", List.of(), SCHEMA, task()));
    assertThrows(
        Failure.class, () -> new TableService.Column(TableService.Type.DECIMAL, "furlong"));
    var table = new fr.jarvis.services.modules.data.Data().csv("amount\n1\n2\n2\n");
    Map<String, Object> analysis = new StructuredAnalyst().analyze(table, SCHEMA, task());
    assertTrue(analysis.toString().contains("mean_is_exact=false"));
    assertTrue(analysis.toString().contains("exact_mean_fraction=5/3"));
    assertThrows(
        Failure.class, () -> new StructuredAnalyst().compare(table, table, "amount", "EUR", "USD"));
  }

  @Test
  void filtersGroupsAndSortAreDeterministic() {
    String csv = "group,amount\nb,0.20\na,0.10\nb,0.30\n";
    var recipe =
        List.of(
            new DataTransformEngine.Operation(DataTransformEngine.Kind.FILTER, "group", "b"),
            new DataTransformEngine.Operation(
                DataTransformEngine.Kind.SORT_NUMERIC, "amount", null),
            new DataTransformEngine.Operation(
                DataTransformEngine.Kind.AGGREGATE, "group", "amount"));
    var result = new TableService().transform(csv, recipe, SCHEMA, task());
    assertEquals(2, result.after.rows.size());
    assertEquals("0.50", result.summaries.get(0).get("b"));
  }

  @Test
  void localMemoryRequiresExactConsentAndNeverWritesCentralStore() {
    Memory memory = new Memory(jobs.store, jobs.guard);
    long expires = System.currentTimeMillis() + 60000;
    String id = UUID.randomUUID().toString();
    assertThrows(
        Failure.class, () -> memory.save(host, id, "preference", "local", "test", expires, null));
    memory.save(
        host,
        id,
        "preference",
        "local",
        "test",
        expires,
        jobs.guard.confirm(
            host, "memory.write", memory.exact(id, "preference", "local", "test", expires)));
    String export = jobs.prepareMemoryExport(host, memory, task());
    assertTrue(jobs.job(host, export).get("text").toString().contains("local"));
    assertEquals(0, jobs.store.count("central_memory"));
  }

  @Test
  void cancellationLeavesNoPreparedJob() {
    Task cancelled = task();
    cancelled.cancel();
    assertThrows(Failure.class, () -> jobs.prepareText(host, "cancelled", cancelled));
    assertEquals(0, jobs.history(host).size());
  }

  @Test
  void sourceAndWorkspaceQuotaAreBounded() {
    assertThrows(Failure.class, () -> document(new byte[Documents.MAX_BYTES + 1], "oversize.pdf"));
    assertThrows(Failure.class, () -> jobs.guard.path(host, "documents.export", "../escape.csv"));
  }

  @Test
  void optionalSearchAdapterCannotInventProvenance() {
    var pages =
        new DocumentService(new PdfBoxText())
            .read("source.txt", "verified Budget".getBytes(StandardCharsets.UTF_8), task());
    var other =
        new DocumentTextExtractor.Page("elsewhere.txt", Guard.digest("other"), 0, "invented");
    var bad =
        new ConsultationService(
            jobs.guard, (q, p, t) -> List.of(new ConsultationService.Passage(other, 0, 8)));
    assertThrows(Failure.class, () -> bad.consult(host, "Budget", pages, task()));
    var good = new ConsultationService(jobs.guard, null);
    assertEquals("verified Budget", good.consult(host, "Budget", pages, task()).get(0).quote);
  }

  @Test
  void successfulSafReadbackAndDuplicateExportIsRefused() throws Exception {
    String id = jobs.prepareText(host, "public exact", task());
    complete(id);
    DiskPort port = new DiskPort(root.resolve("selected"), false, false);
    var request = jobs.exportRequest(host, id, port);
    var result = jobs.export(host, request, jobs.approve(host, request)).result.get();
    assertEquals(ToolDispatcher.Status.SUCCESS, result.status);
    assertEquals("PUBLISHED", jobs.job(host, id).get("publication"));
    assertThrows(Failure.class, () -> jobs.exportRequest(host, id, port));
    assertTrue(Documents.readDocx(Files.readAllBytes(port.created)).contains("public exact"));
  }

  @Test
  void safCorruptionRollsBackFreshOutputWithoutDeletingOriginal() throws Exception {
    String id = jobs.prepareText(host, "rollback", task());
    complete(id);
    DiskPort port = new DiskPort(root.resolve("selected"), true, false);
    Files.createDirectories(port.dir);
    Path original = port.dir.resolve("original.txt");
    Files.writeString(original, "preserve");
    var request = jobs.exportRequest(host, id, port);
    assertEquals(
        ToolDispatcher.Status.UNKNOWN,
        jobs.export(host, request, jobs.approve(host, request)).result.get().status);
    assertEquals("FAILED", jobs.job(host, id).get("publication"));
    assertFalse(Files.exists(port.created));
    assertEquals("preserve", Files.readString(original));
  }

  @Test
  void lostSafOutcomeRemainsUnknownAcrossRestart() throws Exception {
    String id = jobs.prepareText(host, "unknown export", task());
    complete(id);
    DiskPort port = new DiskPort(root.resolve("selected"), false, true);
    var request = jobs.exportRequest(host, id, port);
    jobs.export(host, request, jobs.approve(host, request)).result.get();
    assertEquals("UNKNOWN", jobs.job(host, id).get("publication"));
    reopen();
    assertThrows(
        Failure.class,
        () -> jobs.exportRequest(host, id, new DiskPort(root.resolve("another"), false, false)));
  }

  @Test
  void changedEncodedDocxIsRefusedBeforePublicWrite() throws Exception {
    String id = jobs.prepareText(host, "exact binary", task());
    complete(id);
    Files.write(output(id), new byte[] {1, 2, 3});
    DiskPort port = new DiskPort(root.resolve("selected"), false, false);
    var request = jobs.exportRequest(host, id, port);
    assertEquals(
        ToolDispatcher.Status.UNKNOWN,
        jobs.export(host, request, jobs.approve(host, request)).result.get().status);
    assertNull(port.created);
  }

  @Test
  void dispatcherRefusalHasDurableMetadataWithoutArgumentContent() throws Exception {
    ToolInvocation invocation =
        new ToolInvocation(
            UUID.randomUUID().toString(),
            "documents.search",
            "1.0.0",
            Map.of("query", "PRIVATE_MARKER_DO_NOT_JOURNAL"),
            3000,
            null);
    var result =
        jobs.dispatcher.dispatch(new Guard.Context("denied", Set.of()), invocation).result.get();
    assertEquals(ToolDispatcher.Status.REFUSED, result.status);
    var entries = jobs.store.all("tool_executions");
    assertEquals(2, entries.size());
    assertTrue(entries.stream().anyMatch(r -> "REFUSED".equals(r.get("status"))));
    assertFalse(entries.toString().contains("PRIVATE_MARKER"));
  }

  @Test
  void fullExecutionJournalRefusesActionBeforeAnyOutput() throws Exception {
    for (int i = 0; i < 4095; i++)
      jobs.store.put("tool_executions", Integer.toString(i), Map.of("state", "prior"));
    String id = jobs.prepareText(host, "bounded journal", task());
    assertEquals("FAILED", jobs.advance(host, id, null).state);
    assertFalse(Files.exists(output(id)));
  }

  @Test
  void versionedCatalogueRendersAndSavesLocalMemoryOnlyWithExactApproval() throws Exception {
    ToolInvocation render =
        new ToolInvocation(
            UUID.randomUUID().toString(),
            "documents.template.render",
            "1.0.0",
            Map.of("template", "Hello {{name}}", "fields", "{\"name\":\"local\"}"),
            3000,
            null);
    assertEquals("Hello local", jobs.dispatcher.dispatch(host, render).result.get().value);
    var args =
        Map.of(
            "id",
            UUID.randomUUID().toString(),
            "type",
            "preference",
            "text",
            "local catalogue memory",
            "source",
            "test",
            "expires",
            Long.toString(System.currentTimeMillis() + 60000));
    ToolInvocation request =
        new ToolInvocation(
            UUID.randomUUID().toString(), "memory.local.save", "1.0.0", args, 3000, null);
    assertEquals(
        ToolDispatcher.Status.REFUSED, jobs.dispatcher.dispatch(host, request).result.get().status);
    String receipt = jobs.approve(host, request);
    var approved =
        new ToolInvocation(request.requestId, request.toolId, request.version, args, 3000, receipt);
    assertEquals(
        ToolDispatcher.Status.SUCCESS,
        jobs.dispatcher.dispatch(host, approved).result.get().status);
    assertEquals(1, jobs.store.count("memory"));
    assertEquals(0, jobs.store.count("central_memory"));
  }

  @Test
  void recipeCodecRejectsDuplicatesUnknownOperatorsAndAmbiguousSchema() {
    assertThrows(Failure.class, () -> Recipes.fields("{\"name\":\"a\",\"name\":\"b\"}"));
    assertThrows(Failure.class, () -> Recipes.operations("[{\"kind\":\"EXECUTE_SCRIPT\"}]"));
    assertThrows(
        Failure.class,
        () -> Recipes.schema("{\"amount\":{\"type\":\"DECIMAL\",\"unit\":\"kg\",\"grant\":true}}"));
    assertThrows(Failure.class, () -> Recipes.parse("{\"a\":1} trailing"));
  }

  @Test
  void adapterReusesExistingFtsContractAndRejectsStaleRevision() {
    String text = "Budget source selected", id = UUID.randomUUID().toString();
    var knowledge =
        new fr.jarvis.services.modules.knowledge.Knowledge(
            jobs.store,
            jobs.guard,
            new fr.jarvis.services.modules.knowledge.Knowledge.ConceptEmbeddings());
    Guard.Context indexer =
        new Guard.Context("trusted-indexer", Set.of("knowledge.write", "knowledge.read"));
    knowledge.index(indexer, Map.of("id", id, "name", "source.txt", "text", text), task());
    var pages =
        new DocumentService(new PdfBoxText())
            .read("source.txt", text.getBytes(StandardCharsets.UTF_8), task());
    var adapter =
        new ExistingKnowledgeAdapter(
            knowledge,
            indexer,
            Map.of(
                id,
                new ExistingKnowledgeAdapter.Binding(
                    pages.get(0).sourceSha256, Guard.digest(text))));
    assertEquals(
        text,
        new ConsultationService(jobs.guard, adapter)
            .consult(host, "Budget", pages, task())
            .get(0)
            .quote);
    var stale =
        new ExistingKnowledgeAdapter(
            knowledge,
            indexer,
            Map.of(
                id,
                new ExistingKnowledgeAdapter.Binding(
                    pages.get(0).sourceSha256, Guard.digest("old revision"))));
    assertThrows(
        Failure.class,
        () -> new ConsultationService(jobs.guard, stale).consult(host, "Budget", pages, task()));
  }

  @Test
  void incoherencesAreSourcedAndDoNotEchoInvalidValues() {
    var table = new fr.jarvis.services.modules.data.Data().csv("amount\nnot-a-number\n1.25\n");
    var result = new StructuredAnalyst().inspect(table, SCHEMA, task());
    assertEquals(Guard.digest(table.csv()), result.get("source_csv_sha256"));
    assertTrue(result.toString().contains("TYPE_MISMATCH"));
    assertTrue(result.toString().contains("row=1"));
    assertFalse(result.toString().contains("not-a-number"));
  }

  static final class DiskPort implements SafUriPort {
    final Path dir;
    final boolean corrupt, lost;
    Path created;

    DiskPort(Path dir, boolean corrupt, boolean lost) {
      this.dir = dir;
      this.corrupt = corrupt;
      this.lost = lost;
    }

    public String targetReference() {
      return "selected-test-directory";
    }

    public Transaction begin(String ref, String name, String mime, Task task) {
      try {
        Files.createDirectories(dir);
        created = dir.resolve(name);
        Files.createFile(created);
      } catch (Exception e) {
        throw new Failure("CREATE_FAILED");
      }
      return new Transaction() {
        public void write(byte[] bytes, Task task) {
          try {
            task.check();
            Files.write(created, bytes);
          } catch (Exception e) {
            throw new Failure("WRITE_FAILED");
          }
        }

        public String readbackSha256(Task task) {
          try {
            return corrupt ? Guard.digest("corrupt") : Guard.digest(Files.readAllBytes(created));
          } catch (Exception e) {
            throw new Failure("READ_FAILED");
          }
        }

        public String commit(Task task) {
          if (lost) throw new Failure("LOST_ACK");
          return created.toUri().toString();
        }

        public boolean rollback() {
          if (lost) return false;
          try {
            return Files.deleteIfExists(created);
          } catch (Exception e) {
            return false;
          }
        }
      };
    }
  }
}
