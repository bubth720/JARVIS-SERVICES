package fr.jarvis.services.demo;

import fr.jarvis.services.workflow.*;
import fr.jarvis.services.bridge.*;
import fr.jarvis.services.platform.*;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Separate clean JVM exits demonstrate durable boundaries; not a mid-write power-cut claim. */
public final class WorkflowLabDemo {
  public static void main(String[] args) throws Exception {
    if (args.length == 3) { phase(args[0], Paths.get(args[1]), args[2]); return; }
    if (args.length != 0) throw new IllegalArgumentException("Synthetic lab only");
    Path root = Files.createTempDirectory("jarvis-workflow-lab-"); String id = UUID.randomUUID().toString();
    for (String phase : List.of("phase1", "phase2")) {
      Process child = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
          "-cp", System.getProperty("java.class.path"), WorkflowLabDemo.class.getName(), phase, root.toString(), id)
          .redirectError(ProcessBuilder.Redirect.DISCARD).start();
      if (!child.waitFor(30, TimeUnit.SECONDS)) { child.destroyForcibly(); throw new IllegalStateException("Workflow lab timeout"); }
      if (child.exitValue() != 0) throw new IllegalStateException("Workflow lab phase failed");
      byte[] result = child.getInputStream().readNBytes(4096); System.out.print(new String(result, StandardCharsets.UTF_8));
    }
    JdbcDatabase db = new JdbcDatabase(root.resolve("workflow.sqlite"));
    try {
      Store store = new Store(db); Guard guard = new Guard(root, store); Guard.Context host = host();
      long searchRuns = store.all("workflow_journal").stream().filter(r -> "search".equals(r.get("step")) && "RUNNING".equals(r.get("state"))).count();
      if (searchRuns != 1) throw new IllegalStateException("Completed step replayed");
      System.out.println("workflow: three real tools; search executed once across two JVMs; DOCX verified");
      Path lab = Files.createTempDirectory("jarvis-bridge-lab-"); LocalLoopbackTransport transport = new LocalLoopbackTransport(guard, host, lab);
      BridgeOutbox outbox = new BridgeOutbox(store, guard, new UnifiedLabValidator());
      BridgeMessage message = outbox.enqueue(host, UUID.randomUUID().toString(), Map.of("synthetic_sender", 1L), request());
      System.out.println("bridge outbox: " + outbox.send(host, message.id, transport).get("state"));
      String ack1 = transport.exchange(message), ack2 = transport.exchange(message);
      if (!ack1.equals(ack2)) throw new IllegalStateException("Duplicate receipt mismatch");
      JdbcDatabase inbox = new JdbcDatabase(lab.resolve("inbox.sqlite"));
      try { if (new Store(inbox).count("bridge_inbox") != 1) throw new IllegalStateException("Duplicate delivery"); }
      finally { inbox.close(); }
      System.out.println("bridge: real receiver child JVMs; one durable inbox entry after repeated delivery; no tool executed remotely");
      System.out.println("Synthetic workflow artifacts: " + root);
    } finally { db.close(); }
  }
  private static Guard.Context host() { return new Guard.Context("synthetic-lab", new HashSet<>(List.of("actions.run", "confirm", "documents.read", "documents.import", "documents.export", "data.read", "bridge.lab"))); }
  private static WorkflowDefinition definition(String id) {
    return new WorkflowDefinition(id, List.of(
      new WorkflowDefinition.Step("search", "documents.search", "1.0.0", Set.of(), Map.of("query", "alpha"), 3000),
      new WorkflowDefinition.Step("csv", "data.csv.normalize", "1.0.0", Set.of("search"), Map.of("csv", "name,value\n a ,0.10\n a ,0.10\n"), 3000),
      new WorkflowDefinition.Step("export", "documents.docx.export", "1.0.0", Set.of("csv"), Map.of("path", "result.docx", "text", "Synthetic three-step workflow result"), 3000)));
  }
  private static void phase(String phase, Path root, String id) throws Exception {
    Path checked = root.toRealPath();
    if (!checked.startsWith(Paths.get(System.getProperty("java.io.tmpdir")).toRealPath())
        || !checked.getFileName().toString().startsWith("jarvis-workflow-lab-")) throw new IllegalArgumentException("Synthetic lab only");
    JdbcDatabase db = new JdbcDatabase(checked.resolve("workflow.sqlite"));
    try {
      Store store = new Store(db); Guard guard = new Guard(checked, store); Guard.Context host = host();
      Documents documents = new Documents(guard, store, new PdfBoxText()); ToolRegistry registry = new ToolRegistry(store);
      ServicesTools.register(registry, documents, new Data(), guard); ApprovalGate approvals = new ApprovalGate(store, guard);
      try (ToolDispatcher dispatcher = new ToolDispatcher(registry, guard, approvals)) {
        WorkflowEngine engine = new WorkflowEngine(new WorkflowStateStore(store), guard, registry, dispatcher); WorkflowDefinition definition = definition(id);
        if (phase.equals("phase1")) {
          documents.importBytes(host, "synthetic.txt", "alpha synthetic source".getBytes(StandardCharsets.UTF_8), new Task(3000));
          WorkflowEngine.Progress result = engine.advance(host, definition, Map.of());
          if (!"search".equals(result.step) || result.result.status != ToolDispatcher.Status.SUCCESS) throw new IllegalStateException("Search failed");
          System.out.println("phase1: search succeeded; clean JVM stop with remaining steps pending");
        } else if (phase.equals("phase2")) {
          WorkflowEngine.Progress csv = engine.advance(host, definition, Map.of());
          if (!"csv".equals(csv.step) || csv.result.status != ToolDispatcher.Status.SUCCESS) throw new IllegalStateException("CSV failed");
          WorkflowEngine.Progress pending = engine.advance(host, definition, Map.of());
          if (!"WAITING_APPROVAL".equals(pending.state)) throw new IllegalStateException("Approval not required");
          // Explicit synthetic fixture approval only; production requires the real user's exact-content decision.
          String token = approvals.confirm(host, registry.descriptor(pending.approvalRequest.toolId, pending.approvalRequest.version), pending.approvalRequest);
          if (!"SUCCEEDED".equals(engine.advance(host, definition, Map.of("export", token)).state)) throw new IllegalStateException("Export failed");
          if (!Documents.readDocx(Files.readAllBytes(checked.resolve("result.docx"))).contains("Synthetic three-step workflow result")) throw new IllegalStateException("DOCX mismatch");
          System.out.println("phase2: CSV and approved DOCX succeeded after reopening the same SQLite database");
        } else throw new IllegalArgumentException("Unknown phase");
      }
    } finally { db.close(); }
  }
  private static String request() {
    Map<String,Object> wire = new TreeMap<>(); wire.put("protocol_version", "1.0"); wire.put("contract_version", "1.0.0");
    wire.put("correlation_id", UUID.randomUUID().toString()); wire.put("origin", "core"); wire.put("destination", "services"); wire.put("capability", "tool.invoke");
    wire.put("scope", Map.of("session_id", UUID.randomUUID().toString(), "authorization_ref", UUID.randomUUID().toString()));
    wire.put("limits", Map.of("max_input_bytes", 8192L, "max_output_tokens", 0L, "timeout_ms", 30000L, "declared_memory_bytes", 2097152L));
    wire.put("deadline", Map.of("clock", "sender_monotonic", "sent_at_ms", 0L, "expires_at_ms", 30000L));
    wire.put("cancellation_id", UUID.randomUUID().toString()); wire.put("payload", Map.of("content_ref", UUID.randomUUID().toString()));
    return org.jarvis.contracts.v1.RequestEnvelope.fromMap(wire).toJson();
  }
}
