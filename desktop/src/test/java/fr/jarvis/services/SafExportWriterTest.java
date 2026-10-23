package fr.jarvis.services;

import fr.jarvis.services.pipeline.*;
import fr.jarvis.services.platform.*;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Provider boundary doubles test protocol logic; never evidence of Android SAF execution. */
class SafExportWriterTest {
  @TempDir Path root;
  JdbcDatabase db; Store store; Guard guard; Documents documents; ApprovalGate approvals;
  Guard.Context host = new Guard.Context("synthetic", Set.of("confirm", "documents.export"));
  @BeforeEach void setup() { db = new JdbcDatabase(root.resolve("fixture.db")); store = new Store(db); guard = new Guard(root, store); documents = new Documents(guard, store, null); approvals = new ApprovalGate(store, guard); }
  @AfterEach void close() { db.close(); }
  ToolInvocation approve(String format) {
    ToolInvocation p = new ToolInvocation(UUID.randomUUID().toString(), SafExportWriter.DESCRIPTOR.id, "1.0.0",
        Map.of("target_ref", "opaque-selected-tree", "name", "report", "format", format, "text", "Synthetic exact evidence"), 3000, null);
    String receipt = approvals.confirm(host, SafExportWriter.DESCRIPTOR, p);
    return new ToolInvocation(p.requestId, p.toolId, p.version, p.arguments, 3000, receipt);
  }
  static class Port implements SafUriPort {
    byte[] written; boolean begun, committed, rolledBack, corrupt, failWrite, failRollback, failBeginUnknown;
    public String targetReference() { return "opaque-selected-tree"; }
    public Transaction begin(String ref, String name, String mime, Task task) {
      begun = true; if (failBeginUnknown) throw new Failure("SAF_CREATION_UNKNOWN");
      return new Transaction() {
        public void write(byte[] bytes, Task task) { written = bytes.clone(); if (failWrite) throw new Failure("SAF_WRITE_FAILED"); }
        public String readbackSha256(Task task) { return Guard.digest(corrupt ? new byte[]{1} : written); }
        public String commit(Task task) { task.check(); committed = true; return "content://synthetic/new-document"; }
        public boolean rollback() { rolledBack = !failRollback; return rolledBack; }
      };
    }
  }
  @Test void verifiedMarkdownCommitsOnlyAfterReadbackAndCannotReplay() {
    Port port = new Port(); ToolInvocation i = approve("md"); SafExportWriter writer = new SafExportWriter(documents, guard, approvals, port);
    Map<String,Object> output = writer.write(host, i, new Task(3000));
    assertEquals("VERIFIED_FRESH_DOCUMENT", output.get("publication")); assertTrue(port.committed); assertFalse(port.rolledBack);
    assertEquals("Synthetic exact evidence", new String(port.written, java.nio.charset.StandardCharsets.UTF_8));
    assertEquals("SUCCESS", store.get("tool_approvals", i.approval).get("outcome"));
    assertThrows(Failure.class, () -> writer.write(host, i, new Task(3000)));
  }
  @Test void docxUsesExistingSecureEncoderAndReader() throws Exception {
    Port port = new Port(); new SafExportWriter(documents, guard, approvals, port).write(host, approve("docx"), new Task(3000));
    assertTrue(Documents.readDocx(port.written).contains("Synthetic exact evidence"));
  }
  @Test void corruptReadbackRollsBackFreshOutputAndRefusesSuccess() {
    Port port = new Port(); port.corrupt = true; ToolInvocation i = approve("md");
    Failure error = assertThrows(Failure.class, () -> new SafExportWriter(documents, guard, approvals, port).write(host, i, new Task(3000)));
    assertEquals("INTEGRITY_FAILED", error.code); assertTrue(port.rolledBack); assertFalse(port.committed);
    assertEquals("FAILED", store.get("tool_approvals", i.approval).get("outcome"));
  }
  @Test void writeFailureAndFailedCleanupRemainUnknown() {
    Port port = new Port(); port.failWrite = true; port.failRollback = true; ToolInvocation i = approve("md");
    Failure error = assertThrows(Failure.class, () -> new SafExportWriter(documents, guard, approvals, port).write(host, i, new Task(3000)));
    assertEquals("SAF_OUTCOME_UNKNOWN", error.code); assertFalse(port.committed);
    assertEquals("UNKNOWN", store.get("tool_approvals", i.approval).get("outcome"));
  }
  @Test void lostCreationAcknowledgementRemainsUnknown() {
    Port port = new Port(); port.failBeginUnknown = true; ToolInvocation i = approve("md");
    Failure error = assertThrows(Failure.class, () -> new SafExportWriter(documents, guard, approvals, port).write(host, i, new Task(3000)));
    assertEquals("SAF_OUTCOME_UNKNOWN", error.code); assertEquals("UNKNOWN", store.get("tool_approvals", i.approval).get("outcome"));
  }
  @Test void foreignTargetOrMissingPermissionCannotTouchProvider() {
    Port port = new Port(); ToolInvocation i = approve("md");
    ToolInvocation changed = new ToolInvocation(i.requestId, i.toolId, i.version,
        Map.of("target_ref", "unselected", "name", "report", "format", "md", "text", "Synthetic exact evidence"), 3000, i.approval);
    assertThrows(Failure.class, () -> new SafExportWriter(documents, guard, approvals, port).write(host, changed, new Task(3000)));
    assertFalse(port.begun);
    assertThrows(Failure.class, () -> new SafExportWriter(documents, guard, approvals, port).write(new Guard.Context("denied", Set.of()), i, new Task(3000)));
    assertFalse(port.begun);
  }
  @Test void cancellationBeforeWriteCreatesNothing() {
    Port port = new Port(); Task t = new Task(3000); t.cancel();
    assertThrows(Failure.class, () -> new SafExportWriter(documents, guard, approvals, port).write(host, approve("md"), t));
    assertFalse(port.begun);
  }
}
