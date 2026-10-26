package fr.jarvis.services.api.proposal;

/**
 * Review-only adapter preflight for UNI-08/U01. Never issues CORE consent, translates
 * durable receipt states, opens a provider, or changes ServicesToolPort/1.
 * All outcomes prohibit activating a production write.
 */
public final class ServicesWirePreflight {
  private ServicesWirePreflight() {}
  public static final long MAX_WIRE_GENERATION = 9007199254740991L;
  public enum Verdict { INCOMPATIBLE_GENERATION, NON_EQUIVALENT_STATE, REVIEW_REQUIRED }

  /** Local proposal accepts generation 0; proposed UNIFIED wire/2 does not. */
  public static Verdict review(CommitAuthorization.Preview preview, String localState,
                               String proposedWireState, boolean fullyReadBack) {
    if (preview == null || localState == null || proposedWireState == null)
      return Verdict.REVIEW_REQUIRED;
    if (preview.generation < 1 || preview.generation > MAX_WIRE_GENERATION)
      return Verdict.INCOMPATIBLE_GENERATION;
    // No string cast between separate durability and state machines. A receipt
    // with the same label is still untrusted until CORE attests the decision.
    if ("COMMIT_STARTED".equals(localState) || "REFUSED".equals(localState)
        || "COMPLETED".equals(localState) || "DISPATCHED".equals(localState)
        || "COMMIT_STARTED".equals(proposedWireState)
        || "REFUSED".equals(proposedWireState) || "COMPLETED".equals(proposedWireState)
        || !localState.equals(proposedWireState))
      return Verdict.NON_EQUIVALENT_STATE;
    // Even readback in synthetic SAF fixtures does not prove real CORE
    // consent/UID/revocation or translate a local receipt to UNIFIED wire/2.
    return Verdict.REVIEW_REQUIRED;
  }
}
