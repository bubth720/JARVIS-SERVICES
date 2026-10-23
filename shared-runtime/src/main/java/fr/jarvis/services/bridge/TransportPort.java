package fr.jarvis.services.bridge;

import fr.jarvis.services.shared.api.Contracts.Failure;

/** Future authenticated transports require separate review; production default is disabled. */
public interface TransportPort {
  String exchange(BridgeMessage message);
  final class Disabled implements TransportPort {
    public String exchange(BridgeMessage message) { throw new Failure("TRANSPORT_DISABLED"); }
  }
}
