package fr.jarvis.services.prefusion;

import static org.junit.jupiter.api.Assertions.*;
import fr.jarvis.services.api.proposal.CommitAuthorization;
import fr.jarvis.services.api.proposal.ServicesWirePreflight;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Contract scenarios, NOT a durability, Android SAF or production CORE authority proof. */
class ReceiptContractBenchTest {
  final AtomicLong clock=new AtomicLong(100);
  final ReceiptContractBench.MemoryJournal journal=new ReceiptContractBench.MemoryJournal();
  CommitAuthorization.Preview preview(long generation) {
    return new CommitAuthorization.Preview("00000000-0000-4000-8000-000000000001",
        "00000000-0000-4000-8000-000000000002",generation,"export.csv",
        ReceiptContractBench.digest("source".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        ReceiptContractBench.digest(ReceiptContractBench.syntheticBytes()),"fixture://fresh/new.csv",1000);
  }
  ReceiptContractBench bench() { return new ReceiptContractBench(preview(0),clock::get,journal); }
  ReceiptContractBench.Observation valid() { return new ReceiptContractBench.Observation(true,true,true,ReceiptContractBench.syntheticBytes()); }

  @Test void deterministicClockExpiresWithoutEffectOrSleep() throws Exception {
    ReceiptContractBench b=bench();ReceiptContractBench.Token token=b.confirm();b.dispatch();clock.set(1000);
    assertThrows(SecurityException.class,()->b.authorizeAtCommit(preview(0),token,()->fail("effect")));
    assertEquals(ReceiptContractBench.State.REFUSED,b.state());assertEquals(0,b.observedEffects);
  }
  @Test void cancellationBeforeCommitHasNoEffect() {
    ReceiptContractBench b=bench();ReceiptContractBench.Token token=b.confirm();b.dispatch();b.cancel();
    assertThrows(SecurityException.class,()->b.authorizeAtCommit(preview(0),token,()->fail("effect")));
    assertEquals(ReceiptContractBench.State.REFUSED,b.state());
  }
  @Test void revocationBeforeCommitHasNoEffect() {
    ReceiptContractBench b=bench();ReceiptContractBench.Token token=b.confirm();b.dispatch();b.revoke();
    assertThrows(SecurityException.class,()->b.authorizeAtCommit(preview(0),token,()->fail("effect")));
    assertEquals(0,b.observedEffects);
  }
  @Test void revocationAfterDecisionDoesNotUndoObservedEffect() throws Exception {
    ReceiptContractBench b=bench();ReceiptContractBench.Token token=b.confirm();b.dispatch();
    b.authorizeAtCommit(preview(0),token,()->{ b.revoke();return null; });
    assertTrue(b.revokedAfterCommit());assertEquals(ReceiptContractBench.State.COMMITTED,b.complete(valid()));
    assertEquals(1,b.observedEffects);
  }
  @Test void exactConsentCannotBeBorrowedAcrossFixtureAuthorities() {
    ReceiptContractBench a=bench();ReceiptContractBench.Token token=a.confirm();
    ReceiptContractBench b=new ReceiptContractBench(preview(0),clock::get,new ReceiptContractBench.MemoryJournal());b.dispatch();
    assertThrows(SecurityException.class,()->b.authorizeAtCommit(preview(0),token,()->fail("effect")));
  }
  @Test void duplicateCommandNeverInvokesEffectAgain() throws Exception {
    ReceiptContractBench b=bench();ReceiptContractBench.Token token=b.confirm();b.dispatch();
    b.authorizeAtCommit(preview(0),token,()->null);b.complete(valid());
    assertThrows(SecurityException.class,()->b.authorizeAtCommit(preview(0),token,()->fail("duplicate")));
    assertEquals(1,b.observedEffects);
  }
  @Test void journalFailureBeforeEffectConsumesConsentAndStaysUnknown() {
    ReceiptContractBench b=bench();ReceiptContractBench.Token token=b.confirm();b.dispatch();journal.failNext=true;
    assertThrows(IllegalStateException.class,()->b.authorizeAtCommit(preview(0),token,()->fail("effect")));
    assertTrue(token.used);assertEquals(0,b.observedEffects);assertEquals(ReceiptContractBench.State.UNKNOWN,b.state());
    assertThrows(SecurityException.class,()->b.authorizeAtCommit(preview(0),token,()->fail("retry")));
  }
  @Test void lostAckAfterEffectIsUnknownAndNeverRetryable() throws Exception {
    ReceiptContractBench b=bench();ReceiptContractBench.Token token=b.confirm();b.dispatch();
    b.authorizeAtCommit(preview(0),token,()->null);
    assertEquals(ReceiptContractBench.State.UNKNOWN,b.complete(new ReceiptContractBench.Observation(true,false,true,ReceiptContractBench.syntheticBytes())));
    assertThrows(SecurityException.class,()->b.authorizeAtCommit(preview(0),token,()->fail("retry")));
  }
  @Test void callbackExceptionAfterDecisionNeverRestoresConsent() {
    ReceiptContractBench b=bench();ReceiptContractBench.Token token=b.confirm();b.dispatch();
    assertThrows(java.io.IOException.class,()->b.authorizeAtCommit(preview(0),token,()->{throw new java.io.IOException("SYNTHETIC_ACK_LOST");}));
    assertEquals(ReceiptContractBench.State.UNKNOWN,b.state());assertTrue(token.used);
  }
  @Test void fullReadbackAndCloseAreRequiredNotJustAFinalHashClaim() throws Exception {
    for(ReceiptContractBench.Observation bad:Arrays.asList(
        new ReceiptContractBench.Observation(false,true,true,ReceiptContractBench.syntheticBytes()),
        new ReceiptContractBench.Observation(true,true,false,ReceiptContractBench.syntheticBytes()),
        new ReceiptContractBench.Observation(true,true,true,new byte[]{1}),
        new ReceiptContractBench.Observation(true,true,true,null))) {
      ReceiptContractBench b=new ReceiptContractBench(preview(0),clock::get,new ReceiptContractBench.MemoryJournal());
      ReceiptContractBench.Token token=b.confirm();b.dispatch();b.authorizeAtCommit(preview(0),token,()->null);
      assertEquals(ReceiptContractBench.State.UNKNOWN,b.complete(bad));
    }
  }
  @Test void abandonedDispatchAndCommitRecoverAsUnknownNotPrepared() throws Exception {
    for(boolean started:new boolean[]{false,true}) {
      ReceiptContractBench.MemoryJournal j=new ReceiptContractBench.MemoryJournal();
      ReceiptContractBench b=new ReceiptContractBench(preview(0),clock::get,j);ReceiptContractBench.Token token=b.confirm();b.dispatch();
      if(started)b.authorizeAtCommit(preview(0),token,()->null);
      ReceiptContractBench recovered=new ReceiptContractBench(preview(0),clock::get,j);
      assertEquals(ReceiptContractBench.State.UNKNOWN,recovered.state());
      assertThrows(SecurityException.class,recovered::confirm);
    }
  }
  @Test void corruptOrReorderedJournalIsNotReset() {
    for(List<ReceiptContractBench.Entry> corrupt:Arrays.asList(
        Arrays.asList(new ReceiptContractBench.Entry(2,ReceiptContractBench.State.PREPARED,preview(0))),
        Arrays.asList(new ReceiptContractBench.Entry(1,ReceiptContractBench.State.COMMITTED,preview(0))),
        Arrays.asList(new ReceiptContractBench.Entry(1,ReceiptContractBench.State.PREPARED,preview(0)),new ReceiptContractBench.Entry(2,ReceiptContractBench.State.UNKNOWN,preview(0)),new ReceiptContractBench.Entry(3,ReceiptContractBench.State.DISPATCHED,preview(0))))) {
      ReceiptContractBench.MemoryJournal j=new ReceiptContractBench.MemoryJournal();j.entries.addAll(corrupt);
      ReceiptContractBench b=new ReceiptContractBench(preview(0),clock::get,j);
      assertEquals(ReceiptContractBench.State.UNKNOWN,b.state());assertEquals(corrupt.size(),j.entries.size());
      assertThrows(SecurityException.class,b::confirm);
    }
  }
  @Test void journalForAnotherGenerationCannotBeAdopted() {
    ReceiptContractBench.MemoryJournal j=new ReceiptContractBench.MemoryJournal();
    j.entries.add(new ReceiptContractBench.Entry(1,ReceiptContractBench.State.PREPARED,preview(1)));
    ReceiptContractBench b=new ReceiptContractBench(preview(0),clock::get,j);
    assertEquals(ReceiptContractBench.State.UNKNOWN,b.state());assertEquals(1,j.entries.size());
    assertThrows(SecurityException.class,b::confirm);
  }
  @Test void generationZeroAndIntermediateLabelsRemainNonEquivalent() {
    assertEquals(ServicesWirePreflight.Verdict.INCOMPATIBLE_GENERATION,ServicesWirePreflight.review(preview(0),"PREPARED","PREPARED",false));
    assertEquals(ServicesWirePreflight.Verdict.NON_EQUIVALENT_STATE,ServicesWirePreflight.review(preview(1),"COMMIT_STARTED","DISPATCHED",false));
    assertEquals(ServicesWirePreflight.Verdict.NON_EQUIVALENT_STATE,ServicesWirePreflight.review(preview(1),"COMPLETED","COMMITTED",true));
    assertEquals(ServicesWirePreflight.Verdict.REVIEW_REQUIRED,ServicesWirePreflight.review(preview(1),"COMMITTED","COMMITTED",true));
  }
}
