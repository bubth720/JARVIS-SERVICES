package fr.jarvis.services.api.proposal;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * FUSION-02 proposal/1, NOT part of ServicesToolPort/1 and never wired into its runtime. CORE owns
 * issuance, revocation, generation and the monotonic clock. A host serializes revoke and
 * authorizeAtCommit on the same lock. Authorization consumes one exact consent and establishes
 * COMMIT_STARTED before invoking effect, even if revoke follows immediately. After that point
 * revoke cannot promise rollback. No text/model-derived authority is valid.
 */
public interface CommitAuthorization {
  String VERSION = "jarvis.services.commit-proposal/1";

  final class Preview {
    public final String session, request, operation, sourceDigest, contentDigest, destination;
    public final long generation, deadlineNanos;

    public Preview(
        String session,
        String request,
        long generation,
        String operation,
        String sourceDigest,
        String contentDigest,
        String destination,
        long deadlineNanos) {
      this.session = UUID.fromString(session).toString();
      this.request = UUID.fromString(request).toString();
      if (generation < 0 || deadlineNanos <= 0 || !operation.matches("export\\.(csv|docx)"))
        throw new IllegalArgumentException("INVALID_PREVIEW");
      if (!sourceDigest.matches("[a-f0-9]{64}") || !contentDigest.matches("[a-f0-9]{64}"))
        throw new IllegalArgumentException("INVALID_DIGEST");
      if (destination == null || destination.isEmpty() || destination.length() > 4096)
        throw new IllegalArgumentException("INVALID_DESTINATION");
      this.generation = generation;
      this.operation = operation;
      this.sourceDigest = sourceDigest;
      this.contentDigest = contentDigest;
      this.destination = destination;
      this.deadlineNanos = deadlineNanos;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Preview)) return false;
      Preview p = (Preview) o;
      return generation == p.generation
          && deadlineNanos == p.deadlineNanos
          && session.equals(p.session)
          && request.equals(p.request)
          && operation.equals(p.operation)
          && sourceDigest.equals(p.sourceDigest)
          && contentDigest.equals(p.contentDigest)
          && destination.equals(p.destination);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          session,
          request,
          generation,
          operation,
          sourceDigest,
          contentDigest,
          destination,
          deadlineNanos);
    }
  }

  /** Opaque, non-serializable host capability; never passed through tool arguments. */
  interface ConsentToken {}

  /**
   * A production host must atomically validate exact preview, generation, expiry, sticky
   * cancellation, unique request and single-use consent before invoking this callback once. Throw
   * before invoking effect => REFUSED/no write. Throw after invoking => UNKNOWN. Must not block the
   * revocation lock while performing provider IO. Implementations must distinguish rejection from
   * ambiguous callback failure; laboratory fixture demonstrates it.
   */
  <T> T authorizeAtCommit(Preview preview, ConsentToken consent, Callable<T> effect)
      throws Exception;
}
