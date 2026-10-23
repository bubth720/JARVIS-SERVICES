package fr.jarvis.services.bridge;

import org.jarvis.contracts.v1.RequestEnvelope;

/** Exact vendored UNI-01 JVM codec. Declared authorization_ref is not an authenticated identity. */
public final class UnifiedLabValidator implements BridgeMessage.EnvelopeValidator {
  public String canonical(String requestJson) {
    RequestEnvelope request = RequestEnvelope.parse(requestJson);
    if (request.getDestination() != RequestEnvelope.DestinationValue.SERVICES
        || request.getCapability() != RequestEnvelope.CapabilityValue.TOOL_INVOKE) throw new IllegalArgumentException("Unsupported lab request");
    return request.toJson();
  }
}
