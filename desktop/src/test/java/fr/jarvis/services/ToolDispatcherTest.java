package fr.jarvis.services;

import fr.jarvis.services.platform.*;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ToolDispatcherTest {
  @TempDir Path root;
  JdbcDatabase db; Store store; Guard guard; Documents docs; ToolRegistry registry;
  ApprovalGate approvals; ToolDispatcher dispatcher;
  Guard.Context host = new Guard.Context("synthetic-host", new HashSet<>(Arrays.asList("confirm", "documents.read", "documents.import", "documents.export", "data.read")));
  @BeforeEach void setup() {
    db = new JdbcDatabase(root.resolve("fixture.db")); store = new Store(db);
    guard = new Guard(root, store); docs = new Documents(guard, store, new PdfBoxText());
    registry = new ToolRegistry(store); approvals = new ApprovalGate(store, guard);
    ServicesTools.register(registry, docs, new Data(), guard);
    dispatcher = new ToolDispatcher(registry, guard, approvals);
  }
  @AfterEach void close() throws Exception {
    dispatcher.close(); db.close();
  }
  ToolInvocation call(String tool, Map<String,String> args, String receipt) {
    return new ToolInvocation(UUID.randomUUID().toString(), tool, "1.0.0", args, 3000, receipt);
  }
  ToolDispatcher.Result execute(ToolInvocation i) throws Exception {
    return dispatcher.dispatch(host, i).result.get(5, TimeUnit.SECONDS);
  }
  ToolDescriptor exportDescriptor() {
    return ServicesTools.descriptor("documents.docx.export", "documents", "documents.export", true, "file_digest", "path", "text");
  }
  ToolInvocation approved(String path, String text) {
    ToolInvocation before = call("documents.docx.export", Map.of("path", path, "text", text), null);
    String token = approvals.confirm(host, exportDescriptor(), before);
    return new ToolInvocation(before.requestId, before.toolId, before.version, before.arguments, before.timeoutMs, token);
  }
  @Test void threeRealAdaptersAndReadableDocx() throws Exception {
    docs.importBytes(host, "fixture.txt", "JARVIS fixture alpha".getBytes(java.nio.charset.StandardCharsets.UTF_8), new Task(3000));
    ToolDispatcher.Result search = execute(call("documents.search", Map.of("query", "alpha"), null));
    assertEquals(ToolDispatcher.Status.SUCCESS, search.status);
    assertEquals(1, ((List<?>) search.value).size());
    ToolDispatcher.Result csv = execute(call("data.csv.normalize", Map.of("csv", "item,value\n a ,0.10\n a ,0.10\nb,0.20\n"), null));
    assertEquals(ToolDispatcher.Status.SUCCESS, csv.status);
    assertEquals(2, new Data().csv((String) csv.value).rows.size());
    ToolDispatcher.Result docx = execute(approved("result.docx", "Résultat <exact> & UTF-8"));
    assertEquals(ToolDispatcher.Status.SUCCESS, docx.status);
    assertTrue(Documents.readDocx(Files.readAllBytes(root.resolve("result.docx"))).contains("Résultat <exact> & UTF-8"));
  }
  @Test void permissionRefusedBeforeAdapterReadsAnything() throws Exception {
    boolean[] touched = {false};
    registry.register(ServicesTools.descriptor("knowledge.fixture", "knowledge", "knowledge.read", false, "test", "query"),
        (ctx, args, task) -> { touched[0] = true; return "forbidden"; });
    ToolDispatcher.Result r = execute(call("knowledge.fixture", Map.of("query", "test"), null));
    assertEquals(ToolDispatcher.Status.REFUSED, r.status); assertEquals("UNAUTHORIZED", r.error); assertFalse(touched[0]);
  }
  @Test void receiptCannotBeReplayedAndConsumptionIsDurable() throws Exception {
    ToolInvocation i = approved("once.docx", "synthetic");
    assertEquals(ToolDispatcher.Status.SUCCESS, execute(i).status);
    assertEquals("CONSUMED", store.get("tool_approvals", i.approval).get("state"));
    assertEquals(ToolDispatcher.Status.REFUSED, execute(i).status);
    Store second = new Store(db);
    assertEquals("CONSUMED", second.get("tool_approvals", i.approval).get("state"));
  }
  @Test void restartInvalidatesPendingReceiptWithoutDeletingJournal() {
    ToolInvocation i = approved("pending.docx", "synthetic");
    ApprovalGate restarted = new ApprovalGate(new Store(db), guard);
    assertThrows(Failure.class, () -> restarted.consume(host, exportDescriptor(), i));
    assertEquals("APPROVED", store.get("tool_approvals", i.approval).get("state"));
  }
  @Test void contentPrincipalAndRequestCannotBeChanged() {
    ToolInvocation i = approved("exact.docx", "original");
    ToolInvocation changed = new ToolInvocation(i.requestId, i.toolId, i.version, Map.of("path", "exact.docx", "text", "changed"), 3000, i.approval);
    assertThrows(Failure.class, () -> approvals.consume(host, exportDescriptor(), changed));
    Guard.Context other = new Guard.Context("other-host", Set.of("documents.export"));
    assertThrows(Failure.class, () -> approvals.consume(other, exportDescriptor(), i));
    ToolInvocation otherRequest = call(i.toolId, i.arguments, i.approval);
    assertThrows(Failure.class, () -> approvals.consume(host, exportDescriptor(), otherRequest));
  }
  @Test void collisionAndVersionsAreRejected() throws Exception {
    assertThrows(Failure.class, () -> ServicesTools.register(registry, docs, new Data(), guard));
    ToolInvocation bad = new ToolInvocation(UUID.randomUUID().toString(), "documents.search", "2.0.0", Map.of("query", "x"), 3000, null);
    assertEquals("INCOMPATIBLE_TOOL", execute(bad).error);
    assertEquals("TOOL_UNAVAILABLE", execute(call("not.available", Map.of(), null)).error);
    assertEquals("INVALID_INPUT", execute(call("documents.search", Map.of("query", "x", "unknown", "y"), null)).error);
  }
  @Test void persistentRegistryDoesNotMakeAdaptersExecutable() throws Exception {
    ToolRegistry empty = new ToolRegistry(new Store(db));
    try (ToolDispatcher d = new ToolDispatcher(empty, guard, approvals)) {
      assertTrue(empty.catalogue().isEmpty());
      assertEquals("TOOL_UNAVAILABLE", d.dispatch(host, call("documents.search", Map.of("query", "x"), null)).result.get(5, TimeUnit.SECONDS).error);
    }
  }
  @Test void cancelAndDeadlineArePropagatedAndQueuedWorkNeverRuns() throws Exception {
    CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    registry.register(ServicesTools.descriptor("data.wait", "data", "data.read", false, "test"), (ctx, args, task) -> {
      started.countDown(); try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      task.check(); return "done";
    });
    ToolDispatcher.Handle blocker = dispatcher.dispatch(host, call("data.wait", Map.of(), null));
    assertTrue(started.await(2, TimeUnit.SECONDS));
    ToolDispatcher.Handle cancelled = dispatcher.dispatch(host, call("data.csv.normalize", Map.of("csv", "a\n1\n"), null)); cancelled.cancel();
    ToolInvocation deadline = new ToolInvocation(UUID.randomUUID().toString(), "documents.search", "1.0.0", Map.of("query", "x"), 1, null);
    ToolDispatcher.Handle expired = dispatcher.dispatch(host, deadline);
    Thread.sleep(20); release.countDown();
    assertEquals(ToolDispatcher.Status.SUCCESS, blocker.result.get(5, TimeUnit.SECONDS).status);
    assertEquals("CANCELLED", cancelled.result.get(5, TimeUnit.SECONDS).error);
    assertEquals("EXPIRED", expired.result.get(5, TimeUnit.SECONDS).error);
  }
  @Test void absentApprovalAndUnsafePathNeverCreateFile() throws Exception {
    assertEquals("CONFIRMATION_REQUIRED", execute(call("documents.docx.export", Map.of("path", "missing.docx", "text", "test"), null)).error);
    assertFalse(Files.exists(root.resolve("missing.docx")));
    assertEquals(ToolDispatcher.Status.UNKNOWN, execute(approved("../escape.docx", "test")).status);
    assertFalse(Files.exists(root.resolveSibling("escape.docx")));
  }
  @Test void journalContainsNoArgumentsOrPaths() throws Exception {
    execute(approved("private-name.docx", "synthetic private payload"));
    String records = new com.google.gson.Gson().toJson(store.all("tool_approvals")) + new com.google.gson.Gson().toJson(store.all("audit"));
    assertFalse(records.contains("private-name")); assertFalse(records.contains("synthetic private payload"));
  }
}
