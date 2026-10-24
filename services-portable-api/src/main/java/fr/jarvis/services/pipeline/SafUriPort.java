package fr.jarvis.services.pipeline;

import fr.jarvis.services.shared.tasks.Task;

/** Host-bound, explicitly selected SAF tree. No arbitrary URI can be resolved by a tool.
 * Implementations must create a fresh document, never truncate an existing one, and
 * rollback only that document. Visibility need not be atomic; UNKNOWN is preserved.
 */
public interface SafUriPort {
  String targetReference();
  Transaction begin(String targetReference, String freshName, String mime, Task task);
  interface Transaction {
    void write(byte[] bytes, Task task);
    String readbackSha256(Task task);
    String commit(Task task);
    /** Return true only when the fresh incomplete document was removed. */
    boolean rollback();
  }
}
