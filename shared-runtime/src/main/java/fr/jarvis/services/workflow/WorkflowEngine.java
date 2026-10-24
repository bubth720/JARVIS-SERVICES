package fr.jarvis.services.workflow;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.tools.*;
import java.util.*;
import java.util.concurrent.*;

/** Caller-driven scheduler. No permanent Android service and no automatic replay of effects. */
public final class WorkflowEngine {
  public static final class Progress {
    public final String state, step;
    public final ToolInvocation approvalRequest;
    public final ToolDispatcher.Result result;

    Progress(String state, String step, ToolInvocation approval, ToolDispatcher.Result result) {
      this.state = state;
      this.step = step;
      approvalRequest = approval;
      this.result = result;
    }
  }

  private volatile ToolDispatcher.Handle active;

  public void cancelActive() {
    ToolDispatcher.Handle current = active;
    if (current != null) current.cancel();
  }

  private final WorkflowStateStore states;
  private final Guard guard;
  private final ToolRegistry registry;
  private final ToolDispatcher dispatcher;

  public WorkflowEngine(
      WorkflowStateStore states, Guard guard, ToolRegistry registry, ToolDispatcher dispatcher) {
    this.states = states;
    this.guard = guard;
    this.registry = registry;
    this.dispatcher = dispatcher;
  }

  /**
   * Executes at most one ready step, making interruption between steps explicit and reproducible.
   */
  public Progress advance(
      Context host, WorkflowDefinition definition, Map<String, String> receipts) {
    guard.require(host, "actions.run");
    states.prepare(definition);
    for (WorkflowDefinition.Step step : definition.ordered) {
      String state = states.state(definition.id, step.id);
      if ("SUCCEEDED".equals(state)) continue;
      if (Arrays.asList("RUNNING", "UNKNOWN", "FAILED", "CANCELLED").contains(state))
        return new Progress(state, step.id, null, null);
      boolean ready =
          step.dependencies.stream()
              .allMatch(id -> "SUCCEEDED".equals(states.state(definition.id, id)));
      if (!ready) return new Progress("PENDING", step.id, null, null);
      ToolDescriptor descriptor = registry.descriptor(step.tool, step.version);
      guard.require(host, descriptor.permission);
      String receipt = receipts == null ? null : receipts.get(step.id);
      if (descriptor.mutating && receipt == null) {
        if (!"WAITING_APPROVAL".equals(state))
          states.transition(
              definition.id,
              step.id,
              new HashSet<>(Arrays.asList("PENDING")),
              "WAITING_APPROVAL",
              "WAITING_APPROVAL");
        return new Progress("WAITING_APPROVAL", step.id, definition.invocation(step, null), null);
      }
      states.transition(
          definition.id,
          step.id,
          new HashSet<>(Arrays.asList("PENDING", "WAITING_APPROVAL")),
          "RUNNING",
          "RUNNING");
      ToolDispatcher.Handle handle =
          dispatcher.dispatch(host, definition.invocation(step, receipt));
      active = handle;
      try {
        ToolDispatcher.Result result =
            handle.result.get(step.timeoutMs + 1000, TimeUnit.MILLISECONDS);
        String target =
            result.status == ToolDispatcher.Status.SUCCESS
                ? "SUCCEEDED"
                : result.status == ToolDispatcher.Status.UNKNOWN ? "UNKNOWN" : "FAILED";
        states.transition(
            definition.id,
            step.id,
            new HashSet<>(Arrays.asList("RUNNING")),
            target,
            target.equals("SUCCEEDED") ? "PENDING" : target);
        boolean finished =
            definition.ordered.stream()
                .allMatch(s -> "SUCCEEDED".equals(states.state(definition.id, s.id)));
        if (finished) states.finish(definition.id);
        return new Progress(finished ? "SUCCEEDED" : target, step.id, null, result);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        handle.cancel();
        states.transition(
            definition.id, step.id, new HashSet<>(Arrays.asList("RUNNING")), "UNKNOWN", "UNKNOWN");
        return new Progress("UNKNOWN", step.id, null, null);
      } catch (ExecutionException | TimeoutException e) {
        handle.cancel();
        states.transition(
            definition.id, step.id, new HashSet<>(Arrays.asList("RUNNING")), "UNKNOWN", "UNKNOWN");
        return new Progress("UNKNOWN", step.id, null, null);
      } finally {
        active = null;
      }
    }
    return new Progress("SUCCEEDED", null, null, null);
  }

  /** A new host may conservatively record unknown in-flight state; never reset or retry it. */
  public void recoverAfterHostRestart(Context host, WorkflowDefinition definition) {
    guard.require(host, "actions.run");
    states.prepare(definition);
    for (WorkflowDefinition.Step step : definition.ordered)
      if ("RUNNING".equals(states.state(definition.id, step.id)))
        states.transition(
            definition.id, step.id, new HashSet<>(Arrays.asList("RUNNING")), "UNKNOWN", "UNKNOWN");
  }
}
