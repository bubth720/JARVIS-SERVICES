package fr.jarvis.services.modules.actions;

import com.google.gson.Gson;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import java.util.*;

/** Closed catalogue: no shell, reflection or dynamically registered model tools. */
public final class Actions {
  public static final class Step {
    public final String tool, path, format, text;

    public Step(String tool, String path, String format, String text) {
      if (!"document.export".equals(tool)
          || path == null
          || text == null
          || text.length() > Documents.MAX_TEXT
          || !Arrays.asList("docx", "txt", "md", "csv", "json").contains(format))
        throw new Failure("INVALID_TOOL");
      this.tool = tool;
      this.path = path;
      this.format = format;
      this.text = text;
    }
  }

  private final Store store;
  private final Guard guard;
  private final Documents docs;

  public Actions(Store store, Guard guard, Documents docs) {
    this.store = store;
    this.guard = guard;
    this.docs = docs;
  }

  public Set<String> catalogue() {
    return Collections.singleton("document.export");
  }

  public Map<String, Object> propose(Context ctx, List<Step> steps) {
    guard.require(ctx, "actions.run");
    if (steps.isEmpty() || steps.size() > 10) throw new Failure("INVALID_WORKFLOW");
    List<Step> copy = Collections.unmodifiableList(new ArrayList<>(steps));
    for (Step s : copy) guard.path(ctx, "documents.export", s.path);
    String id = UUID.randomUUID().toString();
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("id", id);
    r.put("steps", copy);
    r.put("status", "proposed");
    r.put("completed", 0);
    r.put("journal", new ArrayList<>());
    store.put("actions", id, r);
    return state(ctx, id);
  }

  public Map<String, Object> state(Context ctx, String id) {
    guard.require(ctx, "actions.run");
    Map<String, Object> r = store.get("actions", id);
    if (r == null) throw new Failure("NOT_FOUND");
    return r;
  }

  public String exact(Context ctx, String id) {
    Map<String, Object> r = state(ctx, id);
    return id + ":" + new Gson().toJson(r.get("steps"));
  }

  public synchronized Map<String, Object> run(Context ctx, String id, String token, Task task) {
    Map<String, Object> r = state(ctx, id);
    if (!r.get("status").equals("proposed")) throw new Failure("UNSAFE_REPLAY");
    guard.consume(ctx, "actions.run", exact(ctx, id), token);
    r.put("status", "confirmed");
    store.put("actions", id, r);
    List<?> steps = (List<?>) r.get("steps");
    List<Object> journal = new ArrayList<>();
    try {
      for (Object item : steps) {
        task.check();
        Step step = new Gson().fromJson(new Gson().toJson(item), Step.class);
        new Step(step.tool, step.path, step.format, step.text);
        r.put("status", "launched");
        store.put("actions", id, r);
        Map<String, Object> result = docs.export(ctx, step.path, step.format, step.text, task);
        journal.add(result);
        r.put("journal", journal);
        r.put("completed", journal.size());
        store.put("actions", id, r);
      }
      r.put("status", "succeeded");
    } catch (RuntimeException e) {
      r.put(
          "status",
          e instanceof Failure && ((Failure) e).code.equals("CANCELLED") ? "cancelled" : "failed");
      r.put("error_code", e instanceof Failure ? ((Failure) e).code : "PROCESSING_ERROR");
      store.put("actions", id, r);
      throw e;
    }
    store.put("actions", id, r);
    return r;
  }

  public void recover(Context ctx) {
    guard.require(ctx, "actions.run");
    for (Map<String, Object> r : store.all("actions"))
      if (Arrays.asList("launched", "confirmed").contains(r.get("status"))) {
        r.put("status", "unknown");
        store.put("actions", (String) r.get("id"), r);
      }
  }
}
