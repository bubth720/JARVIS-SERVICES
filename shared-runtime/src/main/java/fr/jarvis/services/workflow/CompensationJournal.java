package fr.jarvis.services.workflow;

import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Compensation owns only fresh generated temporary files. Never deletes an exported document. */
public final class CompensationJournal {
  private final Store store; private final Guard guard;
  public CompensationJournal(Store store, Guard guard) { this.store = store; this.guard = guard; }
  public String stage(Context host, byte[] bytes, Task task) {
    guard.require(host, "actions.run"); task.check();
    if (bytes == null || bytes.length > Documents.MAX_BYTES) throw new Failure("INPUT_TOO_LARGE");
    bytes = bytes.clone();
    if (store.count("workflow_temps") >= 1024) throw new Failure("COMPENSATION_LIMIT");
    String id = UUID.randomUUID().toString(), name = ".workflow-" + id + ".tmp";
    Path file = guard.path(host, "documents.export", name);
    Map<String,Object> row = new TreeMap<>(); row.put("id", id); row.put("generated_name", name); row.put("sha256", Guard.digest(bytes)); row.put("state", "PREPARED");
    store.put("workflow_temps", id, row); boolean owned = false;
    try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) {
      owned = true; task.check(); out.write(bytes); task.check();
    } catch (Exception e) {
      row.put("state", "UNKNOWN"); store.put("workflow_temps", id, row);
      if (owned) try { Files.deleteIfExists(file); } catch (IOException ignored) {}
      throw new Failure("TEMPORARY_STAGE_FAILED");
    }
    row.put("state", "READY"); store.put("workflow_temps", id, row); return id;
  }
  public void compensate(Context host, String id) {
    guard.require(host, "actions.run");
    store.transaction(() -> {
      Map<String,Object> row = store.get("workflow_temps", id);
      if (row == null || !"READY".equals(row.get("state"))) throw new Failure("COMPENSATION_UNAVAILABLE");
      String expected = ".workflow-" + id + ".tmp";
      if (!expected.equals(row.get("generated_name"))) throw new Failure("UNSAFE_PATH");
      Path file = guard.path(host, "documents.export", expected);
      try {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > Documents.MAX_BYTES
            || !Guard.digest(Files.readAllBytes(file)).equals(row.get("sha256"))) throw new Failure("TEMPORARY_CHANGED");
        Files.delete(file); row.put("state", "COMPENSATED"); store.put("workflow_temps", id, row);
      } catch (IOException e) { throw new Failure("COMPENSATION_UNKNOWN"); }
    });
  }
}
