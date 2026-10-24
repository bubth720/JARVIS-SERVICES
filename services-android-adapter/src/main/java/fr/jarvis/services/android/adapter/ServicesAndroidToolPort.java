package fr.jarvis.services.android.adapter;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import fr.jarvis.services.api.ServicesToolPort;
import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.documents.DocumentContent;
import fr.jarvis.services.modules.guard.AccessGuard;
import fr.jarvis.services.pipeline.DocumentTextExtractor;
import fr.jarvis.services.pipeline.SelectedDocumentSearch;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import java.io.*;
import java.util.*;

/**
 * Android24 in-process SDK. Only the trusted application constructs this port and selects SAF URIs.
 * No Activity, Binder service, permission request, global CORE admission or network is installed.
 * The host must own one session/port and serialize its use with other resource owners.
 */
public final class ServicesAndroidToolPort implements ServicesToolPort {
  private static final Set<String> KNOWN = Collections.unmodifiableSet(new HashSet<>(
      Arrays.asList("documents.read", "data.read", "documents.export", "confirm")));
  private final Context application;
  private final AccessGuard.Context host;
  private final JournalDatabase database;
  private final Store journal;
  private final AccessGuard guard;
  private final ToolRegistry registry;
  private final ApprovalGate approvals;
  private final ToolDispatcher dispatcher;
  private final DocumentTextExtractor extractor;
  private final List<DocumentTextExtractor.Page> selected = new ArrayList<>();
  private final Set<String> sources = new HashSet<>();
  private final java.util.concurrent.CompletableFuture<Void> closedFuture = new java.util.concurrent.CompletableFuture<>();
  private int selectedChars;
  private boolean closed, selecting, workersDrained;

