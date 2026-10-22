package fr.jarvis.services.tools;

import fr.jarvis.services.shared.api.Contracts;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.modules.guard.Guard;
import java.util.*;

/** Host-owned capability metadata. A descriptor is not an authorization. */
public final class ToolDescriptor {
  public final String id, version, module, permission, outputSchema;
  public final boolean mutating, requiresNetwork = false;
  public final long memoryBytes;
  public final Set<String> inputFields;

  public ToolDescriptor(String id, String version, String module, String permission,
      Set<String> fields, String outputSchema, boolean mutating, long memoryBytes) {
    if (id == null || !id.matches("[a-z][a-z0-9._-]{0,79}")
        || version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
        || !Contracts.MODULES.contains(module) || permission == null || permission.isEmpty()
        || fields == null || fields.size() > 16 || fields.stream().anyMatch(f -> !f.matches("[a-z_]{1,40}"))
        || outputSchema == null || outputSchema.isEmpty() || memoryBytes < 0)
      throw new Failure("INVALID_TOOL_DESCRIPTOR");
    this.id = id; this.version = version; this.module = module; this.permission = permission;
    this.inputFields = Collections.unmodifiableSet(new TreeSet<>(fields));
    this.outputSchema = outputSchema; this.mutating = mutating; this.memoryBytes = memoryBytes;
  }

  public Map<String, Object> metadata() {
    Map<String, Object> out = new TreeMap<>();
    out.put("id", id); out.put("version", version); out.put("module", module);
    out.put("permission", permission); out.put("input_fields", inputFields);
    out.put("input_type", "exact_string_object"); out.put("output_schema", outputSchema);
    out.put("memory_bytes", memoryBytes); out.put("requires_network", requiresNetwork);
    out.put("mode", mutating ? "ACTION" : "READ");
    out.put("consent", mutating ? "EXACT_CONTENT_SINGLE_USE" : "HOST_GRANT");
    return Collections.unmodifiableMap(out);
  }

  public String digest() { return Guard.digest(new com.google.gson.Gson().toJson(metadata())); }
}
