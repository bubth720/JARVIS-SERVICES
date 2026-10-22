package fr.jarvis.services.tools;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.util.concurrent.*;

/** Bounded local executor. cancel requests are propagated, never reported as native release. */
public final class ToolDispatcher implements AutoCloseable {
  public enum Status { SUCCESS, REFUSED, FAILED, UNKNOWN }
  public static final class Result {
    public final String requestId, error; public final Status status; public final Object value;
    Result(String id, Status status, Object value, String error) {
      requestId = id; this.status = status; this.value = value; this.error = error;
    }
  }
  public static final class Handle {
    private final Task task; public final CompletableFuture<Result> result = new CompletableFuture<>();
    Handle(Task task) { this.task = task; }
    public void cancel() { task.cancel(); }
  }
  private final ToolRegistry registry;
  private final Guard guard;
  private final ApprovalGate approvals;
  private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
      new ArrayBlockingQueue<>(8), r -> { Thread t = new Thread(r, "services-tools"); t.setDaemon(true); return t; });
  public ToolDispatcher(ToolRegistry registry, Guard guard, ApprovalGate approvals) {
    this.registry = registry; this.guard = guard; this.approvals = approvals;
  }
  public Handle dispatch(Context host, ToolInvocation invocation) {
    Handle handle = new Handle(new Task(invocation.timeoutMs));
    try { executor.execute(() -> execute(host, invocation, handle)); }
    catch (RejectedExecutionException e) { handle.result.complete(new Result(invocation.requestId, Status.REFUSED, null, "BUSY")); }
    return handle;
  }
  private void execute(Context host, ToolInvocation invocation, Handle handle) {
    boolean actionStarted = false;
    try {
      handle.task.check();
      ToolRegistry.Entry entry = registry.resolve(invocation);
      guard.require(host, entry.descriptor.permission);
      handle.task.check();
      if (entry.descriptor.mutating) approvals.consume(host, entry.descriptor, invocation);
      handle.task.check();
      actionStarted = entry.descriptor.mutating;
      Object output = entry.adapter.execute(host, invocation.arguments, handle.task);
      handle.task.check();
      guard.audit("tool." + entry.descriptor.id, "success");
      if (actionStarted) approvals.outcome(invocation, "SUCCESS");
      handle.result.complete(new Result(invocation.requestId, Status.SUCCESS, output, null));
    } catch (RuntimeException e) {
      if (actionStarted) { try { approvals.outcome(invocation, "UNKNOWN"); } catch (RuntimeException ignored) {} }
      String code = e instanceof Failure ? ((Failure) e).code : "PROCESSING_ERROR";
      Status status = actionStarted ? Status.UNKNOWN : (e instanceof Failure ? Status.REFUSED : Status.FAILED);
      // No arguments, principal, paths or exception message in the journal.
      try { guard.audit("tool.dispatch", status.name()); } catch (RuntimeException ignored) { /* Result remains fail-closed if audit storage fails. */ }
      handle.result.complete(new Result(invocation.requestId, status, null, code));
    }
  }
  /** Does not interrupt a running operation or imply resource cleanup. Queued tasks drain. */
  public void close() { executor.shutdown(); }
}
