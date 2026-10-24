package fr.jarvis.services.demo;

import com.google.gson.GsonBuilder;
import fr.jarvis.services.bridge.OfflineBridge;
import fr.jarvis.services.finalization.*;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.platform.*;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.workflow.WorkflowEngine;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/** Reproducible synthetic data written as real PDF/CSV/DOCX files; two independent JVM phases. */
public final class FinalScenariosDemo {
  private static Guard.Context host() {
    return new Guard.Context(
        "synthetic-final-demo",
        new HashSet<>(
            Arrays.asList(
                "confirm",
                "documents.import",
                "documents.read",
                "documents.export",
                "knowledge.read",
                "data.read",
                "analyst.read",
                "actions.run",
                "bridge.file")));
  }

  private static Task task() {
    return new Task(90000);
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 2) {
      phase(args[0], Paths.get(args[1]));
      return;
    }
    if (args.length != 0) throw new IllegalArgumentException("Synthetic examples only");
    Path root = Files.createTempDirectory("jarvis-services-final-");
    for (String phase : Arrays.asList("prepare", "resume")) {
      Process process =
          new ProcessBuilder(
                  Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                  "-cp",
                  System.getProperty("java.class.path"),
                  FinalScenariosDemo.class.getName(),
                  phase,
                  root.toString())
              .inheritIO()
              .start();
      if (!process.waitFor(90, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new IllegalStateException("Demo timeout");
      }
      if (process.exitValue() != 0) throw new IllegalStateException("Demo phase failed");
    }
    benchmarks(root);
    System.out.println("FINAL_EXAMPLE_DIRECTORY=" + root);
  }

  private static void phase(String phase, Path directory) throws Exception {
    Path root = directory.toRealPath();
    if (!root.startsWith(Paths.get(System.getProperty("java.io.tmpdir")).toRealPath())
        || !root.getFileName().toString().startsWith("jarvis-services-final-"))
      throw new IllegalArgumentException("Synthetic directory required");
    try (JdbcDatabase db = new JdbcDatabase(root.resolve("journal.sqlite"))) {
      PdfBoxText pdf = new PdfBoxText();
      try (LocalJobs jobs = new LocalJobs(root.resolve("files"), db, pdf, pdf)) {
        Guard.Context host = host();
        if (phase.equals("prepare")) {
          Files.write(root.resolve("source.pdf"), pdf(1));
          Files.writeString(
              root.resolve("source.csv"), "name,amount\n a ,0.10\n a ,0.10\n b ,0.20\n");
          String id =
              jobs.prepareDocument(
                  host,
                  "source.pdf",
                  Files.readAllBytes(root.resolve("source.pdf")),
                  "Budget",
                  Collections.emptyMap(),
                  null,
                  null,
                  task());
          if (!"SUCCEEDED".equals(jobs.advance(host, id, null).state))
            throw new IllegalStateException("Verification failed");
          Files.writeString(root.resolve("job-id.txt"), id, StandardOpenOption.CREATE_NEW);
          System.out.println(
              "phase1: PDF read from disk; verified source; JVM exits with publication pending");
        } else if (phase.equals("resume")) {
          String id = Files.readString(root.resolve("job-id.txt"));
          jobs.recover(host);
          complete(jobs, host, id);
          Path docx = root.resolve("files").resolve((String) jobs.job(host, id).get("output"));
          String text = Documents.readDocx(Files.readAllBytes(docx));
          if (!text.contains("123.45 EUR")
              || !text.contains(Guard.digest(Files.readAllBytes(root.resolve("source.pdf")))))
            throw new IllegalStateException("DOCX evidence mismatch");
          long executions =
              jobs.store.all("workflow_journal").stream()
                  .filter(
                      r ->
                          id.equals(r.get("workflow"))
                              && "verify".equals(r.get("step"))
                              && "RUNNING".equals(r.get("state")))
                  .count();
          if (executions != 1) throw new IllegalStateException("Verified step was replayed");
          var recipe =
              Arrays.asList(
                  new fr.jarvis.services.pipeline.DataTransformEngine.Operation(
                      fr.jarvis.services.pipeline.DataTransformEngine.Kind.NORMALIZE, null, null),
                  new fr.jarvis.services.pipeline.DataTransformEngine.Operation(
                      fr.jarvis.services.pipeline.DataTransformEngine.Kind.DEDUPLICATE,
                      null,
                      null));
          String table =
              jobs.prepareTable(
                  host,
                  "source.csv",
                  Files.readAllBytes(root.resolve("source.csv")),
                  recipe,
                  Collections.singletonMap(
                      "amount", new TableService.Column(TableService.Type.DECIMAL, "EUR")),
                  "csv",
                  task());
          complete(jobs, host, table);
          Path csv = root.resolve("files").resolve((String) jobs.job(host, table).get("output"));
          if (!new fr.jarvis.services.modules.data.Data()
              .csv(Files.readString(csv))
              .stats("amount")
              .get("sum")
              .equals("0.30")) throw new IllegalStateException("CSV arithmetic mismatch");
          String refusal = null;
          try {
            jobs.advance(
                new Guard.Context("unauthorized-model", Collections.emptySet()), table, null);
          } catch (Failure e) {
            refusal = e.code;
          }
          if (!"UNAUTHORIZED".equals(refusal)) throw new IllegalStateException("Missing refusal");
          Map<String, Object> report = new TreeMap<>();
          report.put("synthetic_files_real_execution", true);
          report.put("tool_catalogue", jobs.registry.catalogue());
          report.put("restart", "two JVMs, clean exit between completed steps");
          report.put("verification_runs_across_restart", executions);
          report.put("pdf_input", artifact(root.resolve("source.pdf")));
          report.put("docx_output", artifact(docx));
          report.put("csv_input", artifact(root.resolve("source.csv")));
          report.put("csv_output", artifact(csv));
          report.put("csv_preview", jobs.job(host, table).get("difference"));
          report.put("csv_analysis", jobs.job(host, table).get("analysis"));
          report.put("unauthorized_action", refusal);
          report.put("bridge", bridge(root));
          Files.writeString(
              root.resolve("scenarios.json"),
              new GsonBuilder().setPrettyPrinting().create().toJson(report),
              StandardOpenOption.CREATE_NEW);
          System.out.println(
              "phase2: sourced DOCX and exact CSV read back; completed verification not replayed;"
                  + " unauthorized action refused; authenticated file bridge ACKED without"
                  + " execution");
        } else throw new IllegalArgumentException("Unknown phase");
      }
    }
  }

