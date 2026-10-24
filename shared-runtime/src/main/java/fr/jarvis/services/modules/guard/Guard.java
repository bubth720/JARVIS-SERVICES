package fr.jarvis.services.modules.guard;

import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import java.nio.file.*;

/** JVM path confinement; authorization, confirmation and audit share the portable implementation. */
public final class Guard extends AccessGuard {
  /** Source-compatible historical host context. */
  public static final class Context extends AccessGuard.Context {
    public Context(String principal, java.util.Set<String> grants) { super(principal, grants); }
  }
  private final Path root;
  public Guard(Path root, Store store) {
    super(store);
    try { Files.createDirectories(root); this.root = root.toRealPath(); }
    catch (Exception e) { throw new Failure("STORAGE_ERROR"); }
  }
  public Path path(AccessGuard.Context ctx, String operation, String relative) {
    require(ctx, operation);
    try {
      Path supplied = Paths.get(relative);
      if (supplied.isAbsolute() || relative.isEmpty()) throw new Failure("UNSAFE_PATH");
      Path resolved = root.resolve(supplied).normalize();
      if (!resolved.startsWith(root) || resolved.equals(root)) throw new Failure("UNSAFE_PATH");
      Path cursor = root;
      for (Path part : root.relativize(resolved)) {
        cursor = cursor.resolve(part);
        if (Files.isSymbolicLink(cursor)) throw new Failure("UNSAFE_PATH");
      }
      return resolved;
    } catch (Failure e) {
      throw e;
    } catch (Exception e) {
      throw new Failure("UNSAFE_PATH");
    }
  }

}
