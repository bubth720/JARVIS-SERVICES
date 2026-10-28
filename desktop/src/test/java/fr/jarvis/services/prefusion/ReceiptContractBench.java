package fr.jarvis.services.prefusion;

import fr.jarvis.services.api.proposal.CommitAuthorization;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;

/** Deterministic synthetic contract fixture ONLY; excluded from all runtime artifacts. */
final class ReceiptContractBench implements CommitAuthorization {
  enum State { PREPARED, DISPATCHED, COMMIT_STARTED, COMMITTED, REFUSED, UNKNOWN }
  static final class Entry {
    final long sequence;
    final State state;
    final Preview binding;
    Entry(long sequence, State state, Preview binding) {
      this.sequence = sequence; this.state = state; this.binding = binding;
    }
  }
  interface Journal { void append(Entry entry); List<Entry> read(); }
  static final class MemoryJournal implements Journal {
    final List<Entry> entries = new ArrayList<>();
    boolean failNext;
    public void append(Entry entry) {
      if (failNext) { failNext = false; throw new IllegalStateException("SYNTHETIC_JOURNAL_FAILURE"); }
      entries.add(entry);
    }
    public List<Entry> read() { return new ArrayList<>(entries); }
  }
  static final class Observation {
    final boolean closeObserved, ackObserved, originalUnchanged;
    final byte[] readback;
    Observation(boolean closeObserved, boolean ackObserved, boolean originalUnchanged, byte[] bytes) {
      this.closeObserved=closeObserved; this.ackObserved=ackObserved;
      this.originalUnchanged=originalUnchanged;
      this.readback=bytes == null ? null : bytes.clone();
    }
  }
  static final class Token implements ConsentToken {
    final ReceiptContractBench owner;
    final Preview preview;
    boolean used;
    Token(ReceiptContractBench owner, Preview preview) { this.owner=owner; this.preview=preview; }
  }
  private final Object decision = new Object();
  private final LongSupplier clock;
  private final Journal journal;
  private final Preview expected;
  private State state;
  private long sequence, generation;
  private boolean cancelled, uncertain, revokedAfterCommit;
  int observedEffects;

  ReceiptContractBench(Preview preview, LongSupplier clock, Journal journal) {
    this.expected=Objects.requireNonNull(preview);this.generation=preview.generation;
    this.clock=Objects.requireNonNull(clock);this.journal=Objects.requireNonNull(journal);
    recover();
  }
  private void recover() {
    try {
      List<Entry> entries=journal.read();
      State previous=null; long last=0;
      for (Entry entry : entries) {
        if (entry==null || !expected.equals(entry.binding) || entry.sequence!=last+1
            || entry.state==null || !allowed(previous,entry.state))
          throw new IllegalStateException("CORRUPT_FIXTURE_JOURNAL");
        previous=entry.state;last=entry.sequence;
      }
      sequence=last;state=previous;
      if (state==null) record(State.PREPARED);
      else if (state==State.DISPATCHED || state==State.COMMIT_STARTED) record(State.UNKNOWN);
    } catch (RuntimeException invalid) { state=State.UNKNOWN;uncertain=true; }
  }
  private static boolean allowed(State before,State after) {
    if (before==null) return after==State.PREPARED;
    switch(before) {
      case PREPARED: return after==State.DISPATCHED || after==State.REFUSED || after==State.UNKNOWN;
      case DISPATCHED: return after==State.COMMIT_STARTED || after==State.REFUSED || after==State.UNKNOWN;
      case COMMIT_STARTED: return after==State.COMMITTED || after==State.UNKNOWN;
      default: return false;
    }
  }
  private void record(State next) {
    if (!allowed(state,next)) throw new IllegalStateException("INVALID_FIXTURE_TRANSITION");
    try { journal.append(new Entry(sequence+1,next,expected));sequence++;state=next; }
    catch (RuntimeException failure) { state=State.UNKNOWN;uncertain=true;throw failure; }
  }
  Token confirm() { synchronized(decision) {
    if (state!=State.PREPARED || uncertain) throw new SecurityException("REFUSED");
    return new Token(this,expected);
  } }
  void revoke() { synchronized(decision) { generation++; if(state==State.COMMIT_STARTED) revokedAfterCommit=true; } }
  void cancel() { synchronized(decision) { cancelled=true; } }
  State state() { synchronized(decision) { return state; } }
  boolean revokedAfterCommit() { synchronized(decision) { return revokedAfterCommit; } }
  void dispatch() { synchronized(decision) { record(State.DISPATCHED); } }

  @Override public <T> T authorizeAtCommit(Preview p,ConsentToken consent,Callable<T> effect) throws Exception {
    synchronized(decision) {
      if (state!=State.DISPATCHED || uncertain) throw new SecurityException("NO_RETRY");
      Token token=consent instanceof Token ? (Token)consent : null;
      if (token==null || token.owner!=this || token.used || !expected.equals(p) || !token.preview.equals(p)
          || generation!=p.generation || cancelled || clock.getAsLong()>=p.deadlineNanos) {
        record(State.REFUSED);throw new SecurityException("REFUSED_BEFORE_COMMIT");
      }
      // Single fixture lock linearizes decision/revocation; no provider IO while it is held.
      token.used=true;
      record(State.COMMIT_STARTED); // journal failure consumes consent and leaves UNKNOWN, never retries
    }
    try { observedEffects++; return effect.call(); }
    catch (Exception failure) {
      synchronized(decision) { if(state==State.COMMIT_STARTED) record(State.UNKNOWN); }
      throw failure;
    }
  }
  State complete(Observation observation) {
    synchronized(decision) {
      if(state!=State.COMMIT_STARTED) throw new SecurityException("NO_RETRY");
      boolean verified=observation!=null && observation.closeObserved && observation.ackObserved
          && observation.originalUnchanged && observation.readback!=null
          && digest(observation.readback).equals(expected.contentDigest);
      record(verified ? State.COMMITTED : State.UNKNOWN);
      return state;
    }
  }
  static String digest(byte[] bytes) {
    try {
      StringBuilder out=new StringBuilder();
      for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format(Locale.ROOT,"%02x",b&255));
      return out.toString();
    } catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
  }
  static byte[] syntheticBytes() { return "value\r\n0.30\r\n".getBytes(StandardCharsets.UTF_8); }
}