  private static void complete(LocalJobs jobs, Guard.Context host, String id) {
    WorkflowEngine.Progress p = jobs.advance(host, id, null);
    if (p.approvalRequest == null && !"WAITING_APPROVAL".equals(p.state))
      p = jobs.advance(host, id, null);
    if (p.approvalRequest == null)
      throw new IllegalStateException("Expected exact synthetic approval");
    // Fixture consent only; production approval is issued solely by the actual host UI.
    String token = jobs.approve(host, p.approvalRequest);
    if (!"SUCCEEDED".equals(jobs.advance(host, id, token).state))
      throw new IllegalStateException("Publication failed");
  }

  private static Map<String, Object> bridge(Path root) throws Exception {
    byte[] key = new byte[32];
    new java.security.SecureRandom().nextBytes(key);
    String session = UUID.randomUUID().toString();
    Guard.Context host = host();
    try (JdbcDatabase a = new JdbcDatabase(root.resolve("phone.sqlite"));
        JdbcDatabase b = new JdbcDatabase(root.resolve("pc.sqlite"))) {
      Store sa = new Store(a), sb = new Store(b);
      Guard ga = new Guard(root.resolve("phone"), sa), gb = new Guard(root.resolve("pc"), sb);
      try (OfflineBridge sender = new OfflineBridge(sa, ga, "phone", "pc", session, key);
          OfflineBridge receiver = new OfflineBridge(sb, gb, "pc", "phone", session, key)) {
        String id = sender.queue(host, "document.reference", UUID.randomUUID().toString());
        Path frame = root.resolve("message.offline.json");
        Files.writeString(frame, sender.frame(host, id), StandardOpenOption.CREATE_NEW);
        String ack = receiver.receive(host, Files.readString(frame));
        sender.interrupted(host, id);
        String interrupted = sender.state(host, id);
        if (!ack.equals(receiver.receive(host, Files.readString(frame))))
          throw new IllegalStateException("Duplicate changed ACK");
        Path reply = root.resolve("receipt.offline.json");
        Files.writeString(reply, ack, StandardOpenOption.CREATE_NEW);
        sender.acknowledge(host, Files.readString(reply));
        Map<String, Object> out = new TreeMap<>();
        out.put("transport", "voluntary synthetic file transfer only; no network");
        out.put("after_lost_ack", interrupted);
        out.put("final_state", sender.state(host, id));
        out.put("durable_inbox_entries", sb.count("offline_inbox"));
        out.put("remote_actions_executed", 0);
        out.put("authentication", "HMAC-SHA256, host-provisioned ephemeral test pairing");
        return out;
      }
    } finally {
      Arrays.fill(key, (byte) 0);
    }
  }

