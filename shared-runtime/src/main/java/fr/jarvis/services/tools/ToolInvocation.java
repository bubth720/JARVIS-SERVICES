package fr.jarvis.services.tools;

import com.google.gson.Gson;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import java.util.*;

/** Public arguments contain no host Context and cannot manufacture grants. */
public final class ToolInvocation {
  public final String requestId, toolId, version, approval;
  public final Map<String, String> arguments;
  public final long timeoutMs;

  public ToolInvocation(
      String requestId,
      String toolId,
      String version,
      Map<String, String> arguments,
      long timeoutMs,
      String approval) {
    try {
      if (!UUID.fromString(requestId).toString().equals(requestId)) throw new Exception();
    } catch (Exception e) {
      throw new Failure("INVALID_REQUEST_ID");
    }
    if (toolId == null
        || !toolId.matches("[a-z][a-z0-9._-]{0,79}")
        || version == null
        || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
        || arguments == null
        || arguments.size() > 16
        || arguments.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getValue() == null)
        || timeoutMs < 1
        || timeoutMs > 90000) throw new Failure("INVALID_INPUT");
    long bytes = 0;
    for (Map.Entry<String, String> e : arguments.entrySet()) {
      if (!e.getKey().matches("[a-z_]{1,40}")) throw new Failure("INVALID_INPUT");
      bytes += e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      if (bytes > 2 * 1024 * 1024) throw new Failure("INPUT_TOO_LARGE");
    }
    this.requestId = requestId;
    this.toolId = toolId;
    this.version = version;
    this.arguments = Collections.unmodifiableMap(new TreeMap<>(arguments));
    this.timeoutMs = timeoutMs;
    this.approval = approval;
  }

  public String contentDigest() {
    return Guard.digest(toolId + "\n" + version + "\n" + new Gson().toJson(arguments));
  }
}
