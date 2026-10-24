package fr.jarvis.services.bridge;

import com.google.gson.Gson;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Authenticated file-exchange protocol only. No sockets, discovery, file opening or command
 * dispatch.
 */
public final class OfflineBridge implements AutoCloseable {
  public static final String VERSION = "services-offline-1";
  private static final Set<String> TYPES =
      new HashSet<>(Arrays.asList("document.reference", "diagnostic.ping"));
  private final Store store;
  private final Guard guard;
  private final String local, peer, session, scope;
  private final byte[] key;
  private boolean closed;
  private static final Gson JSON = new Gson();

  public OfflineBridge(
      Store store, Guard guard, String local, String peer, String session, byte[] provisionedKey) {
    if (local == null
        || peer == null
        || !local.matches("[a-z0-9_-]{1,40}")
        || !peer.matches("[a-z0-9_-]{1,40}")
        || local.equals(peer)
        || provisionedKey == null
        || provisionedKey.length < 32
        || provisionedKey.length > 64) throw new Failure("INVALID_PAIRING");
    uuid(session);
    this.store = store;
    this.guard = guard;
    this.local = local;
    this.peer = peer;
    this.session = session;
    key = provisionedKey.clone();
    scope = local + ":" + peer + ":" + session;
    store.transaction(
        () -> {
          Map<String, Object> old = store.get("offline_pairings", scope);
          String digest = Guard.digest(key);
          if (old != null && !digest.equals(old.get("key_digest")))
            throw new Failure("PAIRING_KEY_CHANGED");
          if (old == null) {
            if (store.count("offline_pairings") >= 16) throw new Failure("PAIRING_LIMIT");
            Map<String, Object> p = new TreeMap<>();
            p.put("key_digest", digest);
            p.put("send", "0");
            p.put("receive", "0");
            store.put("offline_pairings", scope, p);
          }
        });
  }

  public String queue(Context host, String type, String opaqueReference) {
    permit(host);
    allowed(type);
    uuid(opaqueReference);
    String id = UUID.randomUUID().toString();
    store.transaction(
        () -> {
          if (store.count("offline_outbox") >= 1024) throw new Failure("OUTBOX_FULL");
          Map<String, Object> pair = store.get("offline_pairings", scope);
          long sequence = Long.parseLong((String) pair.get("send")) + 1;
          if (sequence > 9007199254740991L) throw new Failure("SEQUENCE_LIMIT");
          Map<String, Object> body = new TreeMap<>();
          body.put("protocol", VERSION);
          body.put("kind", "DATA");
          body.put("session", session);
          body.put("from", local);
          body.put("to", peer);
          body.put("id", id);
          body.put("sequence", Long.toString(sequence));
          body.put("type", type);
          body.put("reference", opaqueReference);
          String frame = sign(body);
          Map<String, Object> row = new TreeMap<>();
          row.put("scope", scope);
          row.put("frame", frame);
          row.put("digest", Guard.digest(frame));
          row.put("state", "PENDING");
          store.put("offline_outbox", id, row);
          pair.put("send", Long.toString(sequence));
          store.put("offline_pairings", scope, pair);
        });
    guard.audit("bridge.file.queue", "queued");
    return id;
  }

  /**
   * Explicit host request to copy a frame to a voluntarily selected file; never an automatic retry.
   */
  public String frame(Context host, String id) {
    permit(host);
    Map<String, Object> row = outgoing(id);
    if ("ACKED".equals(row.get("state"))) throw new Failure("ALREADY_ACKED");
    if ("PENDING".equals(row.get("state"))) {
      row.put("state", "EXPORTED");
      store.put("offline_outbox", id, row);
    }
    return (String) row.get("frame");
  }

  public void interrupted(Context host, String id) {
    permit(host);
    Map<String, Object> row = outgoing(id);
    if (!"ACKED".equals(row.get("state"))) {
      row.put("state", "UNKNOWN");
      store.put("offline_outbox", id, row);
    }
  }

  public String state(Context host, String id) {
    permit(host);
    return (String) outgoing(id).get("state");
  }

