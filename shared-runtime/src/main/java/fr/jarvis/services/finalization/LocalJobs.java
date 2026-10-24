package fr.jarvis.services.finalization;

import com.google.gson.Gson;
import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.pipeline.*;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.*;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import fr.jarvis.services.workflow.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/** Private, bounded workspaces. UI-owned grants/confirmations; no external authority or network. */
public final class LocalJobs implements AutoCloseable {
  public final Store store;
  public final Guard guard;
  public final ToolRegistry registry;
  public final ApprovalGate approvals;
  public final ToolDispatcher dispatcher;
  public final WorkflowStateStore states;
  public final WorkflowEngine workflows;
  private final DocumentService documents;
  private final ConsultationService consultation;
  private final Documents writer;
  private final Path root;
  private final Map<String, SafUriPort> destinations =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final Gson JSON = new Gson();
  private static final String JOBS = "final_jobs";
  public static final ToolDescriptor SAF =
      ServicesTools.descriptor(
          "workspace.saf.export",
          "documents",
          "documents.export",
          true,
          "verified_fresh_document_v1",
          "job",
          "digest",
          "target_ref");

  public LocalJobs(
      Path root, Database db, Documents.PdfText pdf, DocumentTextExtractor.PdfPages pages) {
    store = new Store(db);
    guard = new Guard(root, store);
    this.root = root.toAbsolutePath().normalize();
    documents = new DocumentService(pages);
    consultation = new ConsultationService(guard, null);
    writer = new Documents(guard, store, pdf);
    registry = new ToolRegistry(store);
    approvals = new ApprovalGate(store, guard);
    ServicesTools.register(registry, writer, new Data(), guard);
    registry.register(
        ServicesTools.descriptor(
            "workspace.verify",
            "documents",
            "documents.read",
            false,
            "verified_plan_v1",
            "job",
            "digest"),
        (ctx, args, task) -> {
          Map<String, Object> job = checked(ctx, args.get("job"), args.get("digest"), task);
          return Collections.singletonMap("sha256", job.get("output_digest"));
        });
    registry.register(
        ServicesTools.descriptor(
            "workspace.publish",
            "documents",
            "documents.export",
            true,
            "private_file_v1",
            "job",
            "digest"),
        (ctx, args, task) -> {
          Map<String, Object> job = checked(ctx, args.get("job"), args.get("digest"), task);
          quota(((String) job.get("text")).getBytes(StandardCharsets.UTF_8).length + 65536);
          Map<String, Object> result =
              writer.export(
                  ctx,
                  (String) job.get("output"),
                  (String) job.get("format"),
                  (String) job.get("text"),
                  task);
          job.put("encoded_digest", result.get("sha256"));
          store.put(JOBS, (String) job.get("id"), job);
          return result;
        });
    registry.register(SAF, (ctx, args, task) -> copyToSelectedTree(ctx, args, task));
    FinalToolCatalogue.register(
        registry,
        guard,
        documents,
        consultation,
        new fr.jarvis.services.modules.memory.Memory(store, guard));
    dispatcher = new ToolDispatcher(registry, guard, approvals, store);
    states = new WorkflowStateStore(store);
    workflows = new WorkflowEngine(states, guard, registry, dispatcher);
  }

  public String prepareDocument(
      Context host,
      String name,
      byte[] bytes,
      String query,
      Map<String, String> fields,
      String find,
      String replacement,
      Task task) {
    guard.require(host, "documents.import");
    guard.require(host, "documents.read");
    guard.require(host, "knowledge.read");
    List<DocumentTextExtractor.Page> pages = task.heavy(() -> documents.read(name, bytes, task));
    String text;
    if ((fields != null && !fields.isEmpty()) || (find != null && !find.isEmpty())) {
      StringBuilder body = new StringBuilder();
      for (DocumentTextExtractor.Page p : pages) body.append(p.text).append('\n');
      text = body.toString();
      if (fields != null && !fields.isEmpty()) text = documents.template(text, fields);
      if (find != null && !find.isEmpty()) text = documents.replace(text, find, replacement);
      text +=
          "\nSource originale conservée : "
              + name
              + " ; SHA-256 : "
              + Guard.digest(bytes)
              + "\nDocument dérivé en texte simple; mise en page originale non conservée.\n";
    } else
      text =
          consultation.report(consultation.consult(host, query == null ? "" : query, pages, task));
    return prepare(host, name, bytes, text, "docx", "DOCUMENT", task);
  }

