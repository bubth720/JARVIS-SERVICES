package fr.jarvis.services;

import static org.junit.jupiter.api.Assertions.*;

import fr.jarvis.services.api.proposal.CommitAuthorization;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class CommitAuthorizationTest {
  // Fixture authority only: no CORE policy is implemented by SERVICES production code.
  static class Authority implements CommitAuthorization {
    final Object lock = new Object();
    long generation;
    boolean cancelled;
    final Set<String> started = new HashSet<>();

    static class Token implements ConsentToken {
      final Preview p;
      boolean used;

      Token(Preview p) {
        this.p = p;
      }
    }

    Token confirm(Preview p) {
      return new Token(p);
    }

    void revoke() {
      synchronized (lock) {
        generation++;
      }
    }

    void cancel() {
      synchronized (lock) {
        cancelled = true;
      }
    }

    public <T> T authorizeAtCommit(Preview p, ConsentToken consent, Callable<T> effect)
        throws Exception {
      synchronized (lock) {
        if (!(consent instanceof Token)) throw new SecurityException("NO_CONSENT");
        Token t = (Token) consent;
        if (t.used
            || !t.p.equals(p)
            || generation != p.generation
            || cancelled
            || System.nanoTime() >= p.deadlineNanos
            || started.contains(p.request)) throw new SecurityException("REFUSED");
        t.used = true;
        started.add(p.request); // decision linearization point
      }
      return effect.call(); // no authority lock across provider IO
    }
  }

  static CommitAuthorization.Preview preview(long generation, String dest) {
    return new CommitAuthorization.Preview(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        generation,
        "export.csv",
        String.join("", Collections.nCopies(64, "a")),
        String.join("", Collections.nCopies(64, "b")),
        dest,
        System.nanoTime() + TimeUnit.SECONDS.toNanos(10));
  }

  @Test
  void revocationBeforeCommitPreventsEffect() throws Exception {
    Authority a = new Authority();
    CommitAuthorization.Preview p = preview(0, "fresh");
    Authority.Token t = a.confirm(p);
    CountDownLatch ready = new CountDownLatch(1), go = new CountDownLatch(1);
    AtomicInteger effects = new AtomicInteger();
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<Boolean> f =
          pool.submit(
              () -> {
                ready.countDown();
                assertTrue(go.await(5, TimeUnit.SECONDS));
                try {
                  a.authorizeAtCommit(p, t, effects::incrementAndGet);
                  return false;
                } catch (SecurityException e) {
                  return true;
                }
              });
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      a.revoke();
      go.countDown();
      assertTrue(f.get(5, TimeUnit.SECONDS));
      assertEquals(0, effects.get());
    } finally {
      go.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void revocationAfterCommitCannotPromiseRollback() throws Exception {
    Authority a = new Authority();
    CommitAuthorization.Preview p = preview(0, "fresh");
    Authority.Token t = a.confirm(p);
    CountDownLatch committed = new CountDownLatch(1), go = new CountDownLatch(1);
    AtomicInteger effects = new AtomicInteger();
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<Integer> f =
          pool.submit(
              () ->
                  a.authorizeAtCommit(
                      p,
                      t,
                      () -> {
                        committed.countDown();
                        assertTrue(go.await(5, TimeUnit.SECONDS));
                        return effects.incrementAndGet();
                      }));
      assertTrue(committed.await(5, TimeUnit.SECONDS));
      a.revoke();
      go.countDown();
      assertEquals(1, f.get(5, TimeUnit.SECONDS));
      assertThrows(
          SecurityException.class, () -> a.authorizeAtCommit(p, t, effects::incrementAndGet));
      assertEquals(1, effects.get());
    } finally {
      go.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void alteredDestinationAndCancellationRefuse() {
    Authority a = new Authority();
    CommitAuthorization.Preview p = preview(0, "fresh");
    Authority.Token t = a.confirm(p);
    CommitAuthorization.Preview other =
        new CommitAuthorization.Preview(
            p.session,
            p.request,
            0,
            p.operation,
            p.sourceDigest,
            p.contentDigest,
            "other",
            p.deadlineNanos);
    assertThrows(
        SecurityException.class, () -> a.authorizeAtCommit(other, t, () -> fail("effect")));
    a.cancel();
    assertThrows(SecurityException.class, () -> a.authorizeAtCommit(p, t, () -> fail("effect")));
  }

  @Test
  void everyPreviewFieldIsBoundToExactConsent() {
    Authority a = new Authority();
    CommitAuthorization.Preview p = preview(0, "fresh");
    Authority.Token t = a.confirm(p);
    String alternate = String.join("", Collections.nCopies(64, "c"));
    List<CommitAuthorization.Preview> changed =
        Arrays.asList(
            new CommitAuthorization.Preview(
                UUID.randomUUID().toString(),
                p.request,
                0,
                p.operation,
                p.sourceDigest,
                p.contentDigest,
                p.destination,
                p.deadlineNanos),
            new CommitAuthorization.Preview(
                p.session,
                UUID.randomUUID().toString(),
                0,
                p.operation,
                p.sourceDigest,
                p.contentDigest,
                p.destination,
                p.deadlineNanos),
            new CommitAuthorization.Preview(
                p.session,
                p.request,
                1,
                p.operation,
                p.sourceDigest,
                p.contentDigest,
                p.destination,
                p.deadlineNanos),
            new CommitAuthorization.Preview(
                p.session,
                p.request,
                0,
                "export.docx",
                p.sourceDigest,
                p.contentDigest,
                p.destination,
                p.deadlineNanos),
            new CommitAuthorization.Preview(
                p.session,
                p.request,
                0,
                p.operation,
                alternate,
                p.contentDigest,
                p.destination,
                p.deadlineNanos),
            new CommitAuthorization.Preview(
                p.session,
                p.request,
                0,
                p.operation,
                p.sourceDigest,
                alternate,
                p.destination,
                p.deadlineNanos),
            new CommitAuthorization.Preview(
                p.session,
                p.request,
                0,
                p.operation,
                p.sourceDigest,
                p.contentDigest,
                p.destination,
                p.deadlineNanos + 1));
    for (CommitAuthorization.Preview other : changed)
      assertThrows(
          SecurityException.class, () -> a.authorizeAtCommit(other, t, () -> fail("effect")));
  }

  @Test
  void ambiguousCallbackFailureDoesNotRestoreConsent() throws Exception {
    Authority a = new Authority();
    CommitAuthorization.Preview p = preview(0, "fresh");
    Authority.Token t = a.confirm(p);
    AtomicInteger effects = new AtomicInteger();
    assertThrows(
        java.io.IOException.class,
        () ->
            a.authorizeAtCommit(
                p,
                t,
                () -> {
                  effects.incrementAndGet();
                  throw new java.io.IOException("ACK_LOST");
                }));
    assertThrows(
        SecurityException.class, () -> a.authorizeAtCommit(p, t, effects::incrementAndGet));
    assertEquals(1, effects.get());
  }

  @Test
  void missingConsentAndExpiredDeadlineRefuse() {
    Authority a = new Authority();
    CommitAuthorization.Preview p = preview(0, "fresh");
    assertThrows(SecurityException.class, () -> a.authorizeAtCommit(p, null, () -> fail("effect")));
    CommitAuthorization.Preview expired =
        new CommitAuthorization.Preview(
            p.session,
            p.request,
            0,
            p.operation,
            p.sourceDigest,
            p.contentDigest,
            p.destination,
            1);
    assertThrows(
        SecurityException.class,
        () -> a.authorizeAtCommit(expired, a.confirm(expired), () -> fail("effect")));
  }
}
