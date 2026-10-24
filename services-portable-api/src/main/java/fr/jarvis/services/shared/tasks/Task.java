package fr.jarvis.services.shared.tasks;

import fr.jarvis.services.shared.api.Contracts.Failure;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Task {
  private static final AtomicBoolean HEAVY = new AtomicBoolean();
  public final String id = UUID.randomUUID().toString();
  private final long deadline;
  private volatile boolean cancelled;
  private int sequence;
  private final List<Map<String, Object>> events = new ArrayList<>();

  public Task(long timeoutMs) {
    if (timeoutMs < 1 || timeoutMs > 90000) throw new Failure("INVALID_TIMEOUT");
    deadline = System.nanoTime() + timeoutMs * 1_000_000L;
  }

  public void cancel() {
    cancelled = true;
  }

  public void check() {
    if (cancelled || Thread.currentThread().isInterrupted()) throw new Failure("CANCELLED");
    if (System.nanoTime() > deadline) throw new Failure("EXPIRED");
  }

  public synchronized void progress(String phase, int percent) {
    check();
    if (percent < 0 || percent > 100 || phase == null) throw new Failure("INVALID_PROGRESS");
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("request_id", id);
    e.put("sequence", ++sequence);
    e.put("phase", phase);
    e.put("percent", percent);
    events.add(e);
  }

  public synchronized List<Map<String, Object>> events() {
    return new ArrayList<>(events);
  }

  public <T> T heavy(java.util.concurrent.Callable<T> action) {
    check();
    if (!HEAVY.compareAndSet(false, true)) throw new Failure("BUSY");
    try {
      T result = action.call();
      check();
      return result;
    } catch (Failure e) {
      throw e;
    } catch (Exception e) {
      throw new Failure("PROCESSING_ERROR");
    } finally {
      HEAVY.set(false);
    }
  }
}