  /** Permissions are issued by host policy, never parsed from document/tool arguments. */
  public ServicesAndroidToolPort(Context context, String principal, Set<String> permissions) {
    if (context == null || permissions == null || !KNOWN.containsAll(permissions))
      throw new Failure("UNKNOWN_PERMISSION");
    application = context.getApplicationContext();
    host = new AccessGuard.Context(principal, permissions);
    database = new JournalDatabase(application);
    try {
      journal = Store.journal(database);
      database.transaction(() -> {
        // A missing final receipt is uncertainty, never permission to retry the same request.
        for (Map<String, Object> row : journal.all("android_requests")) {
          if ("ACCEPTED".equals(row.get("state"))) {
            row.put("state", "UNKNOWN"); journal.put("android_requests", (String) row.get("id"), row);
          }
        }
      });
      guard = new AccessGuard(journal);
      registry = new ToolRegistry(journal);
      approvals = new ApprovalGate(journal, guard);
      PDFBoxResourceLoader.init(application);
      extractor = new DocumentTextExtractor(new AndroidPdfPages());
      Data data = new Data();
      registry.register(descriptor("documents.search", "documents", "documents.read", "document_hits", "query"),
          (ctx, args, task) -> SelectedDocumentSearch.search(snapshot(), args.get("query"), task));
      registry.register(descriptor("data.csv.normalize", "data", "data.read", "csv_table", "csv"),
          (ctx, args, task) -> task.heavy(() -> data.csv(args.get("csv")).normalize().deduplicate().csv()));
      dispatcher = new ToolDispatcher(registry, guard, approvals, result -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", result.requestId); row.put("state", result.status.name());
        journal.put("android_requests", result.requestId, row);
      });
    } catch (RuntimeException | Error failure) {
      try { database.close(); } catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
      throw failure;
    }
  }

  private static ToolDescriptor descriptor(String id, String module, String permission, String output, String field) {
    return new ToolDescriptor(id, "1.0.0", module, permission,
        Collections.singleton(field), output, false, 2 * 1024 * 1024L);
  }
  private synchronized void open() { if (closed) throw new Failure("CLOSED"); }
  private synchronized List<DocumentTextExtractor.Page> snapshot() { return new ArrayList<>(selected); }

  /** Read-only, host-selected SAF document; content lives in memory for this port only. */
  public String selectSource(Uri uri, Task task) {
    beginSelection();
    try {
      guard.require(host, "documents.read");
      if (uri == null || !"content".equals(uri.getScheme())) throw new Failure("INVALID_URI");
      task.check();
      String name;
      try (Cursor cursor = application.getContentResolver().query(uri,
          new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
        if (cursor == null || !cursor.moveToFirst() || cursor.isNull(0)) throw new Failure("SOURCE_NAME_REQUIRED");
        name = cursor.getString(0);
      }
      byte[] bytes;
      try (InputStream input = application.getContentResolver().openInputStream(uri)) {
        if (input == null) throw new Failure("READ_FAILED");
        bytes = readBounded(input, task);
      }
      return task.heavy(() -> {
        List<DocumentTextExtractor.Page> pages = extractor.extract(name, bytes, task);
        String revision = AccessGuard.digest(bytes);
        synchronized (this) {
          open(); task.check();
          if (sources.contains(revision)) throw new Failure("DUPLICATE_SOURCE");
          int chars = 0; for (DocumentTextExtractor.Page page : pages) chars += page.text.length();
          if (sources.size() >= 8 || selected.size() + pages.size() > 100
              || selectedChars + chars > DocumentContent.MAX_TEXT) throw new Failure("SOURCE_LIMIT");
          selected.addAll(pages); sources.add(revision); selectedChars += chars;
        }
        guard.audit("documents.select", "success");
        return revision;
      });
    } catch (SecurityException e) { guard.audit("documents.select", "URI_DENIED"); throw new Failure("URI_DENIED"); }
    catch (IOException e) { guard.audit("documents.select", "READ_FAILED"); throw new Failure("READ_FAILED"); }
    catch (RuntimeException e) { guard.audit("documents.select", "refused"); throw e; }
    finally { endSelection(); }
  }
  private synchronized void beginSelection() {
    open(); if (selecting) throw new Failure("BUSY"); selecting = true;
  }
  private synchronized void endSelection() { selecting = false; finishClosureIfIdle(); }
  private synchronized void finishClosureIfIdle() {
    if (closed && workersDrained && !selecting && !closedFuture.isDone()) {
      try { database.close(); closedFuture.complete(null); }
      catch (RuntimeException failure) { closedFuture.completeExceptionally(failure); }
    }
  }

  private static byte[] readBounded(InputStream input, Task task) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192]; int count;
    while ((count = input.read(buffer)) != -1) {
      task.check();
      if (bytes.size() + count > DocumentContent.MAX_BYTES) throw new Failure("INPUT_TOO_LARGE");
      bytes.write(buffer, 0, count);
    }
    task.check(); return bytes.toByteArray();
  }

  public List<Map<String, Object>> catalogue() { open(); return registry.catalogue(); }
  public synchronized ToolDispatcher.Handle dispatch(ToolInvocation invocation) {
    open();
    if (invocation == null) throw new Failure("INVALID_INPUT");
    try { database.transaction(() -> {
      if (journal.get("android_requests", invocation.requestId) != null) throw new Failure("DUPLICATE_REQUEST");
      if (journal.count("android_requests") >= 4096) throw new Failure("REQUEST_JOURNAL_FULL");
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", invocation.requestId); row.put("state", "ACCEPTED");
      journal.put("android_requests", invocation.requestId, row);
    }); } catch (Failure failure) { guard.audit("tool.dispatch", failure.code); throw failure; }
    return dispatcher.dispatch(host, invocation);
  }

  public String confirm(ToolInvocation invocation) {
    open(); guard.require(host, "confirm");
    return approvals.confirm(host, registry.descriptor(invocation.toolId, invocation.version), invocation);
  }
  /** No provider was qualified for fresh-create/ACK/readback/rollback; never truncate an existing URI. */
  public void exportDocx(ToolInvocation invocation) {
    open(); guard.require(host, "documents.export");
    guard.audit("documents.docx.export", "SAF_EXPORT_UNQUALIFIED");
    throw new Failure("SAF_EXPORT_UNQUALIFIED");
  }
  /** Only non-sensitive request IDs and outcomes; no arguments or source data. */
  public List<Map<String, Object>> receipts() { open(); return journal.all("android_requests"); }
  public Map<String, Object> capabilities() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("version", VERSION); result.put("min_sdk", 24);
    result.put("transport", "trusted_in_process"); result.put("docx_export", "disabled:SAF_EXPORT_UNQUALIFIED");
    result.put("fts", "not_required"); result.put("network", false);
    return Collections.unmodifiableMap(result);
  }
  /** Non-sensitive operational journal only; never source text, CSV, URI or principal. */
  public List<Map<String, Object>> audit() { open(); return journal.all("audit"); }

  /** Nonblocking close. Host may await the returned stage off the UI thread for confirmed DB closure. */
  public java.util.concurrent.CompletionStage<Void> closeAsync() {
    close(); return closedFuture.thenApply(value -> value);
  }
  public synchronized void close() {
    if (closed) return;
    closed = true; selected.clear(); sources.clear(); selectedChars = 0;
    dispatcher.closeWhenDrained(() -> {
      synchronized (ServicesAndroidToolPort.this) { workersDrained = true; finishClosureIfIdle(); }
    });
  }
}
