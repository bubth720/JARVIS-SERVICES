package fr.jarvis.services;

import fr.jarvis.services.workflow.*;
import fr.jarvis.services.bridge.*;
import fr.jarvis.services.platform.*;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.tools.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowBridgeTest {
  @TempDir Path root;
  JdbcDatabase db; Store store; Guard guard; ToolRegistry registry; ApprovalGate approvals; ToolDispatcher dispatcher;
  Guard.Context host = new Guard.Context("synthetic-workflow", Set.of("actions.run", "confirm", "documents.import", "documents.read", "documents.export", "data.read", "bridge.lab"));
  void open() {
    db = new JdbcDatabase(root.resolve("state.sqlite")); store = new Store(db); guard = new Guard(root, store);
    registry = new ToolRegistry(store); approvals = new ApprovalGate(store, guard);
    ServicesTools.register(registry, new Documents(guard, store, new PdfBoxText()), new Data(), guard);
    dispatcher = new ToolDispatcher(registry, guard, approvals);
  }
  @BeforeEach void setup() { open(); }
  @AfterEach void close() { dispatcher.close(); db.close(); }
  WorkflowDefinition definition(String id) {
    return new WorkflowDefinition(id, List.of(
      new WorkflowDefinition.Step("search", "documents.search", "1.0.0", Set.of(), Map.of("query", "alpha"), 3000),
      new WorkflowDefinition.Step("csv", "data.csv.normalize", "1.0.0", Set.of("search"), Map.of("csv", "name,value\n a ,0.10\n a ,0.10\n"), 3000),
      new WorkflowDefinition.Step("export", "documents.docx.export", "1.0.0", Set.of("csv"), Map.of("path", "workflow.docx", "text", "Synthetic workflow result"), 3000)));
  }
  WorkflowEngine engine() { return new WorkflowEngine(new WorkflowStateStore(store), guard, registry, dispatcher); }
  static String request() {
    Map<String,Object> wire = new TreeMap<>();
    wire.put("protocol_version", "1.0"); wire.put("contract_version", "1.0.0"); wire.put("correlation_id", UUID.randomUUID().toString());
    wire.put("origin", "core"); wire.put("destination", "services"); wire.put("capability", "tool.invoke");
    wire.put("scope", Map.of("session_id", UUID.randomUUID().toString(), "authorization_ref", UUID.randomUUID().toString()));
    wire.put("limits", Map.of("max_input_bytes", 8192L, "max_output_tokens", 0L, "timeout_ms", 30000L, "declared_memory_bytes", 2097152L));
    wire.put("deadline", Map.of("clock", "sender_monotonic", "sent_at_ms", 0L, "expires_at_ms", 30000L));
    wire.put("cancellation_id", UUID.randomUUID().toString()); wire.put("payload", Map.of("content_ref", UUID.randomUUID().toString()));
    return org.jarvis.contracts.v1.RequestEnvelope.fromMap(wire).toJson();
  }
  @Test void realThreeStepWorkflowResumesWithoutReplayingCompletedSteps() throws Exception {
    new Documents(guard, store, new PdfBoxText()).importBytes(host, "source.txt", "source alpha".getBytes(StandardCharsets.UTF_8), new Task(3000));
    String workflowId = UUID.randomUUID().toString(); WorkflowDefinition def = definition(workflowId);
    WorkflowEngine.Progress first = engine().advance(host, def, Map.of());
    assertEquals("search", first.step); assertEquals(ToolDispatcher.Status.SUCCESS, first.result.status);
    dispatcher.close(); db.close(); open(); // Reopen the real database with fresh host objects.
    WorkflowEngine.Progress second = engine().advance(host, definition(workflowId), Map.of());
    assertEquals("csv", second.step); assertEquals(ToolDispatcher.Status.SUCCESS, second.result.status);
    WorkflowEngine.Progress pending = engine().advance(host, def, Map.of()); assertEquals("WAITING_APPROVAL", pending.state);
    assertFalse(Files.exists(root.resolve("workflow.docx")));
    String receipt = approvals.confirm(host, registry.descriptor(pending.approvalRequest.toolId, pending.approvalRequest.version), pending.approvalRequest);
    assertEquals("SUCCEEDED", engine().advance(host, def, Map.of("export", receipt)).state);
    assertTrue(Documents.readDocx(Files.readAllBytes(root.resolve("workflow.docx"))).contains("Synthetic workflow result"));
    int journal = store.count("workflow_journal"); assertEquals("SUCCEEDED", engine().advance(host, def, Map.of()).state);
    assertEquals(journal, store.count("workflow_journal"));
    long searchRuns = store.all("workflow_journal").stream().filter(r -> "search".equals(r.get("step")) && "RUNNING".equals(r.get("state"))).count();
    assertEquals(1, searchRuns);
    String serialized = new com.google.gson.Gson().toJson(store.all("workflows")) + new com.google.gson.Gson().toJson(store.all("workflow_journal"));
    assertFalse(serialized.contains("Synthetic workflow result")); assertFalse(serialized.contains("source alpha"));
  }
  @Test void cyclesChangedDefinitionsAndUnknownDependenciesAreRejected() {
    assertThrows(Failure.class, () -> new WorkflowDefinition(UUID.randomUUID().toString(), List.of(
      new WorkflowDefinition.Step("a", "documents.search", "1.0.0", Set.of("b"), Map.of("query", "x"), 3000),
      new WorkflowDefinition.Step("b", "documents.search", "1.0.0", Set.of("a"), Map.of("query", "x"), 3000))));
    assertThrows(Failure.class, () -> new WorkflowDefinition(UUID.randomUUID().toString(), List.of(
      new WorkflowDefinition.Step("a", "documents.search", "1.0.0", Set.of("missing"), Map.of("query", "x"), 3000))));
    WorkflowDefinition first = definition(UUID.randomUUID().toString()); new WorkflowStateStore(store).prepare(first);
    WorkflowDefinition changed = new WorkflowDefinition(first.id, List.of(new WorkflowDefinition.Step("search", "documents.search", "1.0.0", Set.of(), Map.of("query", "changed"), 3000)));
    assertThrows(Failure.class, () -> new WorkflowStateStore(store).prepare(changed));
  }
  @Test void inFlightRecoveryStaysUnknownAndCannotAutoRetry() {
    WorkflowDefinition def = definition(UUID.randomUUID().toString()); WorkflowStateStore states = new WorkflowStateStore(store); states.prepare(def);
    states.transition(def.id, "search", Set.of("PENDING"), "RUNNING", "RUNNING");
    engine().recoverAfterHostRestart(host, def);
    assertEquals("UNKNOWN", states.state(def.id, "search")); assertEquals("UNKNOWN", engine().advance(host, def, Map.of()).state);
  }
  @Test void permissionsAreCheckedBeforeWorkflowDispatch() {
    WorkflowDefinition def = definition(UUID.randomUUID().toString());
    assertThrows(Failure.class, () -> engine().advance(new Guard.Context("denied", Set.of()), def, Map.of()));
    assertEquals(0, store.count("workflows"));
  }
  @Test void compensationOwnsOnlyFreshTemporaryAndRefusesChangedContent() throws Exception {
    Files.writeString(root.resolve("original.txt"), "original"); CompensationJournal journal = new CompensationJournal(store, guard);
    String id = journal.stage(host, "temporary".getBytes(StandardCharsets.UTF_8), new Task(3000)); journal.compensate(host, id);
    assertFalse(Files.exists(root.resolve(".workflow-" + id + ".tmp"))); assertEquals("original", Files.readString(root.resolve("original.txt")));
    String changed = journal.stage(host, "temporary".getBytes(StandardCharsets.UTF_8), new Task(3000));
    Files.writeString(root.resolve(".workflow-" + changed + ".tmp"), "changed");
    assertThrows(Failure.class, () -> journal.compensate(host, changed));
    assertTrue(Files.exists(root.resolve(".workflow-" + changed + ".tmp")));
  }
  @Test void realTwoProcessLabTransferDeduplicatesAndAcknowledges() throws Exception {
    Path lab = Files.createTempDirectory("jarvis-bridge-lab-");
    LocalLoopbackTransport transport = new LocalLoopbackTransport(guard, host, lab);
    BridgeOutbox outbox = new BridgeOutbox(store, guard, new UnifiedLabValidator());
    BridgeMessage message = outbox.enqueue(host, UUID.randomUUID().toString(), Map.of("sender", 1L), request());
    assertEquals("ACKED", outbox.send(host, message.id, transport).get("state"));
    String first = transport.exchange(message); String duplicate = transport.exchange(message); assertEquals(first, duplicate);
    JdbcDatabase inboxDb = new JdbcDatabase(lab.resolve("inbox.sqlite"));
    try { assertEquals(1, new Store(inboxDb).count("bridge_inbox")); } finally { inboxDb.close(); }
    assertEquals("ACKED", outbox.send(host, message.id, new TransportPort.Disabled()).get("state")); // No resend after durable ACK.
  }
  @Test void gapsVectorRegressionTamperAndWrongContractAreRejected() {
    BridgeInbox inbox = new BridgeInbox(store, guard); String stream = UUID.randomUUID().toString();
    BridgeMessage first = new BridgeMessage(UUID.randomUUID().toString(), stream, 1, Map.of("sender", 2L), request(), new UnifiedLabValidator()); inbox.receive(host, first);
    BridgeMessage gap = new BridgeMessage(UUID.randomUUID().toString(), stream, 3, Map.of("sender", 3L), request(), new UnifiedLabValidator());
    assertThrows(Failure.class, () -> inbox.receive(host, gap));
    BridgeMessage regressed = new BridgeMessage(UUID.randomUUID().toString(), stream, 2, Map.of("sender", 1L), request(), new UnifiedLabValidator());
    assertThrows(Failure.class, () -> inbox.receive(host, regressed));
    assertThrows(Failure.class, () -> BridgeMessage.parse(first.toJson().replace(first.digest, "a".repeat(64)), new UnifiedLabValidator()));
    assertThrows(Failure.class, () -> new BridgeMessage(UUID.randomUUID().toString(), stream, 2, Map.of("sender", 3L), "{}", new UnifiedLabValidator()));
    assertThrows(Failure.class, () -> BridgeMessage.parse(first.toJson().replace("\"sequence\":1", "\"sequence\":1.0"), new UnifiedLabValidator()));
    assertThrows(Failure.class, () -> BridgeMessage.parse(first.toJson().replace("\"sequence\":1", "\"sequence\":1,\"sequence\":1"), new UnifiedLabValidator()));
  }
  @Test void missingOrFalseAckAndDisabledTransportNeverBecomeSuccess() {
    BridgeOutbox outbox = new BridgeOutbox(store, guard, new UnifiedLabValidator()); BridgeMessage m = outbox.enqueue(host, UUID.randomUUID().toString(), Map.of("sender", 1L), request());
    assertThrows(Failure.class, () -> outbox.send(host, m.id, new TransportPort.Disabled()));
    assertEquals("UNKNOWN", store.get("bridge_outbox", m.id).get("state"));
    assertThrows(RuntimeException.class, () -> outbox.send(host, m.id, message -> "{}"));
    assertEquals("UNKNOWN", store.get("bridge_outbox", m.id).get("state"));
  }
}
