package fr.jarvis.services.tools;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import java.util.*;

/** Durable, consumed-before-action receipt. Pending receipts fail closed after restart.
 * Only a trusted host calls confirm after displaying the exact input; never a model tool.
 */
public final class ApprovalGate {
  private final Store store;
  private final Guard guard;
  private final String hostEpoch = UUID.randomUUID().toString();
  public ApprovalGate(Store store, Guard guard) { this.store = store; this.guard = guard; }

  public String confirm(Context host, ToolDescriptor descriptor, ToolInvocation invocation) {
    guard.require(host, "confirm"); guard.require(host, descriptor.permission);
    if (!descriptor.mutating || !descriptor.id.equals(invocation.toolId)
        || !descriptor.version.equals(invocation.version)
        || !descriptor.inputFields.equals(invocation.arguments.keySet())) throw new Failure("INVALID_INPUT");
    if (store.count("tool_approvals") >= 1024) throw new Failure("APPROVAL_JOURNAL_FULL");
    String token = UUID.randomUUID().toString();
    Map<String, Object> row = new TreeMap<>();
    row.put("id", token); row.put("state", "APPROVED"); row.put("host_epoch", hostEpoch);
    row.put("principal_digest", Guard.digest(host.principal)); row.put("tool", descriptor.id);
    row.put("descriptor_digest", descriptor.digest()); row.put("content_digest", invocation.contentDigest());
    row.put("request_id", invocation.requestId);
    row.put("deadline_nanos", Long.toString(System.nanoTime() + 60_000_000_000L));
    store.put("tool_approvals", token, row);
    return token;
  }

  public void consume(Context host, ToolDescriptor descriptor, ToolInvocation invocation) {
    guard.require(host, descriptor.permission);
    if (invocation.approval == null) throw new Failure("CONFIRMATION_REQUIRED");
    store.transaction(() -> {
      Map<String, Object> row = store.get("tool_approvals", invocation.approval);
      if (row == null || !"APPROVED".equals(row.get("state"))
          || !hostEpoch.equals(row.get("host_epoch"))
          || !Guard.digest(host.principal).equals(row.get("principal_digest"))
          || !descriptor.digest().equals(row.get("descriptor_digest"))
          || !invocation.contentDigest().equals(row.get("content_digest"))
          || !invocation.requestId.equals(row.get("request_id"))
          || System.nanoTime() > Long.parseLong((String) row.get("deadline_nanos")))
        throw new Failure("CONFIRMATION_REQUIRED");
      row.put("state", "CONSUMED"); row.put("outcome", "UNKNOWN");
      store.put("tool_approvals", invocation.approval, row);
    });
  }
  public void outcome(ToolInvocation invocation, String outcome) {
    if (invocation.approval == null) return;
    store.transaction(() -> {
      Map<String, Object> row = store.get("tool_approvals", invocation.approval);
      if (row == null || !"CONSUMED".equals(row.get("state"))
          || !hostEpoch.equals(row.get("host_epoch"))
          || !invocation.requestId.equals(row.get("request_id"))) throw new Failure("INVALID_RECEIPT");
      row.put("outcome", outcome);
      store.put("tool_approvals", invocation.approval, row);
    });
  }
}
