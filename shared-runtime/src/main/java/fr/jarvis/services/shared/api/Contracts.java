package fr.jarvis.services.shared.api;

import java.util.*;

/** In-process SERVICES contract. Not an implementation of UNIFIED IPC. */
public final class Contracts {
  private Contracts() {}

  public static final String VERSION = "1.0.0";
  public static final List<String> MODULES =
      Collections.unmodifiableList(
          Arrays.asList(
              "documents", "knowledge", "data", "actions", "memory", "analyst", "guard", "bridge"));

  public static final class Failure extends RuntimeException {
    public final String code;

    public Failure(String code) {
      super(code);
      this.code = code;
    }
  }

  public static final class Request {
    public final String request_id, schema_version, module, action;
    public final Map<String, Object> parameters;
    public final Map<String, String> permission_context =
        Collections.singletonMap("transport", "trusted_in_process");
    public final long timeout;

    public Request(
        String id,
        String version,
        String module,
        String action,
        Map<String, Object> parameters,
        long timeout) {
      try {
        UUID.fromString(id);
      } catch (Exception e) {
        throw new Failure("INVALID_REQUEST_ID");
      }
      if (!VERSION.equals(version)) throw new Failure("INCOMPATIBLE");
      if (!MODULES.contains(module)
          || action == null
          || !action.matches("[a-z_]{1,40}")
          || parameters == null
          || timeout < 1
          || timeout > 90000) throw new Failure("INVALID_INPUT");
      this.request_id = id;
      this.schema_version = version;
      this.module = module;
      this.action = action;
      this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
      this.timeout = timeout;
    }
  }

  public static final class Response {
    public final String request_id, schema_version = VERSION, status, error_code;
    public final Object result;
    public final Map<String, Object> diagnostics;

    public Response(String id, Object result, String error, long millis) {
      request_id = id;
      this.result = error == null ? result : null;
      error_code = error;
      status = error == null ? "success" : "failure";
      diagnostics = Collections.singletonMap("duration_ms", millis);
    }
  }
}
