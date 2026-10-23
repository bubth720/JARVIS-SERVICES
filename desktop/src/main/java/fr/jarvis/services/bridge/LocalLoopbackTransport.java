package fr.jarvis.services.bridge;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import java.nio.file.*;
import java.nio.charset.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Explicit synthetic desktop lab only: fixed Java child via stdio, no sockets or external peer. */
public final class LocalLoopbackTransport implements TransportPort {
  private final Path syntheticDirectory;
  public LocalLoopbackTransport(Guard guard, Guard.Context host, Path newSyntheticDirectory) {
    guard.require(host, "bridge.lab");
    try {
      Path root = newSyntheticDirectory.toRealPath();
      Path temp = Paths.get(System.getProperty("java.io.tmpdir")).toRealPath();
      if (!root.startsWith(temp) || !root.getFileName().toString().startsWith("jarvis-bridge-lab-")) throw new Failure("LAB_DIRECTORY_REQUIRED");
      syntheticDirectory = root;
    } catch (IOException e) { throw new Failure("LAB_DIRECTORY_REQUIRED"); }
  }
  private static String childClasspath() throws Exception {
    java.util.Set<String> entries = new java.util.LinkedHashSet<>();
    for (Class<?> type : new Class<?>[]{LoopbackReceiver.class, fr.jarvis.services.shared.storage.Store.class,
        com.google.gson.Gson.class, org.sqlite.JDBC.class, org.slf4j.LoggerFactory.class})
      entries.add(Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
    return String.join(java.io.File.pathSeparator, entries);
  }
  public String exchange(BridgeMessage message) {
    Process child = null;
    try {
      Path javaExecutable = Paths.get(System.getProperty("java.home"), "bin", "java");
      ProcessBuilder builder = new ProcessBuilder(javaExecutable.toString(), "-cp", childClasspath(),
          LoopbackReceiver.class.getName(), syntheticDirectory.resolve("inbox.sqlite").toString());
      builder.redirectError(ProcessBuilder.Redirect.DISCARD); child = builder.start();
      try (OutputStream out = child.getOutputStream()) { out.write(message.toJson().getBytes(StandardCharsets.UTF_8)); out.write('\n'); }
      if (!child.waitFor(5, TimeUnit.SECONDS)) { child.destroyForcibly(); throw new Failure("LAB_TIMEOUT"); }
      if (child.exitValue() != 0) throw new Failure("LAB_PEER_FAILED");
      ByteArrayOutputStream response = new ByteArrayOutputStream();
      try (InputStream in = child.getInputStream()) {
        byte[] buffer = new byte[1024]; int n;
        while ((n = in.read(buffer)) != -1) { if (response.size() + n > 65536) throw new Failure("INPUT_TOO_LARGE"); response.write(buffer, 0, n); }
      }
      return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(response.toByteArray())).toString().trim();
    } catch (Failure e) { throw e; }
    catch (Exception e) { throw new Failure("LAB_TRANSPORT_FAILED"); }
    finally { if (child != null && child.isAlive()) child.destroyForcibly(); }
  }
}
