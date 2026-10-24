package fr.jarvis.services;

import static org.junit.jupiter.api.Assertions.*;

import fr.jarvis.services.bridge.*;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.platform.JdbcDatabase;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class OfflineBridgeSecurityTest {
  @TempDir Path root;
  JdbcDatabase a, b;
  Store sa, sb;
  Guard ga, gb;
  OfflineBridge sender, receiver;
  String session = UUID.randomUUID().toString();
  byte[] key = new byte[32];
  Guard.Context host = new Guard.Context("paired-test", Set.of("bridge.file"));

  @BeforeEach
  void open() {
    new java.security.SecureRandom().nextBytes(key);
    a = new JdbcDatabase(root.resolve("a.sqlite"));
    b = new JdbcDatabase(root.resolve("b.sqlite"));
    sa = new Store(a);
    sb = new Store(b);
    ga = new Guard(root.resolve("afiles"), sa);
    gb = new Guard(root.resolve("bfiles"), sb);
    sender = new OfflineBridge(sa, ga, "phone", "pc", session, key);
    receiver = new OfflineBridge(sb, gb, "pc", "phone", session, key);
  }

  @AfterEach
  void close() {
    sender.close();
    receiver.close();
    a.close();
    b.close();
  }

  @Test
  void interruptionDuplicatesAndAuthenticatedAckNeverExecuteActions() {
    String id = sender.queue(host, "document.reference", UUID.randomUUID().toString());
    String wire = sender.frame(host, id);
    String ack = receiver.receive(host, wire);
    sender.interrupted(host, id);
    assertEquals("UNKNOWN", sender.state(host, id));
    assertEquals(ack, receiver.receive(host, wire));
    sender.acknowledge(host, ack);
    assertEquals("ACKED", sender.state(host, id));
    assertEquals(1, sb.count("offline_inbox"));
    assertEquals(0, sb.count("actions"));
    assertThrows(Failure.class, () -> sender.frame(host, id));
  }

  @Test
  void keyAuthenticationAndTamperingAreRefused() {
    String id = sender.queue(host, "diagnostic.ping", UUID.randomUUID().toString());
    String wire = sender.frame(host, id);
    assertThrows(
        Failure.class,
        () -> receiver.receive(host, wire.replace("diagnostic.ping", "document.reference")));
    byte[] wrong = key.clone();
    wrong[0] ^= 1;
    assertThrows(Failure.class, () -> new OfflineBridge(sb, gb, "pc", "phone", session, wrong));
    assertEquals(0, sb.count("offline_inbox"));
  }

  @Test
  void arbitraryCommandsAndMissingPermissionAreRefused() {
    assertThrows(
        Failure.class, () -> sender.queue(host, "tool.invoke", UUID.randomUUID().toString()));
    assertThrows(
        Failure.class,
        () ->
            sender.queue(
                new Guard.Context("model", Set.of()),
                "diagnostic.ping",
                UUID.randomUUID().toString()));
    assertEquals(0, sa.count("offline_outbox"));
  }

  @Test
  void duplicateAndSequenceStateSurviveReceiverRestart() {
    String id = sender.queue(host, "diagnostic.ping", UUID.randomUUID().toString());
    String wire = sender.frame(host, id);
    String ack = receiver.receive(host, wire);
    receiver.close();
    b.close();
    b = new JdbcDatabase(root.resolve("b.sqlite"));
    sb = new Store(b);
    gb = new Guard(root.resolve("bfiles"), sb);
    receiver = new OfflineBridge(sb, gb, "pc", "phone", session, key);
    assertEquals(ack, receiver.receive(host, wire));
    assertEquals(1, sb.count("offline_inbox"));
  }

  @Test
  void outOfOrderAndFakeAckAreRefused() {
    sender.queue(host, "diagnostic.ping", UUID.randomUUID().toString());
    String second = sender.queue(host, "diagnostic.ping", UUID.randomUUID().toString());
    assertThrows(Failure.class, () -> receiver.receive(host, sender.frame(host, second)));
    assertThrows(Failure.class, () -> sender.acknowledge(host, "{\"kind\":\"ACK\"}"));
    assertEquals(0, sb.count("offline_inbox"));
  }

  @Test
  void reflectedMessageAndDuplicateJsonKeysAreRefused() {
    String id = sender.queue(host, "diagnostic.ping", UUID.randomUUID().toString());
    String wire = sender.frame(host, id);
    assertThrows(Failure.class, () -> sender.receive(host, wire));
    assertThrows(
        Failure.class,
        () -> receiver.receive(host, wire.replaceFirst("\\{", "{\"kind\":\"DATA\",")));
  }

  @Test
  void senderUnknownStateSurvivesRestartAndRequiresExplicitAck() {
    String id = sender.queue(host, "diagnostic.ping", UUID.randomUUID().toString());
    String frame = sender.frame(host, id);
    sender.interrupted(host, id);
    sender.close();
    a.close();
    a = new JdbcDatabase(root.resolve("a.sqlite"));
    sa = new Store(a);
    ga = new Guard(root.resolve("afiles"), sa);
    sender = new OfflineBridge(sa, ga, "phone", "pc", session, key);
    assertEquals("UNKNOWN", sender.state(host, id));
    assertEquals(frame, sender.frame(host, id));
    assertEquals("UNKNOWN", sender.state(host, id));
    sender.acknowledge(host, receiver.receive(host, frame));
    assertEquals("ACKED", sender.state(host, id));
  }
}
