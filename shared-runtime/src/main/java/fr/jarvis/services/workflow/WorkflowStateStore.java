package fr.jarvis.services.workflow;

import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import java.util.*;

/** State changes and append-only metadata journal commit in one SQLite transaction. */
public final class WorkflowStateStore {
  private final Store store;
  public WorkflowStateStore(Store store) { this.store = store; }
  public void prepare(WorkflowDefinition definition) {
    store.transaction(() -> {
      Map<String,Object> old = store.get("workflows", definition.id);
      if (old != null) { if (!definition.digest.equals(old.get("definition_digest"))) throw new Failure("WORKFLOW_CHANGED"); return; }
      if (store.count("workflows") >= 128) throw new Failure("WORKFLOW_LIMIT");
      Map<String,Object> row = new TreeMap<>(); row.put("id", definition.id); row.put("definition_digest", definition.digest);
      row.put("sequence", 0); Map<String,Object> states = new TreeMap<>();
      for (WorkflowDefinition.Step step : definition.ordered) states.put(step.id, "PENDING");
      row.put("steps", states); row.put("status", "PENDING");
      append(row, null, "PENDING"); store.put("workflows", definition.id, row);
    });
  }
  public Map<String,Object> snapshot(String workflowId) {
    Map<String,Object> state = store.get("workflows", workflowId);
    if (state == null) throw new Failure("NOT_FOUND"); return state;
  }
  @SuppressWarnings("unchecked")
  public String state(String workflowId, String stepId) { return (String)((Map<String,Object>)snapshot(workflowId).get("steps")).get(stepId); }
  @SuppressWarnings("unchecked")
  public void transition(String workflowId, String stepId, Set<String> expected, String target, String overall) {
    store.transaction(() -> {
      Map<String,Object> row = snapshot(workflowId); Map<String,Object> states = (Map<String,Object>)row.get("steps");
      if (!expected.contains(states.get(stepId))) throw new Failure("WORKFLOW_BUSY_OR_STALE");
      states.put(stepId, target); row.put("status", overall); append(row, stepId, target); store.put("workflows", workflowId, row);
    });
  }
  public void finish(String workflowId) {
    store.transaction(() -> { Map<String,Object> row = snapshot(workflowId); row.put("status", "SUCCEEDED"); append(row, null, "SUCCEEDED"); store.put("workflows", workflowId, row); });
  }
  private void append(Map<String,Object> row, String step, String status) {
    if (store.count("workflow_journal") >= 4096) throw new Failure("WORKFLOW_JOURNAL_FULL");
    int sequence = ((Number)row.get("sequence")).intValue() + 1; row.put("sequence", sequence);
    String id = UUID.randomUUID().toString(); Map<String,Object> event = new TreeMap<>();
    event.put("id", id); event.put("workflow", row.get("id")); event.put("step", step); event.put("sequence", sequence); event.put("state", status);
    store.put("workflow_journal", id, event);
  }
}
