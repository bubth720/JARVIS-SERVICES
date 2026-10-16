package fr.jarvis.services;

import static org.junit.jupiter.api.Assertions.*;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.platform.*;
import fr.jarvis.services.shared.api.Contracts;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.*;
import fr.jarvis.services.shared.tasks.Task;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class FoundationTest {
  @TempDir Path root;
  Database db;
  Store store;
  Guard guard;
  final Context owner =
      new Context("user", new HashSet<>(Arrays.asList("read", "write", "confirm")));

  @BeforeEach
  void open() {
    db = new JdbcDatabase(root.resolve("test.sqlite"));
    store = new Store(db);
    guard = new Guard(root.resolve("files"), store);
  }

  @AfterEach
  void close() {
    db.close();
  }

  void code(String expected, Runnable r) {
    assertEquals(expected, assertThrows(Failure.class, r::run).code);
  }

  @Test
  void unknownProtocolRejected() {
    code(
        "INCOMPATIBLE",
        () ->
            new Contracts.Request(
                UUID.randomUUID().toString(), "2.0.0", "data", "read", Map.of(), 100));
  }

  @Test
  void malformedRequestRejected() {
    code(
        "INVALID_REQUEST_ID",
        () -> new Contracts.Request("x", Contracts.VERSION, "data", "read", Map.of(), 100));
    code(
        "INVALID_INPUT",
        () ->
            new Contracts.Request(
                UUID.randomUUID().toString(), Contracts.VERSION, "shell", "run", Map.of(), 100));
  }

  @Test
  void permissionsFailClosed() {
    code("UNAUTHORIZED", () -> guard.require(null, "read"));
    code("UNAUTHORIZED", () -> guard.require(new Context("model", Set.of()), "write"));
  }

  @Test
  void traversalAndAbsoluteRejected() {
    for (String s : Arrays.asList("../outside", "/etc/passwd", "a/../../outside", ""))
      code("UNSAFE_PATH", () -> guard.path(owner, "read", s));
  }

  @Test
  void symbolicLinkRejected() throws Exception {
    Files.createSymbolicLink(root.resolve("files/link"), root);
    code("UNSAFE_PATH", () -> guard.path(owner, "read", "link/secret"));
  }

  @Test
  void confirmationBindsExactContent() {
    String token = guard.confirm(owner, "write", "first");
    code("CONFIRMATION_REQUIRED", () -> guard.consume(owner, "write", "changed", token));
  }

  @Test
  void confirmationBindsIdentity() {
    String token = guard.confirm(owner, "write", "first");
    code(
        "CONFIRMATION_REQUIRED",
        () -> guard.consume(new Context("other", Set.of("write")), "write", "first", token));
  }

  @Test
  void confirmationOneShot() {
    String token = guard.confirm(owner, "write", "first");
    guard.consume(owner, "write", "first", token);
    code("CONFIRMATION_REQUIRED", () -> guard.consume(owner, "write", "first", token));
  }

  @Test
  void auditDoesNotStoreContent() {
    String secret = "private-content-not-for-logs";
    guard.confirm(owner, "write", secret);
    assertFalse(new com.google.gson.Gson().toJson(store.all("audit")).contains(secret));
  }

  @Test
  void integrityAndRestart() {
    store.put("memory", "m", Map.of("id", "m", "text", "persist"));
    db.close();
    db = new JdbcDatabase(root.resolve("test.sqlite"));
    store = new Store(db);
    assertEquals("persist", store.get("memory", "m").get("text"));
    assertEquals("ok", store.integrity());
  }

  @Test
  void transactionRollsBack() {
    assertThrows(
        IllegalStateException.class,
        () ->
            store.transaction(
                () -> {
                  store.put("memory", "x", Map.of("id", "x"));
                  throw new IllegalStateException();
                }));
    assertNull(store.get("memory", "x"));
  }

  @Test
  void migrationRoundtrip() {
    store.migrate(0);
    store.migrate(1);
    assertEquals("ok", store.integrity());
  }

  @Test
  void destructiveDowngradeRefused() {
    store.put("memory", "x", Map.of("id", "x"));
    code("BACKUP_REQUIRED", () -> store.migrate(0));
    assertNotNull(store.get("memory", "x"));
  }

  @Test
  void backupRestoreRebuildsSearch() {
    store.put("knowledge", "k", Map.of("id", "k", "text", "offline source"));
    store.index("k", "offline source");
    String backup = store.exportSnapshot();
    store.delete("knowledge", "k");
    store.unindex("k");
    store.restoreSnapshot(backup);
    assertEquals("k", store.search("offline", 1).get(0).get("id"));
  }

  @Test
  void malformedBackupDoesNotDelete() {
    store.put("memory", "x", Map.of("id", "x"));
    assertThrows(RuntimeException.class, () -> store.restoreSnapshot("{}"));
    assertNotNull(store.get("memory", "x"));
  }

  @Test
  void cancellationAndDeadline() throws Exception {
    Task cancelled = new Task(1000);
    cancelled.cancel();
    code("CANCELLED", cancelled::check);
    Task expired = new Task(1);
    Thread.sleep(10);
    code("EXPIRED", expired::check);
  }

  @Test
  void singleHeavyTaskAndRelease() throws Exception {
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> f =
        executor.submit(
            () ->
                new Task(10000)
                    .heavy(
                        () -> {
                          entered.countDown();
                          release.await();
                          return null;
                        }));
    try {
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      code("BUSY", () -> new Task(1000).heavy(() -> null));
    } finally {
      release.countDown();
      f.get(2, TimeUnit.SECONDS);
      executor.shutdownNow();
    }
    assertEquals("ok", new Task(1000).heavy(() -> "ok"));
  }

  @Test
  void progressSequence() {
    Task t = new Task(1000);
    t.progress("extract", 10);
    t.progress("index", 20);
    assertEquals(2, t.events().get(1).get("sequence"));
  }
}
