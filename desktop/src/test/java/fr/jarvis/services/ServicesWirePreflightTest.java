package fr.jarvis.services;

import static org.junit.jupiter.api.Assertions.*;
import fr.jarvis.services.api.proposal.CommitAuthorization;
import fr.jarvis.services.api.proposal.ServicesWirePreflight;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServicesWirePreflightTest {
  static CommitAuthorization.Preview preview(long generation) {
    return new CommitAuthorization.Preview(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
        generation, "export.csv", "a".repeat(64), "b".repeat(64), "new-file",
        System.nanoTime()+1000000000L);
  }
  @Test void localGenerationZeroCannotBePromoted() {
    assertEquals(ServicesWirePreflight.Verdict.INCOMPATIBLE_GENERATION,
        ServicesWirePreflight.review(preview(0), "PREPARED", "PREPARED", false));
  }
  @Test void hugeJavaGenerationCannotBePromoted() {
    assertEquals(ServicesWirePreflight.Verdict.INCOMPATIBLE_GENERATION,
        ServicesWirePreflight.review(preview(Long.MAX_VALUE), "PREPARED", "PREPARED", false));
  }
  @Test void validBoundaryIsStillNotAuthorization() {
    assertEquals(ServicesWirePreflight.Verdict.REVIEW_REQUIRED,
        ServicesWirePreflight.review(preview(ServicesWirePreflight.MAX_WIRE_GENERATION), "COMMITTED", "COMMITTED", true));
  }
  @Test void commitStartedIsNotDispatched() {
    assertEquals(ServicesWirePreflight.Verdict.NON_EQUIVALENT_STATE,
        ServicesWirePreflight.review(preview(1), "COMMIT_STARTED", "DISPATCHED", false));
  }
  @Test void oldCompletedIsNotCommitted() {
    assertEquals(ServicesWirePreflight.Verdict.NON_EQUIVALENT_STATE,
        ServicesWirePreflight.review(preview(1), "COMPLETED", "COMMITTED", true));
  }
  @Test void refusalAndUnknownAreNotSilentlyCast() {
    assertEquals(ServicesWirePreflight.Verdict.NON_EQUIVALENT_STATE,
        ServicesWirePreflight.review(preview(1), "REFUSED", "UNKNOWN", false));
    assertEquals(ServicesWirePreflight.Verdict.REVIEW_REQUIRED,
        ServicesWirePreflight.review(preview(1), "UNKNOWN", "UNKNOWN", false));
  }
  @Test void nullNeverActivatesRuntime() {
    assertEquals(ServicesWirePreflight.Verdict.REVIEW_REQUIRED,
        ServicesWirePreflight.review(null, null, null, true));
  }
}
