package fr.jarvis.services;

import static org.junit.jupiter.api.Assertions.*;

import fr.jarvis.services.android.AndroidDatabase;
import fr.jarvis.services.shared.storage.Store;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exact Android adapter code + JVM native library, not an Android device test. */
class BundledStorageTest {
  @TempDir Path root;

  @Test
  void bundledDriverSupportsFts5AndRollback() {
    try (AndroidDatabase db = new AndroidDatabase(root.resolve("bundled.sqlite").toString())) {
      Store store = new Store(db);
      store.index("s", "android compatible sqlite");
      assertEquals("s", store.search("compatible", 1).get(0).get("id"));
      assertThrows(
          IllegalStateException.class,
          () ->
              store.transaction(
                  () -> {
                    store.index("bad", "rollback");
                    throw new IllegalStateException();
                  }));
      assertTrue(store.search("rollback", 1).isEmpty());
      assertEquals("ok", store.integrity());
    }
  }
}
