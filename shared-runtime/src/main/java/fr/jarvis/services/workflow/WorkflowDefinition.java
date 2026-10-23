package fr.jarvis.services.workflow;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.tools.ToolInvocation;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable closed DAG. Payloads remain in host memory and are never journaled. */
public final class WorkflowDefinition {
  public static final class Step {
    public final String id, tool, version; public final Set<String> dependencies;
    public final Map<String,String> arguments; public final long timeoutMs;
    public Step(String id, String tool, String version, Set<String> dependencies, Map<String,String> arguments, long timeoutMs) {
      if (id == null || !id.matches("[a-z][a-z0-9_-]{0,39}") || dependencies == null || dependencies.size() > 10)
        throw new Failure("INVALID_WORKFLOW");
      ToolInvocation check = new ToolInvocation(UUID.randomUUID().toString(), tool, version, arguments, timeoutMs, null);
      this.id = id; this.tool = tool; this.version = version;
      this.dependencies = Collections.unmodifiableSet(new TreeSet<>(dependencies));
      this.arguments = check.arguments; this.timeoutMs = timeoutMs;
    }
  }
  public final String id, digest; public final List<Step> ordered;
  public WorkflowDefinition(String id, List<Step> steps) {
    try { if (!UUID.fromString(id).toString().equals(id)) throw new Exception(); }
    catch (Exception e) { throw new Failure("INVALID_WORKFLOW_ID"); }
    if (steps == null || steps.isEmpty() || steps.size() > 10) throw new Failure("INVALID_WORKFLOW");
    Map<String,Step> pending = new TreeMap<>();
    for (Step step : steps) if (step == null || pending.put(step.id, step) != null) throw new Failure("STEP_COLLISION");
    Set<String> known = new HashSet<>(pending.keySet());
    for (Step step : steps) if (!known.containsAll(step.dependencies)) throw new Failure("UNKNOWN_DEPENDENCY");
    List<Step> sorted = new ArrayList<>(); Set<String> done = new HashSet<>();
    while (!pending.isEmpty()) {
      Step next = pending.values().stream().filter(s -> done.containsAll(s.dependencies)).findFirst().orElseThrow(() -> new Failure("WORKFLOW_CYCLE"));
      pending.remove(next.id); sorted.add(next); done.add(next.id);
    }
    this.id = id; this.ordered = Collections.unmodifiableList(sorted);
    List<Map<String,Object>> metadata = new ArrayList<>();
    for (Step s : sorted) {
      Map<String,Object> row = new TreeMap<>(); row.put("id", s.id); row.put("dependencies", s.dependencies);
      row.put("tool", s.tool); row.put("version", s.version); row.put("timeout", s.timeoutMs);
      row.put("arguments_digest", Guard.digest(new com.google.gson.Gson().toJson(s.arguments))); metadata.add(row);
    }
    digest = Guard.digest(new com.google.gson.Gson().toJson(metadata));
  }
  public ToolInvocation invocation(Step step, String receipt) {
    String request = UUID.nameUUIDFromBytes((id + ":" + step.id).getBytes(StandardCharsets.UTF_8)).toString();
    return new ToolInvocation(request, step.tool, step.version, step.arguments, step.timeoutMs, receipt);
  }
}
