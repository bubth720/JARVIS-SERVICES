package fr.jarvis.services.prefusion;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * LOT4: synthetically models independent CORE and SERVICES journals.
 * This is test-only code: no SAF provider, SQLite, real CORE identity or permission.
 */
class TwoJournalLot4Test {
  enum Owner { CORE, SERVICES }
  enum Step { PREPARED, CONSENT_CONSUMED, REVOKED, DISPATCHED, COMMIT_STARTED, COMMITTED, UNKNOWN }
  record Entry(Owner owner, long sequence, Step step, String epoch, long generation) {}
  static final class Journal {
    final Owner owner;
    final ArrayList<Entry> events = new ArrayList<>();
    boolean failNext;
    Journal(Owner owner) { this.owner=owner; }
    void append(Step step, String epoch, long generation) {
      if (failNext) { failNext=false;throw new IllegalStateException("SYNTHETIC_JOURNAL_LOST"); }
      events.add(new Entry(owner, events.size()+1L, step, epoch, generation));
    }
  }
  enum Inspection { EVIDENCE_MATCH, INCONCLUSIVE }
  record Readback(String destination, String digest, boolean closed,
                  boolean ack, boolean sourceUnchanged) {}
  static final class Lab {
    final String epoch = "00000000-0000-4000-8000-000000000001";
    final String destination = "fixture://new/result.csv";
    final String digest = "a".repeat(64);
    final long generation = 1L;
    final Journal core=new Journal(Owner.CORE);
    final Journal worker=new Journal(Owner.SERVICES);
    boolean prepared, dispatched, consumed, started, revoked, unknown, committed;
    int syntheticEffects;

