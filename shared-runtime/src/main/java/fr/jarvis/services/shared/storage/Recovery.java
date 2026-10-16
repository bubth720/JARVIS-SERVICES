package fr.jarvis.services.shared.storage;

import com.google.gson.*;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Explicit logical recovery package, not a legacy CORE/UNIFIED backup format. */
public final class Recovery {
  private final Store store;
  private final Guard guard;

  public Recovery(Store store, Guard guard) {
    this.store = store;
    this.guard = guard;
  }

  public String backup(Context ctx, Task task) {
    guard.require(ctx, "storage.backup");
    return task.heavy(
        () -> {
          Map<String, Object> packageData = new LinkedHashMap<>();
          String snapshot = store.exportSnapshot();
          packageData.put("schema_version", Contracts.VERSION);
          packageData.put("snapshot", snapshot);
          packageData.put("sha256", Guard.digest(snapshot));
          Map<String, Object> originals = new LinkedHashMap<>();
          long total = 0;
          for (Map<String, Object> doc : store.scan("documents")) {
            task.check();
            String name = (String) doc.get("original");
            Path path = guard.path(ctx, "storage.backup", name);
            long size = Files.size(path);
            total += size;
            if (total > 16 * 1024 * 1024) throw new Failure("BACKUP_TOO_LARGE");
            byte[] bytes = Files.readAllBytes(path);
            if (!Guard.digest(bytes).equals(doc.get("sha256")))
              throw new Failure("INTEGRITY_FAILED");
            originals.put(name, Base64.getEncoder().encodeToString(bytes));
          }
          packageData.put("originals", originals);
          return new Gson().toJson(packageData);
        });
  }

  public void restore(Context ctx, String backup, String confirmation, Task task) {
    guard.consume(ctx, "storage.restore", Guard.digest(backup), confirmation);
    task.heavy(
        () -> {
          if (backup.length() > 32 * 1024 * 1024) throw new Failure("BACKUP_TOO_LARGE");
          JsonObject pack = JsonParser.parseString(backup).getAsJsonObject();
          if (!Contracts.VERSION.equals(pack.get("schema_version").getAsString()))
            throw new Failure("INCOMPATIBLE");
          String snapshot = pack.get("snapshot").getAsString();
          if (!Guard.digest(snapshot).equals(pack.get("sha256").getAsString()))
            throw new Failure("INTEGRITY_FAILED");
          JsonObject parsed = JsonParser.parseString(snapshot).getAsJsonObject(),
              originals = pack.getAsJsonObject("originals");
          Map<Path, byte[]> files = new LinkedHashMap<>();
          for (JsonElement d : parsed.getAsJsonArray("documents")) {
            task.check();
            JsonObject doc = d.getAsJsonObject();
            String name = doc.get("original").getAsString();
            Path path = guard.path(ctx, "storage.restore", name);
            byte[] bytes = Base64.getDecoder().decode(originals.get(name).getAsString());
            if (!Guard.digest(bytes).equals(doc.get("sha256").getAsString()))
              throw new Failure("INTEGRITY_FAILED");
            if (Files.exists(path)) {
              if (!Guard.digest(Files.readAllBytes(path)).equals(doc.get("sha256").getAsString()))
                throw new Failure("RESTORE_CONFLICT");
            } else files.put(path, bytes);
          }
          List<Path> created = new ArrayList<>();
          try {
            for (Map.Entry<Path, byte[]> f : files.entrySet()) {
              task.check();
              Files.write(f.getKey(), f.getValue(), StandardOpenOption.CREATE_NEW);
              created.add(f.getKey());
            }
            task.check();
            store.restoreSnapshot(snapshot);
          } catch (RuntimeException | IOException e) {
            for (Path path : created)
              try {
                Files.deleteIfExists(path);
              } catch (IOException ignored) {
              }
            throw e;
          }
          return null;
        });
  }
}
