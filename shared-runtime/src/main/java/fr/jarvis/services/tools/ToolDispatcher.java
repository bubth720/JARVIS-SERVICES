package fr.jarvis.services.tools;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.util.concurrent.*;

/** Bounded local executor. cancel requests are propagated, never reported as native release. */
public final class ToolDispatcher implements AutoCloseable {
  public enum Status {
    SUCCESS,
    REFUSED,
    FAILED,
    UNKNOWN
  }

  public static final class Result {
    public final String requestId, error;
    public final Status status;
    public final Object value;

    Result(String id, Status status, Object value, String error) {
      requestId = id;
      this.status = status;
      this.value = value;
      this.error = error;
    }
  }

  public static final class Handle {
    private final Task task;
    public final CompletableFuture<Result> result = new CompletableFuture<>();

    Handle(Task task) {
      this.task = task;
    }

    public void cancel() {
      task.cancel();
    }
  }

  private final ToolRegistry registry;
  private final Guard guard;
  private final ApprovalGate approvals;
  private final fr.jarvis.services.shared.storage.Store journal;
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(8),
          r -> {
            Thread t = new Thread(r, "services-tools");
            t.setDaemon(true);
            return t;
          });

  public ToolDispatcher(ToolRegistry registry, Guard guard, ApprovalGate approvals) {
    this(registry, guard, approvals, null);
  }

  /** Optional bounded append-only execution evidence for the standalone workspace. */
  public ToolDispatcher(
      ToolRegistry registry,
      Guard guard,
      ApprovalGate approvals,
      fr.jarvis.services.shared.storage.Store journal) {
    this.registry = registry;
    this.guard = guard;
    this.approvals = approvals;
    this.journal = journal;
  }

  public Handle dispatch(Context host, ToolInvocation invocation) {
    Handle handle = new Handle(new Task(invocation.timeoutMs));
    try {
      executor.execute(() -> execute(host, invocation, handle));
    } catch (RejectedExecutionException e) {
      handle.result.complete(new Result(invocation.requestId, Status.REFUSED, null, "BUSY"));
    }
    return handle;
  }

  private void execute(Context host, ToolInvocation invocation, Handle handle) {
    boolean actionStarted = false, journalAdmitted = false;
    try {
      record(invocation, "REQUESTED", null, true);
      journalAdmitted = true;
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
      record(invocation, "SUCCESS", null, false);
      handle.result.complete(new Result(invocation.requestId, Status.SUCCESS, output, null));
    } catch (RuntimeException e) {
      if (actionStarted) {
        try {
          approvals.outcome(invocation, "UNKNOWN");
        } catch (RuntimeException ignored) {
        }
      }
      String code = e instanceof Failure ? ((Failure) e).code : "PROCESSING_ERROR";
      Status status =
          actionStarted ? Status.UNKNOWN : (e instanceof Failure ? Status.REFUSED : Status.FAILED);
      // No arguments, principal, paths or exception message in the journal.
      try {
        guard.audit("tool.dispatch", status.name());
      } catch (RuntimeException ignored) {
        /* Result remains fail-closed if audit storage fails. */
      }
      if (journalAdmitted) {
        try {
          record(invocation, status.name(), code, false);
        } catch (RuntimeException ignored) {
        }
      }
      handle.result.complete(new Result(invocation.requestId, status, null, code));
    }
  }

  private void record(
      ToolInvocation invocation, String status, String error, boolean reserveCompletion) {
    if (journal == null) return;
    journal.transaction(
        () -> {
          if (journal.count("tool_executions") >= (reserveCompletion ? 4095 : 4096))
            throw new Failure("EXECUTION_JOURNAL_FULL");
          java.util.Map<String, Object> row = new java.util.TreeMap<>();
          row.put("request_id", invocation.requestId);
          row.put("tool", invocation.toolId);
          row.put("version", invocation.version);
          row.put("arguments_digest", invocation.contentDigest());
          row.put("status", status);
          if (error != null)
            row.put("error", error.matches("[A-Z_]{2,80}") ? error : "PROCESSING_ERROR");
          row.put("timestamp", Long.toString(System.currentTimeMillis()));
          journal.put("tool_executions", java.util.UUID.randomUUID().toString(), row);
        });
  }

  /** Does not interrupt a running operation or imply resource cleanup. Queued tasks drain. */
  public void close() {
    executor.shutdown();
  }
}