  public String prepareText(Context host, String text, Task task) {
    guard.require(host, "documents.import");
    DocumentService.bounded(text);
    return prepare(
        host, "texte.txt", text.getBytes(StandardCharsets.UTF_8), text, "docx", "DOCUMENT", task);
  }

  public String prepareTable(
      Context host,
      String name,
      byte[] bytes,
      List<DataTransformEngine.Operation> recipe,
      Map<String, TableService.Column> schema,
      String format,
      Task task) {
    guard.require(host, "documents.import");
    guard.require(host, "data.read");
    guard.require(host, "analyst.read");
    if (!Arrays.asList("csv", "json").contains(format)
        || bytes == null
        || bytes.length > Documents.MAX_BYTES) throw new Failure("INVALID_INPUT");
    String csv = utf8(bytes);
    if (name.toLowerCase(Locale.ROOT).endsWith(".json"))
      csv = new Data().json(Recipes.parse(csv).toString()).csv();
    TableService.Preview preview = new TableService().transform(csv, recipe, schema, task);
    String text = format.equals("csv") ? preview.after.csv() : preview.after.json();
    Map<String, Object> analysis = new StructuredAnalyst().analyze(preview.after, schema, task);
    analysis.put("input_file_sha256", Guard.digest(bytes));
    analysis.put("recipe", JSON.toJson(recipe));
    Map<String, Object> metadata = new TreeMap<>();
    Map<String, Object> difference = new TreeMap<>();
    difference.put("before_rows", preview.before.rows.size());
    difference.put("after_rows", preview.after.rows.size());
    difference.put("changed_cells_by_position", preview.changedCells);
    difference.put("changed_row_indices_first_100", preview.changedRowIndices);
    difference.put("removed_rows", preview.removedRows);
    difference.put("added_rows", preview.addedRows);
    difference.put("columns", preview.before.columns);
    difference.put(
        "before_sample", preview.before.rows.subList(0, Math.min(20, preview.before.rows.size())));
    difference.put(
        "after_sample", preview.after.rows.subList(0, Math.min(20, preview.after.rows.size())));
    difference.put("operations", preview.after.history);
    difference.put("summaries", preview.summaries);
    metadata.put("difference", difference);
    metadata.put("analysis", analysis);
    metadata.put("schema", JSON.toJson(schema));
    metadata.put("recipe", JSON.toJson(recipe));
    return prepare(host, name, bytes, text, format, "DATA", task, metadata);
  }

  public String prepareMemoryExport(
      Context host, fr.jarvis.services.modules.memory.Memory memory, Task task) {
    guard.require(host, "memory.read");
    guard.require(host, "documents.import");
    String text = memory.export(host);
    return prepare(
        host,
        "memoire-locale.json",
        text.getBytes(StandardCharsets.UTF_8),
        text,
        "json",
        "MEMORY_LOCAL_ONLY",
        task);
  }

  private synchronized String prepare(
      Context host, String name, byte[] bytes, String text, String format, String kind, Task task) {
    return prepare(host, name, bytes, text, format, kind, task, Collections.emptyMap());
  }

