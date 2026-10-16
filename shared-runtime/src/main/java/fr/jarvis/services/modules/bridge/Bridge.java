package fr.jarvis.services.modules.bridge;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.util.*;

public final class Bridge {
  public interface Transport {
    Set<String> discover();

    Set<String> capabilities(String peer);

    Message exchange(Message message, Task task);

    void disconnect(String peer);
  }

  public static final class Message {
    public final String schema_version = Contracts.VERSION, request_id, peer, type, payload;

    public Message(String id, String peer, String type, String payload) {
      try {
        UUID.fromString(id);
      } catch (Exception e) {
        throw new Failure("INVALID_REQUEST_ID");
      }
      if (peer == null || type == null || payload == null || payload.length() > 4096)
        throw new Failure("INVALID_INPUT");
      this.request_id = id;
      this.peer = peer;
      this.type = type;
      this.payload = payload;
    }
  }

  public static final class Simulator implements Transport {
    private boolean connected = true;

    public Set<String> discover() {
      return connected ? Collections.singleton("simulator-local") : Collections.emptySet();
    }

    public Set<String> capabilities(String peer) {
      if (!discover().contains(peer)) throw new Failure("DISCONNECTED");
      return Collections.singleton("echo");
    }

    public Message exchange(Message m, Task task) {
      task.check();
      if (!capabilities(m.peer).contains(m.type)) throw new Failure("UNSUPPORTED_CAPABILITY");
      return new Message(m.request_id, m.peer, m.type, m.payload);
    }

    public void disconnect(String peer) {
      connected = false;
    }
  }

  private final Guard guard;
  private final Transport transport;

  public Bridge(Guard guard, Transport transport) {
    this.guard = guard;
    this.transport = transport;
  }

  public Set<String> discover(Context ctx) {
    guard.require(ctx, "bridge.discover");
    return transport.discover();
  }

  public Set<String> capabilities(Context ctx, String peer) {
    guard.require(ctx, "bridge.peer:" + peer);
    return transport.capabilities(peer);
  }

  public Message send(Context ctx, Message m, Task task) {
    guard.require(ctx, "bridge.peer:" + m.peer);
    if (!Contracts.VERSION.equals(m.schema_version)) throw new Failure("INCOMPATIBLE");
    Message result = transport.exchange(m, task);
    task.check();
    if (result == null
        || !m.request_id.equals(result.request_id)
        || !m.peer.equals(result.peer)
        || !Contracts.VERSION.equals(result.schema_version)) throw new Failure("STALE_RESPONSE");
    return result;
  }

  public void disconnect(Context ctx, String peer) {
    guard.require(ctx, "bridge.peer:" + peer);
    transport.disconnect(peer);
  }
}
