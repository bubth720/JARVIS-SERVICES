package fr.jarvis.services.bridge;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import java.util.*;

/** Metadata-only durable delivery state; lost ACK remains UNKNOWN and retry is explicit. */
public final class BridgeOutbox {
  private final Store store; private final Guard guard; private final BridgeMessage.EnvelopeValidator validator;
  public BridgeOutbox(Store store, Guard guard, BridgeMessage.EnvelopeValidator validator) { this.store = store; this.guard = guard; this.validator = validator; }
  public BridgeMessage enqueue(Context host, String stream, Map<String,Long> vector, String requestJson) {
    guard.require(host, "bridge.lab"); BridgeMessage[] out = new BridgeMessage[1];
    store.transaction(() -> {
      if (store.count("bridge_outbox") >= 1024) throw new Failure("OUTBOX_FULL");
      Map<String,Object> state = store.get("bridge_outbox_vectors", stream);
      long sequence = state == null ? 1 : Long.parseLong((String)state.get("sequence")) + 1;
      BridgeMessage message = new BridgeMessage(UUID.randomUUID().toString(), stream, sequence, vector, requestJson, validator);
      Map<String,Object> row = new TreeMap<>(); row.put("id", message.id); row.put("body", message.toJson()); row.put("state", "PENDING");
      store.put("bridge_outbox", message.id, row);
      store.put("bridge_outbox_vectors", stream, Collections.singletonMap("sequence", Long.toString(sequence))); out[0] = message;
    }); return out[0];
  }
  public Map<String,Object> send(Context host, String messageId, TransportPort transport) {
    guard.require(host, "bridge.lab");
    Map<String,Object> row = store.get("bridge_outbox", messageId); if (row == null) throw new Failure("NOT_FOUND");
    if ("ACKED".equals(row.get("state"))) return Collections.singletonMap("state", "ACKED");
    BridgeMessage message = BridgeMessage.parse((String)row.get("body"), validator);
    row.put("state", "SENDING"); store.put("bridge_outbox", messageId, row);
    try {
      String reply = transport.exchange(message);
      com.google.gson.JsonObject ack = new com.google.gson.Gson().toJsonTree(LabJson.parse(reply)).getAsJsonObject();
      if (!ack.keySet().equals(new HashSet<>(Arrays.asList("id", "digest", "sequence", "state", "stream_id")))
          || !message.id.equals(ack.get("id").getAsString()) || !message.digest.equals(ack.get("digest").getAsString())
          || !Long.toString(message.sequence).equals(ack.get("sequence").getAsString())
          || !message.streamId.equals(ack.get("stream_id").getAsString()) || !"RECEIVED".equals(ack.get("state").getAsString())) throw new Failure("INVALID_ACK");
      row.put("state", "ACKED"); store.put("bridge_outbox", messageId, row);
      return Collections.singletonMap("state", "ACKED");
    } catch (RuntimeException e) {
      row.put("state", "UNKNOWN"); store.put("bridge_outbox", messageId, row); throw e;
    }
  }
}
