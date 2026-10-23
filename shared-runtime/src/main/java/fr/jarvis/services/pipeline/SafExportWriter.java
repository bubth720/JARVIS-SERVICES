package fr.jarvis.services.pipeline;

import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import java.nio.file.*;
import java.util.*;

/** Confirmed fresh-document SAF export with verified readback and explicit rollback outcome. */
public final class SafExportWriter {
  public static final ToolDescriptor DESCRIPTOR = ServicesTools.descriptor(
      "documents.saf.export", "documents", "documents.export", true, "fresh_document_digest", "target_ref", "name", "format", "text");
  private final Documents documents; private final Guard guard; private final ApprovalGate approvals; private final SafUriPort port;
  public SafExportWriter(Documents documents, Guard guard, ApprovalGate approvals, SafUriPort port) {
    this.documents = documents; this.guard = guard; this.approvals = approvals; this.port = port;
  }
  /** Show this exact generated name in the host preview before confirming the invocation. */
  public static String destinationName(ToolInvocation invocation) {
    String name = invocation.arguments.get("name"), format = invocation.arguments.get("format");
    if (name == null || name.isEmpty() || name.length() > 80 || name.equals(".") || name.equals("..")
        || name.chars().anyMatch(c -> c < 32 || c == 127 || c == '/' || c == '\\')
        || !Arrays.asList("md", "docx", "txt").contains(format)) throw new Failure("INVALID_EXPORT_NAME");
    return name + "-" + invocation.requestId + "." + format;
  }
  public Map<String,Object> write(Context host, ToolInvocation approved, Task task) {
    guard.require(host, "documents.export"); task.check();
    if (!DESCRIPTOR.id.equals(approved.toolId) || !DESCRIPTOR.version.equals(approved.version)
        || !DESCRIPTOR.inputFields.equals(approved.arguments.keySet())) throw new Failure("INVALID_INPUT");
    String name = destinationName(approved), format = approved.arguments.get("format");
    if (!port.targetReference().equals(approved.arguments.get("target_ref"))) throw new Failure("INVALID_URI_REFERENCE");
    approvals.consume(host, DESCRIPTOR, approved);
    String temporary = ".saf-" + UUID.randomUUID() + "." + format;
    Path file = guard.path(host, "documents.export", temporary);
    SafUriPort.Transaction tx = null; boolean committed = false, beginAttempted = false;
    try {
      Map<String,Object> encoded = documents.export(host, temporary, format, approved.arguments.get("text"), task);
      byte[] bytes = Files.readAllBytes(file);
      if (bytes.length > Documents.MAX_BYTES || !Guard.digest(bytes).equals(encoded.get("sha256"))) throw new Failure("INTEGRITY_FAILED");
      beginAttempted = true;
      tx = port.begin(approved.arguments.get("target_ref"), name,
          format.equals("docx") ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document" : "text/plain", task);
      tx.write(bytes, task); task.check();
      String digest = tx.readbackSha256(task);
      if (!Guard.digest(bytes).equals(digest)) throw new Failure("INTEGRITY_FAILED");
      task.check(); String uri = tx.commit(task); committed = true;
      approvals.outcome(approved, "SUCCESS");
      Map<String,Object> result = new LinkedHashMap<>(); result.put("uri", uri); result.put("name", name);
      result.put("sha256", digest); result.put("bytes", bytes.length); result.put("publication", "VERIFIED_FRESH_DOCUMENT");
      return Collections.unmodifiableMap(result);
    } catch (Exception failure) {
      boolean rolledBack = false;
      if (!committed) {
        if (tx != null) { try { rolledBack = tx.rollback(); } catch (RuntimeException ignored) {} }
        else rolledBack = !beginAttempted; // No handle after a creation attempt is never evidence of no side effect.
      }
      String outcome = rolledBack ? "FAILED" : "UNKNOWN";
      try { approvals.outcome(approved, outcome); } catch (RuntimeException ignored) {}
      if (!rolledBack) throw new Failure("SAF_OUTCOME_UNKNOWN");
      if (failure instanceof Failure) throw (Failure)failure;
      throw new Failure("SAF_EXPORT_FAILED");
    } finally {
      try { Files.deleteIfExists(file); } catch (java.io.IOException ignored) { /* Only our fresh private temporary is touched. */ }
    }
  }
}