  private static Map<String, Object> artifact(Path path) throws IOException {
    byte[] bytes = Files.readAllBytes(path);
    Map<String, Object> a = new TreeMap<>();
    a.put("name", path.getFileName().toString());
    a.put("bytes", bytes.length);
    a.put("sha256", Guard.digest(bytes));
    return a;
  }

  public static byte[] pdf(int pages) throws Exception {
    try (PDDocument d = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      for (int i = 0; i < pages; i++) {
        PDPage page = new PDPage();
        d.addPage(page);
        try (PDPageContentStream c = new PDPageContentStream(d, page)) {
          c.beginText();
          c.setFont(PDType1Font.HELVETICA, 12);
          c.newLineAtOffset(30, 700);
          c.showText("Budget 2026 approved: 123.45 EUR. Page " + (i + 1));
          c.endText();
        }
      }
      d.save(out);
      return out.toByteArray();
    }
  }

  private static void benchmarks(Path root) throws Exception {
    List<Map<String, Object>> results = new ArrayList<>();
    for (int rows : new int[] {100, 1000, 5000}) {
      StringBuilder csv = new StringBuilder("group,amount,label\n");
      String label = String.join("", Collections.nCopies(200, "x"));
      for (int i = 0; i < rows; i++)
        csv.append(i % 8)
            .append(",0.")
            .append(String.format(Locale.ROOT, "%02d", i % 8))
            .append(',')
            .append(label)
            .append('\n');
      Path source = root.resolve("benchmark-" + rows + ".csv");
      Files.writeString(source, csv.toString(), StandardOpenOption.CREATE_NEW);
      long start = System.nanoTime();
      var preview =
          new TableService()
              .transform(
                  Files.readString(source),
                  Arrays.asList(
                      new fr.jarvis.services.pipeline.DataTransformEngine.Operation(
                          fr.jarvis.services.pipeline.DataTransformEngine.Kind.NORMALIZE,
                          null,
                          null),
                      new fr.jarvis.services.pipeline.DataTransformEngine.Operation(
                          fr.jarvis.services.pipeline.DataTransformEngine.Kind.DEDUPLICATE,
                          null,
                          null)),
                  Collections.singletonMap(
                      "amount", new TableService.Column(TableService.Type.DECIMAL, "EUR")),
                  task());
      Path output = root.resolve("benchmark-" + rows + "-result.csv");
      Files.writeString(output, preview.after.csv(), StandardOpenOption.CREATE_NEW);
      new fr.jarvis.services.modules.data.Data().csv(Files.readString(output));
      Map<String, Object> row = new TreeMap<>();
      row.put("kind", "CSV");
      row.put("input_rows", rows);
      row.put("output_rows", preview.after.rows.size());
      row.put("elapsed_ms", (System.nanoTime() - start) / 1_000_000.0);
      row.put("input", artifact(source));
      row.put("output", artifact(output));
      results.add(row);
    }
    for (int pages : new int[] {1, 10, 50}) {
      Path source = root.resolve("benchmark-" + pages + ".pdf");
      Files.write(source, pdf(pages), StandardOpenOption.CREATE_NEW);
      long start = System.nanoTime();
      var extracted =
          new DocumentService(new PdfBoxText())
              .read(source.getFileName().toString(), Files.readAllBytes(source), task());
      if (extracted.size() != pages) throw new IllegalStateException("Page count mismatch");
      Map<String, Object> row = new TreeMap<>();
      row.put("kind", "PDF");
      row.put("pages", pages);
      row.put("elapsed_ms", (System.nanoTime() - start) / 1_000_000.0);
      row.put("input", artifact(source));
      results.add(row);
    }
    Map<String, Object> report = new TreeMap<>();
    report.put("platform", "JVM Linux, not Android hardware");
    report.put("samples", results);
    report.put(
        "measurement",
        "one timed pass per generated file; includes file read/write and output validation; not a"
            + " statistical performance claim");
    Path proc = Paths.get("/proc/self/status");
    if (Files.exists(proc))
      for (String line : Files.readAllLines(proc))
        if (line.startsWith("VmHWM:")) report.put("process_cumulative_rss_high_water_mark", line);
    report.put("physical_battery_thermal", "not measured");
    Files.writeString(
        root.resolve("benchmarks.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(report),
        StandardOpenOption.CREATE_NEW);
  }
}