  public String receive(Context host, String frame) {
    permit(host);
    Map<String, Object> m =
        verify(
            frame,
            new HashSet<>(
                Arrays.asList(
                    "protocol",
                    "kind",
                    "session",
                    "from",
                    "to",
                    "id",
                    "sequence",
                    "type",
                    "reference",
                    "mac")));
    if (!"DATA".equals(m.get("kind"))) throw new Failure("INVALID_MESSAGE");
    allowed((String) m.get("type"));
    uuid((String) m.get("reference"));
    String id = (String) m.get("id");
    uuid(id);
    String number = (String) m.get("sequence");
    if (!number.matches("[1-9][0-9]{0,15}")) throw new Failure("INVALID_NUMBER");
    long sequence = Long.parseLong(number);
    String[] ack = {null};
    store.transaction(
        () -> {
          Map<String, Object> old = store.get("offline_inbox", scope + ":" + id);
          String digest = Guard.digest(frame);
          if (old != null) {
            if (!digest.equals(old.get("digest"))) throw new Failure("MESSAGE_CONFLICT");
            ack[0] = (String) old.get("ack");
            return;
          }
          if (store.count("offline_inbox") >= 1024) throw new Failure("INBOX_FULL");
          Map<String, Object> pair = store.get("offline_pairings", scope);
          if (sequence != Long.parseLong((String) pair.get("receive")) + 1)
            throw new Failure("REPLAY_OR_SEQUENCE_GAP");
          Map<String, Object> receipt = new TreeMap<>();
          receipt.put("protocol", VERSION);
          receipt.put("kind", "ACK");
          receipt.put("session", session);
          receipt.put("from", local);
          receipt.put("to", peer);
          receipt.put("id", id);
          receipt.put("digest", digest);
          ack[0] = sign(receipt);
          Map<String, Object> row = new TreeMap<>();
          row.put("digest", digest);
          row.put("ack", ack[0]);
          row.put("state", "RECEIVED_NO_EXECUTION");
          store.put("offline_inbox", scope + ":" + id, row);
          pair.put("receive", Long.toString(sequence));
          store.put("offline_pairings", scope, pair);
        });
    guard.audit("bridge.file.receive", "received_no_execution");
    return ack[0];
  }

  public void acknowledge(Context host, String frame) {
    permit(host);
    Map<String, Object> ack =
        verify(
            frame,
            new HashSet<>(
                Arrays.asList("protocol", "kind", "session", "from", "to", "id", "digest", "mac")));
    if (!"ACK".equals(ack.get("kind"))) throw new Failure("INVALID_ACK");
    String id = (String) ack.get("id");
    store.transaction(
        () -> {
          Map<String, Object> row = outgoing(id);
          if (!row.get("digest").equals(ack.get("digest"))) throw new Failure("INVALID_ACK");
          row.put("state", "ACKED");
          store.put("offline_outbox", id, row);
        });
  }

  private Map<String, Object> outgoing(String id) {
    uuid(id);
    Map<String, Object> row = store.get("offline_outbox", id);
    if (row == null || !scope.equals(row.get("scope"))) throw new Failure("NOT_FOUND");
    return row;
  }

  private Map<String, Object> verify(String frame, Set<String> fields) {
    try {
      Map<String, Object> m = LabJson.parse(frame);
      if (!m.keySet().equals(fields)) throw new Failure("INVALID_MESSAGE");
      for (Object v : m.values()) if (!(v instanceof String)) throw new Failure("INVALID_MESSAGE");
      String supplied = (String) m.remove("mac");
      String expected = mac(JSON.toJson(new TreeMap<>(m)));
      if (supplied == null
          || !supplied.matches("[0-9a-f]{64}")
          || !MessageDigest.isEqual(
              supplied.getBytes(StandardCharsets.US_ASCII),
              expected.getBytes(StandardCharsets.US_ASCII)))
        throw new Failure("AUTHENTICATION_FAILED");
      if (!VERSION.equals(m.get("protocol"))
          || !session.equals(m.get("session"))
          || !peer.equals(m.get("from"))
          || !local.equals(m.get("to"))) throw new Failure("WRONG_PEER_OR_SESSION");
      return m;
    } catch (Failure e) {
      guard.audit("bridge.file.receive", "refused");
      throw e;
    } catch (RuntimeException e) {
      guard.audit("bridge.file.receive", "refused");
      throw new Failure("INVALID_MESSAGE");
    }
  }

  private void allowed(String type) {
    if (!TYPES.contains(type)) {
      guard.audit("bridge.file.command", "refused");
      throw new Failure("COMMAND_FORBIDDEN");
    }
  }

  private String sign(Map<String, Object> body) {
    Map<String, Object> m = new TreeMap<>(body);
    m.put("mac", mac(JSON.toJson(m)));
    return JSON.toJson(m);
  }

  private String mac(String body) {
    if (closed) throw new Failure("PAIRING_CLOSED");
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      byte[] digest = mac.doFinal((VERSION + "\n" + body).getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder();
      for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 255));
      return out.toString();
    } catch (Exception e) {
      throw new Failure("AUTHENTICATION_UNAVAILABLE");
    }
  }

  private void permit(Context host) {
    if (closed) throw new Failure("PAIRING_CLOSED");
    guard.require(host, "bridge.file");
  }

  private static void uuid(String id) {
    try {
      if (!UUID.fromString(id).toString().equals(id)) throw new Exception();
    } catch (Exception e) {
      throw new Failure("INVALID_REFERENCE");
    }
  }

  public void close() {
    Arrays.fill(key, (byte) 0);
    closed = true;
  }
}
