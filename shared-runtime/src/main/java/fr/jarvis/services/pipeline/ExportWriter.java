package fr.jarvis.services.pipeline;

import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Publish a verified private temporary file without overwriting any original.
 * No SAF provider is assumed atomic: unsupported providers must refuse publication.
 */
public final class ExportWriter {
  public static final ToolDescriptor DESCRIPTOR = ServicesTools.descriptor(
      "documents.report.export", "documents", "documents.export", true, "file_digest", "path", "format", "text");
  private final Documents documents; private final Guard guard; private final ApprovalGate approvals;
  public ExportWriter(Documents documents, Guard guard, ApprovalGate approvals) {
    this.documents = documents; this.guard = guard; this.approvals = approvals;
  }
  public Map<String,Object> write(Context host, ToolInvocation approved, Task task) {
    guard.require(host, "documents.export"); task.check();
    if (!DESCRIPTOR.id.equals(approved.toolId) || !DESCRIPTOR.version.equals(approved.version)
        || !DESCRIPTOR.inputFields.equals(approved.arguments.keySet())) throw new Failure("INVALID_INPUT");
    String format = approved.arguments.get("format"), relative = approved.arguments.get("path");
    if (!Arrays.asList("md", "docx", "txt").contains(format)) throw new Failure("UNSUPPORTED_FORMAT");
    Path destination = guard.path(host, "documents.export", relative);
    if (Files.exists(destination)) throw new Failure("WOULD_OVERWRITE");
    approvals.consume(host, DESCRIPTOR, approved);
    String tempName = ".pipeline-" + UUID.randomUUID() + "." + format;
    Path temp = guard.path(host, "documents.export", tempName);
    boolean published = false;
    try {
      Map<String,Object> result = documents.export(host, tempName, format, approved.arguments.get("text"), task);
      task.check();
      // A hard link is atomic and fails if destination already exists. Unlike rename+exists,
      // no race can replace an existing file. Unsupported filesystems fail closed.
      Files.createLink(destination, temp); published = true;
      Map<String,Object> out = new LinkedHashMap<>(result); out.put("path", relative);
      approvals.outcome(approved, "SUCCESS");
      return out;
    } catch (RuntimeException | IOException e) {
      try { approvals.outcome(approved, published ? "UNKNOWN" : "FAILED"); } catch (RuntimeException ignored) {}
      if (published) throw new Failure("EXPORT_OUTCOME_UNKNOWN");
      if (e instanceof Failure) throw (Failure)e;
      throw new Failure(e instanceof FileAlreadyExistsException ? "WOULD_OVERWRITE" : "ATOMIC_EXPORT_UNAVAILABLE");
    } finally {
      // Only the fresh private temporary name is eligible for cleanup, never the original/destination.
      try { Files.deleteIfExists(temp); } catch (IOException ignored) { /* Safe orphan; no success claim about cleanup. */ }
    }
  }
}