    void prepare() {
      if(prepared)throw new SecurityException("REPLAY");
      core.append(Step.PREPARED,epoch,generation);
      prepared=true;
    }
    void dispatch() {
      if(!prepared || dispatched || revoked || unknown)throw new SecurityException("NO_DISPATCH");
      worker.append(Step.DISPATCHED,epoch,generation);
      dispatched=true;
    }
    void consume() {
      if(!prepared || !dispatched || consumed || revoked || unknown)
        throw new SecurityException("CORE_REFUSED");
      try {
        core.append(Step.CONSENT_CONSUMED,epoch,generation);
      } catch(RuntimeException ex) { unknown=true;throw ex; }
      consumed=true;
    }
    void revoke() {
      core.append(Step.REVOKED,epoch,generation);
      revoked=true;
    }
    void start() {
      if(!consumed || !dispatched || started || unknown || revoked)
        throw new SecurityException("NO_CORE_AUTHORITY");
      try {
        worker.append(Step.COMMIT_STARTED,epoch,generation);
      } catch(RuntimeException ex) { unknown=true;throw ex; }
      started=true;
    }
    void effect() {
      // No actual effect; this counter is the ONLY synthetic mock side effect.
      if(!started || unknown || syntheticEffects>0)throw new SecurityException("NO_RETRY");
      syntheticEffects++;
    }
    Inspection inspectUnknown(Readback rb) {
      // Inspection must not update either owner journal or mutate state.
      if(!unknown || rb==null || !rb.closed || !rb.ack || !rb.sourceUnchanged
          || !destination.equals(rb.destination) || !digest.equals(rb.digest))
        return Inspection.INCONCLUSIVE;
      return Inspection.EVIDENCE_MATCH;
    }
    void loseAck() {
      if(!started || unknown)throw new SecurityException("BAD_STATE");
      worker.append(Step.UNKNOWN,epoch,generation);
      unknown=true;
    }
    void finish(Readback rb) {
      if(!started || unknown || committed || syntheticEffects!=1)
        throw new SecurityException("NO_COMMIT");
      if(rb==null || !rb.closed || !rb.ack || !rb.sourceUnchanged
          || !destination.equals(rb.destination) || !digest.equals(rb.digest)) {
        worker.append(Step.UNKNOWN,epoch,generation);
        unknown=true;
        return;
      }
      worker.append(Step.COMMITTED,epoch,generation);
      committed=true;
    }
    Readback good() {return new Readback(destination,digest,true,true,true);}
  }
  @Test void successRemainsASyntheticLabOnly() {
    Lab l=new Lab();l.prepare();l.dispatch();l.consume();l.start();l.effect();l.finish(l.good());
    assertTrue(l.committed);assertEquals(1,l.syntheticEffects);
    assertEquals(2,l.core.events.size());assertEquals(3,l.worker.events.size());
    assertEquals(Step.COMMIT_STARTED,l.worker.events.get(1).step());
  }
  @Test void noFakeCoreConsent() {
    Lab l=new Lab();l.prepare();l.dispatch();
    assertThrows(SecurityException.class,l::start);assertEquals(0,l.syntheticEffects);
  }
  @Test void revocationBeforeCommitPreventsSyntheticEffect() {
    Lab l=new Lab();l.prepare();l.dispatch();l.consume();l.revoke();
    assertThrows(SecurityException.class,l::start);assertEquals(0,l.syntheticEffects);
  }
  @Test void repeatConsentRefused() {
    Lab l=new Lab();l.prepare();l.dispatch();l.consume();
    assertThrows(SecurityException.class,l::consume);
  }
  @Test void duplicateDispatchRefused() {
    Lab l=new Lab();l.prepare();l.dispatch();
    assertThrows(SecurityException.class,l::dispatch);
  }
  @Test void failedCoreJournalAppendIsUnknown() {
    Lab l=new Lab();l.prepare();l.dispatch();l.core.failNext=true;
    assertThrows(IllegalStateException.class,l::consume);
    assertTrue(l.unknown);assertThrows(SecurityException.class,l::start);
    assertEquals(0,l.syntheticEffects);
  }
  @Test void failedWorkerJournalBeforeEffectIsUnknown() {
    Lab l=new Lab();l.prepare();l.dispatch();l.consume();l.worker.failNext=true;
    assertThrows(IllegalStateException.class,l::start);
    assertTrue(l.unknown);assertEquals(0,l.syntheticEffects);
  }
  @Test void lostAckNoRetryOrPromote() {
    Lab l=new Lab();l.prepare();l.dispatch();l.consume();l.start();l.effect();l.loseAck();
    int before=l.core.events.size(), after=l.worker.events.size();
    assertEquals(Inspection.EVIDENCE_MATCH,l.inspectUnknown(l.good()));
    assertEquals(before,l.core.events.size());assertEquals(after,l.worker.events.size());
    assertTrue(l.unknown);assertFalse(l.committed);
    assertThrows(SecurityException.class,l::effect);
    assertThrows(SecurityException.class,()->l.finish(l.good()));
  }
  @Test void unknownWithFalseEvidenceStaysUnknown() {
    Lab l=new Lab();l.prepare();l.dispatch();l.consume();l.start();l.effect();l.loseAck();
    assertEquals(Inspection.INCONCLUSIVE,l.inspectUnknown(new Readback(l.destination,l.digest,false,true,true)));
    assertEquals(Inspection.INCONCLUSIVE,l.inspectUnknown(new Readback(l.destination,"b".repeat(64),true,true,true)));
    assertEquals(Inspection.INCONCLUSIVE,l.inspectUnknown(new Readback("fixture://other",l.digest,true,true,true)));
    assertEquals(Inspection.INCONCLUSIVE,l.inspectUnknown(new Readback(l.destination,l.digest,true,false,true)));
    assertEquals(Inspection.INCONCLUSIVE,l.inspectUnknown(new Readback(l.destination,l.digest,true,true,false)));
    assertTrue(l.unknown);assertFalse(l.committed);
  }
  @Test void badReadbackNeverBecomesCommitted() {
    Lab l=new Lab();l.prepare();l.dispatch();l.consume();l.start();l.effect();
    l.finish(new Readback(l.destination,l.digest,true,false,true));
    assertTrue(l.unknown);assertFalse(l.committed);assertEquals(1,l.syntheticEffects);
  }
  @Test void cannotMarkCommittedFromDispatched() {
    Lab l=new Lab();l.prepare();l.dispatch();
    assertThrows(SecurityException.class,()->l.finish(l.good()));
  }
  @Test void readOnlyInspectionBeforeUnknownIsInconclusive() {
    Lab l=new Lab();l.prepare();
    assertEquals(Inspection.INCONCLUSIVE,l.inspectUnknown(l.good()));
  }
  @Test void appendFailureCannotResetOldOwnerJournal() {
    Lab l=new Lab();l.prepare();l.dispatch();l.core.failNext=true;
    assertThrows(IllegalStateException.class,l::consume);
    assertEquals(Step.PREPARED,l.core.events.get(0).step());
    assertEquals(Step.DISPATCHED,l.worker.events.get(0).step());
    assertTrue(l.unknown);
  }
}
