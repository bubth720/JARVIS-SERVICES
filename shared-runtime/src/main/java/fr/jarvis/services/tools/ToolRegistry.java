package fr.jarvis.services.tools;

import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import java.util.*;

/** Metadata is persisted; executable adapters can only be registered by the trusted host. */
public final class ToolRegistry {
  @FunctionalInterface public interface Adapter {
    Object execute(Context context, Map<String, String> arguments, Task task);
  }
  static final class Entry {
    final ToolDescriptor descriptor; final Adapter adapter;
    Entry(ToolDescriptor d, Adapter a) { descriptor = d; adapter = a; }
  }
  private final Store store;
  private final Map<String, Entry> entries = new TreeMap<>();
  public ToolRegistry(Store store) { this.store = store; }
  public synchronized void register(ToolDescriptor d, Adapter adapter) {
    if (adapter == null || entries.size() >= 128 || entries.containsKey(d.id)) throw new Failure("TOOL_COLLISION");
    Map<String, Object> prior = store.get("tool_registry", d.id);
    if (prior != null && !d.digest().equals(prior.get("digest"))) throw new Failure("INCOMPATIBLE_TOOL");
    Map<String, Object> metadata = new TreeMap<>(d.metadata()); metadata.put("digest", d.digest());
    store.put("tool_registry", d.id, metadata); entries.put(d.id, new Entry(d, adapter));
  }
  /** Host-side metadata lookup, never a grant or executable object from serialized input. */
  public synchronized ToolDescriptor descriptor(String toolId, String version) {
    Entry entry = entries.get(toolId);
    if (entry == null) throw new Failure("TOOL_UNAVAILABLE");
    if (!entry.descriptor.version.equals(version)) throw new Failure("INCOMPATIBLE_TOOL");
    return entry.descriptor;
  }
  synchronized Entry resolve(ToolInvocation invocation) {
    Entry entry = entries.get(invocation.toolId);
    if (entry == null) throw new Failure("TOOL_UNAVAILABLE");
    if (!entry.descriptor.version.equals(invocation.version)) throw new Failure("INCOMPATIBLE_TOOL");
    if (!entry.descriptor.inputFields.equals(invocation.arguments.keySet())) throw new Failure("INVALID_INPUT");
    return entry;
  }
  public synchronized List<Map<String, Object>> catalogue() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Entry entry : entries.values()) out.add(entry.descriptor.metadata());
    return Collections.unmodifiableList(out);
  }
}
