package fr.jarvis.services.bridge;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.platform.JdbcDatabase;
import fr.jarvis.services.shared.storage.Store;
import java.nio.file.*;
import java.nio.charset.*;
import java.io.*;
import java.util.*;

/** Fixed child entrypoint, accepts exactly one bounded synthetic metadata message, never executes tools. */
public final class LoopbackReceiver {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("Synthetic lab only");
    Path dbPath = Paths.get(args[0]).normalize().toAbsolutePath();
    Path parent = dbPath.getParent().toRealPath();
    if (!parent.startsWith(Paths.get(System.getProperty("java.io.tmpdir")).toRealPath())
        || !parent.getFileName().toString().startsWith("jarvis-bridge-lab-") || !dbPath.getFileName().toString().equals("inbox.sqlite"))
      throw new IllegalArgumentException("Synthetic lab only");
    ByteArrayOutputStream input = new ByteArrayOutputStream();
    int b; while ((b = System.in.read()) != -1 && b != '\n') { if (input.size() >= 65536) throw new IllegalArgumentException("Input limit"); input.write(b); }
    String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(input.toByteArray())).toString();
    BridgeMessage message = BridgeMessage.parse(json, new UnifiedLabValidator());
    JdbcDatabase db = new JdbcDatabase(dbPath);
    try {
      Store store = new Store(db); Guard guard = new Guard(parent, store);
      Guard.Context host = new Guard.Context("synthetic-loopback", Collections.singleton("bridge.lab"));
      Map<String,Object> receipt = new BridgeInbox(store, guard).receive(host, message);
      System.out.println(new com.google.gson.Gson().toJson(receipt));
    } finally { db.close(); }
  }
}
