package fr.jarvis.services.bridge;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import java.util.*;

/** Transactional deduplication/sequence receipt, never permission to execute an incoming tool. */
public final class BridgeInbox {
  private final Store store; private final Guard guard;
  public BridgeInbox(Store store, Guard guard) { this.store = store; this.guard = guard; }
  @SuppressWarnings("unchecked")
  public Map<String,Object> receive(Context host, BridgeMessage message) {
    guard.require(host, "bridge.lab");
    Map<String,Object>[] result = new Map[1];
    store.transaction(() -> {
      Map<String,Object> old = store.get("bridge_inbox", message.id);
      if (old != null) {
        if (!message.digest.equals(old.get("digest"))) throw new Failure("MESSAGE_CONFLICT");
        result[0] = old; return;
      }
      if (store.count("bridge_inbox") >= 1024) throw new Failure("INBOX_FULL");
      Map<String,Object> stream = store.get("bridge_inbox_vectors", message.streamId);
      long previous = stream == null ? 0 : Long.parseLong((String)stream.get("sequence"));
      if (message.sequence != previous + 1) throw new Failure("SEQUENCE_GAP");
      if (stream != null) for (Map.Entry<String,Object> e : ((Map<String,Object>)stream.get("vector")).entrySet()) {
        long prior = ((Number)e.getValue()).longValue();
        if (!message.vector.containsKey(e.getKey()) || message.vector.get(e.getKey()) < prior) throw new Failure("VECTOR_REGRESSION");
      }
      Map<String,Object> receipt = new TreeMap<>(); receipt.put("id", message.id); receipt.put("digest", message.digest);
      receipt.put("sequence", Long.toString(message.sequence)); receipt.put("state", "RECEIVED"); receipt.put("stream_id", message.streamId);
      store.put("bridge_inbox", message.id, receipt);
      Map<String,Object> next = new TreeMap<>(); next.put("sequence", Long.toString(message.sequence)); next.put("vector", message.vector);
      store.put("bridge_inbox_vectors", message.streamId, next); result[0] = receipt;
    });
    return Collections.unmodifiableMap(result[0]);
  }
}
