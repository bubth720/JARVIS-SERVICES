package fr.jarvis.services.modules.guard;

import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Trusted host constructs grants; no serialized document/model can mint a Context. */
public class AccessGuard {
  public static class Context {
    public final String principal;
    private final Set<String> grants;

    public Context(String principal, Set<String> grants) {
      if (principal == null || principal.isEmpty()) throw new Failure("UNAUTHORIZED");
      this.principal = principal;
      this.grants = new HashSet<>(grants);
    }
  }

  private static final class Approval {
    final String principal, operation, digest;
    final long expires;

    Approval(String p, String o, String d) {
      principal = p;
      operation = o;
      digest = d;
      expires = System.nanoTime() + 60_000_000_000L;
    }
  }

  private final Store store;
  private final Map<String, Approval> approvals = new HashMap<>();

  public AccessGuard(Store store) { this.store = Objects.requireNonNull(store); }

  public void require(Context ctx, String operation) {
    boolean allowed = ctx != null && ctx.grants.contains(operation);
    audit(operation, allowed ? "allowed" : "denied");
    if (!allowed) throw new Failure("UNAUTHORIZED");
  }

  /** Call ONLY after a trusted UI has displayed and confirmed the exact operation/content. */
  public synchronized String confirm(Context ctx, String operation, String content) {
    require(ctx, "confirm");
    require(ctx, operation);
    approvals.entrySet().removeIf(e -> System.nanoTime() > e.getValue().expires);
    if (approvals.size() >= 128) throw new Failure("TOO_MANY_CONFIRMATIONS");
    String token = UUID.randomUUID().toString();
    approvals.put(token, new Approval(ctx.principal, operation, digest(content)));
    return token;
  }

  public synchronized void consume(Context ctx, String operation, String content, String token) {
    require(ctx, operation);
    Approval a = approvals.remove(token);
    if (a == null
        || !a.principal.equals(ctx.principal)
        || !a.operation.equals(operation)
        || !a.digest.equals(digest(content))
        || System.nanoTime() > a.expires) throw new Failure("CONFIRMATION_REQUIRED");
  }

  public void audit(String operation, String status) {
    String id = UUID.randomUUID().toString();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", id);
    row.put("operation", operation);
    row.put("status", status);
    row.put("timestamp", System.currentTimeMillis());
    store.put("audit", id, row);
    store.trimAudit(500);
  }

  public static String digest(String s) {
    return digest(s.getBytes(StandardCharsets.UTF_8));
  }

  public static String digest(byte[] bytes) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder b = new StringBuilder();
      for (byte v : hash) b.append(String.format("%02x", v & 255));
      return b.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
