package fr.jarvis.services.demo;

import fr.jarvis.services.platform.*;
import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Synthetic local demo only. No LLM, network, personal files or automatic import. */
public final class ToolsDemo {
  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("jarvis-services-synthetic-");
    JdbcDatabase db = new JdbcDatabase(dir.resolve("demo.sqlite"));
    try {
      Store store = new Store(db); Guard guard = new Guard(dir, store);
      Guard.Context host = new Guard.Context("synthetic-demo", new HashSet<>(Arrays.asList("confirm", "documents.read", "documents.import", "documents.export", "data.read")));
      Documents docs = new Documents(guard, store, new PdfBoxText());
      docs.importBytes(host, "sample.txt", "JARVIS synthetic source alpha".getBytes(StandardCharsets.UTF_8), new Task(3000));
      ToolRegistry registry = new ToolRegistry(store); ServicesTools.register(registry, docs, new Data(), guard);
      ApprovalGate gate = new ApprovalGate(store, guard);
      try (ToolDispatcher dispatcher = new ToolDispatcher(registry, guard, gate)) {
        for (ToolInvocation i : Arrays.asList(
            invocation("documents.search", Map.of("query", "alpha")),
            invocation("data.csv.normalize", Map.of("csv", "name,value\n alpha ,0.10\n alpha ,0.10\n")))) {
          ToolDispatcher.Result r = dispatcher.dispatch(host, i).result.get(5, TimeUnit.SECONDS);
          System.out.println(i.toolId + " " + r.status + " " + new com.google.gson.Gson().toJson(r.value));
        }
        ToolInvocation proposed = invocation("documents.docx.export", Map.of("path", "synthetic.docx", "text", "Synthetic confirmed report"));
        // This demo constructs its own synthetic data. Production must obtain an actual user decision.
        String token = gate.confirm(host, ServicesTools.descriptor("documents.docx.export", "documents", "documents.export", true, "file_digest", "path", "text"), proposed);
        ToolInvocation confirmed = new ToolInvocation(proposed.requestId, proposed.toolId, proposed.version, proposed.arguments, 3000, token);
        ToolDispatcher.Result r = dispatcher.dispatch(host, confirmed).result.get(5, TimeUnit.SECONDS);
        System.out.println("documents.docx.export " + r.status);
        System.out.println("DOCX readback: " + Documents.readDocx(Files.readAllBytes(dir.resolve("synthetic.docx"))).trim());
        System.out.println("Replay: " + dispatcher.dispatch(host, confirmed).result.get(5, TimeUnit.SECONDS).status);
        System.out.println("Synthetic artifacts: " + dir);
      }
    } finally { db.close(); }
  }
  private static ToolInvocation invocation(String id, Map<String, String> args) {
    return new ToolInvocation(UUID.randomUUID().toString(), id, "1.0.0", args, 3000, null);
  }
}