  private synchronized String prepare(
      Context host,
      String name,
      byte[] bytes,
      String text,
      String format,
      String kind,
      Task task,
      Map<String, Object> metadata) {
    guard.require(host, "documents.import");
    task.check();
    DocumentService.bounded(text);
    if (bytes == null
        || bytes.length > Documents.MAX_BYTES
        || name == null
        || name.isEmpty()
        || name.length() > 200
        || name.chars().anyMatch(c -> c < 32)
        || store.count(JOBS) >= 64) throw new Failure("WORKSPACE_LIMIT");
    quota(bytes.length + text.getBytes(StandardCharsets.UTF_8).length + 65536);
    String id = UUID.randomUUID().toString(), original = "source-" + id + ".bin";
    Path path = guard.path(host, "documents.import", original);
    boolean saved = false, created = false;
    try {
      Files.createFile(path);
      created = true;
      Files.write(path, bytes);
      task.check();
      Map<String, Object> row = new TreeMap<>();
      row.put("id", id);
      row.put("kind", kind);
      row.put("source_name", name);
      row.put("source", original);
      row.put("source_digest", Guard.digest(bytes));
      row.put("text", text);
      row.put("output_digest", Guard.digest(text));
      row.put("format", format);
      row.put("output", "result-" + id + "." + format);
      row.put("created_at", Long.toString(System.currentTimeMillis()));
      row.put("publication", "PENDING");
      row.putAll(metadata);
      row.put("plan_digest", planDigest(row));
      store.transaction(
          () -> {
            store.put(JOBS, id, row);
            states.prepare(definition(row));
          });
      saved = true;
      return id;
    } catch (Failure e) {
      throw e;
    } catch (IOException e) {
      throw new Failure("STORAGE_ERROR");
    } finally {
      if (created && !saved)
        try {
          Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
  }

  private void quota(long extra) {
    try (java.util.stream.Stream<Path> entries = Files.list(root)) {
      long used = 0;
      for (Path p : (Iterable<Path>) entries::iterator) {
        if (Files.isSymbolicLink(p) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
          throw new Failure("UNSAFE_WORKSPACE");
        used += Files.size(p);
      }
      if (extra < 0 || used + extra > 64L * 1024 * 1024) throw new Failure("WORKSPACE_QUOTA");
    } catch (IOException e) {
      throw new Failure("STORAGE_ERROR");
    }
  }

  private String planDigest(Map<String, Object> row) {
    Map<String, Object> plan = new TreeMap<>();
    for (String key :
        Arrays.asList(
            "id",
            "kind",
            "source_name",
            "source",
            "source_digest",
            "output_digest",
            "format",
            "output",
            "schema",
            "recipe")) plan.put(key, row.get(key));
    return Guard.digest(JSON.toJson(plan));
  }

  private Map<String, Object> checked(Context host, String id, String digest, Task task) {
    guard.require(host, "documents.read");
    task.check();
    Map<String, Object> row = job(host, id);
    if (!planDigest(row).equals(digest)
        || !digest.equals(row.get("plan_digest"))
        || !Guard.digest((String) row.get("text")).equals(row.get("output_digest")))
      throw new Failure("CORRUPT_PLAN");
    try {
      Path file = guard.path(host, "documents.read", (String) row.get("source"));
      if (Files.size(file) > Documents.MAX_BYTES
          || !Guard.digest(Files.readAllBytes(file)).equals(row.get("source_digest")))
        throw new Failure("SOURCE_CHANGED");
    } catch (IOException e) {
      throw new Failure("SOURCE_UNAVAILABLE");
    }
    return row;
  }

  public Map<String, Object> job(Context host, String id) {
    guard.require(host, "documents.read");
    try {
      if (!UUID.fromString(id).toString().equals(id)) throw new Exception();
    } catch (Exception e) {
      throw new Failure("INVALID_JOB");
    }
    Map<String, Object> row = store.get(JOBS, id);
    if (row == null) throw new Failure("NOT_FOUND");
    return row;
  }

  public WorkflowDefinition definition(Map<String, Object> row) {
    Map<String, String> args = new TreeMap<>();
    args.put("job", (String) row.get("id"));
    args.put("digest", (String) row.get("plan_digest"));
    return new WorkflowDefinition(
        (String) row.get("id"),
        Arrays.asList(
            new WorkflowDefinition.Step(
                "verify", "workspace.verify", "1.0.0", Collections.emptySet(), args, 30000),
            new WorkflowDefinition.Step(
                "publish",
                "workspace.publish",
                "1.0.0",
                Collections.singleton("verify"),
                args,
                30000)));
  }

  public WorkflowEngine.Progress advance(Context host, String id, String approval) {
    Map<String, Object> row = job(host, id);
    return workflows.advance(
        host,
        definition(row),
        approval == null ? Collections.emptyMap() : Collections.singletonMap("publish", approval));
  }

  public String approve(Context host, ToolInvocation invocation) {
    if (invocation.toolId.equals("workspace.publish") || invocation.toolId.equals(SAF.id))
      checked(
          host,
          invocation.arguments.get("job"),
          invocation.arguments.get("digest"),
          new Task(30000));
    return approvals.confirm(
        host, registry.descriptor(invocation.toolId, invocation.version), invocation);
  }

  public void recover(Context host) {
    guard.require(host, "actions.run");
    for (Map<String, Object> row : store.scan(JOBS)) {
      workflows.recoverAfterHostRestart(host, definition(row));
      if ("RUNNING".equals(row.get("publication"))) {
        row.put("publication", "UNKNOWN");
        store.put(JOBS, (String) row.get("id"), row);
      }
    }
  }

  public List<Map<String, Object>> history(Context host) {
    guard.require(host, "documents.read");
    List<Map<String, Object>> history = new ArrayList<>();
    for (Map<String, Object> row : store.scan(JOBS)) {
      Map<String, Object> item = new TreeMap<>();
      for (String key :
          Arrays.asList("id", "kind", "source_name", "created_at", "output_digest", "publication"))
        item.put(key, row.get(key));
      item.put("workflow", states.snapshot((String) row.get("id")).get("status"));
      history.add(item);
    }
    history.sort((a, b) -> ((String) b.get("created_at")).compareTo((String) a.get("created_at")));
    return history;
  }

  public ToolInvocation exportRequest(Context host, String id, SafUriPort userSelectedTarget) {
    Map<String, Object> row = job(host, id);
    if (!"SUCCEEDED".equals(states.snapshot(id).get("status")))
      throw new Failure("WORKFLOW_INCOMPLETE");
    if (!"PENDING".equals(row.get("publication")))
      throw new Failure("PUBLICATION_ALREADY_ATTEMPTED");
    if (destinations.size() >= 16) throw new Failure("DESTINATION_LIMIT");
    destinations.put(userSelectedTarget.targetReference(), userSelectedTarget);
    Map<String, String> args = new TreeMap<>();
    args.put("job", id);
    args.put("digest", (String) row.get("plan_digest"));
    args.put("target_ref", userSelectedTarget.targetReference());
    return new ToolInvocation(
        UUID.nameUUIDFromBytes((id + ":saf").getBytes(StandardCharsets.UTF_8)).toString(),
        SAF.id,
        SAF.version,
        args,
        30000,
        null);
  }

  public void releaseTarget(Context host, String reference) {
    guard.require(host, "documents.export");
    destinations.remove(reference);
  }

  public ToolDispatcher.Handle export(Context host, ToolInvocation request, String approval) {
    return dispatcher.dispatch(
        host,
        new ToolInvocation(
            request.requestId,
            request.toolId,
            request.version,
            request.arguments,
            request.timeoutMs,
            approval));
  }

  private Object copyToSelectedTree(Context host, Map<String, String> args, Task task) {
    Map<String, Object> row = checked(host, args.get("job"), args.get("digest"), task);
    if (!"SUCCEEDED".equals(states.snapshot(args.get("job")).get("status")))
      throw new Failure("WORKFLOW_INCOMPLETE");
    SafUriPort target = destinations.get(args.get("target_ref"));
    if (target == null) throw new Failure("HOST_DESTINATION_REQUIRED");
    Path path = guard.path(host, "documents.export", (String) row.get("output"));
    byte[] bytes;
    try {
      if (Files.size(path) > Documents.MAX_BYTES) throw new Failure("OUTPUT_TOO_LARGE");
      bytes = Files.readAllBytes(path);
    } catch (IOException e) {
      throw new Failure("OUTPUT_UNAVAILABLE");
    }
    if (!Guard.digest(bytes).equals(row.get("encoded_digest"))) throw new Failure("OUTPUT_CHANGED");
    store.transaction(
        () -> {
          Map<String, Object> current = store.get(JOBS, args.get("job"));
          if (!"PENDING".equals(current.get("publication")))
            throw new Failure("PUBLICATION_ALREADY_ATTEMPTED");
          current.put("publication", "RUNNING");
          store.put(JOBS, args.get("job"), current);
        });
    SafUriPort.Transaction tx = null;
    boolean committed = false, attempted = false;
    try {
      task.check();
      attempted = true;
      String format = (String) row.get("format"),
          mime =
              format.equals("docx")
                  ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                  : format.equals("csv") ? "text/csv" : "application/json";
      tx = target.begin(target.targetReference(), (String) row.get("output"), mime, task);
      tx.write(bytes, task);
      task.check();
      String digest = tx.readbackSha256(task);
      if (!Guard.digest(bytes).equals(digest)) throw new Failure("INTEGRITY_FAILED");
      String uri = tx.commit(task);
      committed = true;
      row.put("publication", "PUBLISHED");
      row.put("export_uri", uri);
      row.put("export_sha256", digest);
      store.put(JOBS, args.get("job"), row);
      guard.audit("workspace.export", "success");
      return Collections.singletonMap("sha256", digest);
    } catch (RuntimeException e) {
      boolean rolledBack = !attempted;
      if (tx != null && !committed)
        try {
          rolledBack = tx.rollback();
        } catch (RuntimeException ignored) {
        }
      row.put("publication", rolledBack ? "FAILED" : "UNKNOWN");
      store.put(JOBS, args.get("job"), row);
      throw e;
    } finally {
      destinations.remove(args.get("target_ref"));
    }
  }

  public void cancel() {
    workflows.cancelActive();
  }

  public void close() {
    dispatcher.close();
  }

  public static String utf8(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      throw new Failure("INVALID_UTF8");
    }
  }
}
