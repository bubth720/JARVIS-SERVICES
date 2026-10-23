package fr.jarvis.services.bridge;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import java.util.*;

/** Lab metadata only: canonical UNIFIED request contains opaque references, no document content. */
public final class BridgeMessage {
  public interface EnvelopeValidator { String canonical(String requestJson); }
  public final String id, streamId, requestJson, digest; public final long sequence;
  public final Map<String,Long> vector;
  public BridgeMessage(String id, String streamId, long sequence, Map<String,Long> vector, String requestJson, EnvelopeValidator validator) {
    try { if (!UUID.fromString(id).toString().equals(id) || !UUID.fromString(streamId).toString().equals(streamId)) throw new Exception(); }
    catch (Exception e) { throw new Failure("INVALID_MESSAGE_ID"); }
    if (sequence < 1 || sequence > 9007199254740991L || vector == null || vector.isEmpty() || vector.size() > 16
        || requestJson == null || requestJson.length() > 32768 || validator == null) throw new Failure("INVALID_MESSAGE");
    Map<String,Long> copy = new TreeMap<>();
    for (Map.Entry<String,Long> e : vector.entrySet()) {
      if (e.getKey() == null || !e.getKey().matches("[a-z0-9_-]{1,40}") || e.getValue() == null
          || e.getValue() < 0 || e.getValue() > 9007199254740991L) throw new Failure("INVALID_VECTOR");
      copy.put(e.getKey(), e.getValue());
    }
    this.id = id; this.streamId = streamId; this.sequence = sequence; this.vector = Collections.unmodifiableMap(copy);
    try { this.requestJson = validator.canonical(requestJson); }
    catch (RuntimeException e) { throw new Failure("INVALID_CONTRACT"); }
    this.digest = Guard.digest(new com.google.gson.Gson().toJson(body()));
  }
  public Map<String,Object> body() {
    Map<String,Object> body = new TreeMap<>(); body.put("id", id); body.put("stream_id", streamId);
    body.put("sequence", sequence); body.put("vector", vector); body.put("request_json", requestJson); return body;
  }
  public String toJson() { Map<String,Object> row = body(); row.put("digest", digest); return new com.google.gson.Gson().toJson(row); }
  public static BridgeMessage parse(String json, EnvelopeValidator validator) {
    try {
      if (json == null || json.length() > 65536) throw new Failure("INPUT_TOO_LARGE");
      com.google.gson.JsonObject obj = new com.google.gson.Gson().toJsonTree(LabJson.parse(json)).getAsJsonObject();
      if (!obj.keySet().equals(new HashSet<>(Arrays.asList("id", "stream_id", "sequence", "vector", "request_json", "digest")))) throw new Failure("INVALID_MESSAGE");
      Map<String,Long> vector = new TreeMap<>();
      for (Map.Entry<String,com.google.gson.JsonElement> e : obj.getAsJsonObject("vector").entrySet()) vector.put(e.getKey(), exactLong(e.getValue()));
      BridgeMessage message = new BridgeMessage(obj.get("id").getAsString(), obj.get("stream_id").getAsString(), exactLong(obj.get("sequence")), vector, obj.get("request_json").getAsString(), validator);
      if (!message.digest.equals(obj.get("digest").getAsString())) throw new Failure("INTEGRITY_FAILED");
      return message;
    } catch (Failure e) { throw e; }
    catch (RuntimeException e) { throw new Failure("INVALID_MESSAGE"); }
  }
  private static long exactLong(com.google.gson.JsonElement value) {
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.toString().matches("0|[1-9][0-9]{0,15}")) throw new Failure("INVALID_NUMBER");
    return Long.parseLong(value.toString());
  }
}
