package fr.jarvis.services.tools;

import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import java.util.*;

/** Three real adapters; other historical modules remain available through their existing APIs. */
public final class ServicesTools {
  private ServicesTools() {}
  public static void register(ToolRegistry registry, Documents docs, Data data, Guard guard) {
    registry.register(descriptor("documents.search", "documents", "documents.read", false, "document_hits", "query"),
        (ctx, args, task) -> { task.check(); return docs.search(ctx, args.get("query")); });
    registry.register(descriptor("data.csv.normalize", "data", "data.read", false, "csv_table", "csv"),
        (ctx, args, task) -> { guard.require(ctx, "data.read"); return task.heavy(() -> data.csv(args.get("csv")).normalize().deduplicate().csv()); });
    registry.register(descriptor("documents.docx.export", "documents", "documents.export", true, "file_digest", "path", "text"),
        (ctx, args, task) -> docs.export(ctx, args.get("path"), "docx", args.get("text"), task));
  }
  public static ToolDescriptor descriptor(String id, String module, String permission,
      boolean action, String output, String... fields) {
    return new ToolDescriptor(id, "1.0.0", module, permission, new TreeSet<>(Arrays.asList(fields)), output, action, 2 * 1024 * 1024L);
  }
}
