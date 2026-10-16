package fr.jarvis.services.android;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import fr.jarvis.services.adapters.llm.LlmPort;
import fr.jarvis.services.modules.actions.Actions;
import fr.jarvis.services.modules.bridge.Bridge;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.api.Runtime;
import fr.jarvis.services.shared.tasks.Task;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LocalQualificationTest {
  @Rule public TestName testName = new TestName();
  private Context context;
  private Path root;
  private AndroidDatabase database;
  private Runtime runtime;
  private Guard.Context owner;
  private long started;

  private Task task() {
    return new Task(90000);
  }

  private void open() {
    database = new AndroidDatabase(root.resolve("services.sqlite").toString());
    runtime =
        new Runtime(root.resolve("documents"), database, new AndroidPdfText(), new LlmPort.Mock());
  }

  @Before
  public void before() throws Exception {
    started = SystemClock.elapsedRealtime();
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    PDFBoxResourceLoader.init(context);
    root = Files.createTempDirectory(context.getCacheDir().toPath(), "qualification-");
    open();
    owner =
        new Guard.Context(
            "synthetic-local-user",
            new HashSet<>(
                Arrays.asList(
                    "confirm",
                    "documents.import",
                    "documents.read",
                    "documents.export",
                    "knowledge.read",
                    "knowledge.write",
                    "memory.read",
                    "memory.write",
                    "actions.run",
                    "analyst.read",
                    "bridge.discover",
                    "bridge.peer:simulator-local",
                    "diagnostics.read",
                    "storage.backup",
                    "storage.restore")));
  }

  @After
  public void after() throws Exception {
    Bundle metrics = new Bundle();
    metrics.putString("metric_scenario", testName.getMethodName());
    metrics.putLong("metric_duration_ms", SystemClock.elapsedRealtime() - started);
    metrics.putLong("metric_pss_kib", Debug.getPss());
    try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
      String line;
      while ((line = reader.readLine()) != null)
        if (line.startsWith("VmHWM:")) metrics.putString("metric_vm_hwm", line.substring(6).trim());
    }
    InstrumentationRegistry.getInstrumentation().sendStatus(2, metrics);
    if (database != null) database.close();
    if (root != null)
      try (var paths = Files.walk(root)) {
        paths
            .sorted(Comparator.reverseOrder())
            .forEach(
                f -> {
                  try {
                    Files.delete(f);
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                });
      }
  }

  private void failure(String code, Runnable action) {
    try {
      action.run();
      fail("Expected " + code);
    } catch (Failure error) {
      assertEquals(code, error.code);
    }
  }

  private byte[] bytes(String name) throws IOException {
    try (InputStream in =
            InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int n;
      while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
      return out.toByteArray();
    }
  }

  @Test
  public void nativePdfFtsAndNonDestructiveDocx() throws Exception {
    byte[] pdf = bytes("sample.pdf");
    Map<String, Object> document = runtime.documents.importBytes(owner, "sample.pdf", pdf, task());
    assertTrue(((String) document.get("text")).contains("JARVIS_002A_PRIVATE_SYNTHETIC"));
    runtime.knowledge.index(owner, document, task());
    assertEquals(
        document.get("id"), runtime.knowledge.lexical(owner, "evidence", 5).get(0).get("id"));
    Path original = root.resolve("documents").resolve((String) document.get("original"));
    String hash = Guard.digest(Files.readAllBytes(original));
    runtime.documents.export(owner, "report.docx", "docx", "Synthetic report", task());
    assertTrue(Files.size(root.resolve("documents/report.docx")) > 0);
    byte[] docx = Files.readAllBytes(root.resolve("documents/report.docx"));
    fr.jarvis.services.modules.documents.Documents.readDocx(docx);
    assertTrue(
        ((String) runtime.documents.importBytes(owner, "report.docx", docx, task()).get("text"))
            .contains("Synthetic report"));
    failure(
        "WOULD_OVERWRITE",
        () -> runtime.documents.export(owner, "report.docx", "docx", "Replacement", task()));
    assertEquals(hash, Guard.digest(Files.readAllBytes(original)));
  }

  @Test
  public void exactCsvAndMemoryAfterReopen() {
    assertEquals(
        "0.3",
        runtime
            .data
            .csv("name,value\na,0.1\na,0.1\nb,0.2")
            .deduplicate()
            .stats("value")
            .get("sum"));
    long expires = System.currentTimeMillis() + 600000;
    String exact =
        runtime.memory.exact(
            "test-preference", "preference", "ECO synthetic", "qualification", expires);
    runtime.memory.save(
        owner,
        "test-preference",
        "preference",
        "ECO synthetic",
        "qualification",
        expires,
        runtime.guard.confirm(owner, "memory.write", exact));
    runtime.memory.temporary(owner, "volatile", "not persistent");
    database.close();
    open();
    assertEquals(1, runtime.memory.search(owner, "ECO synthetic").size());
    assertNull(runtime.memory.temporary(owner, "volatile"));
  }

  @Test
  public void guardAndWorkflowFailureAreSafe() throws Exception {
    Guard.Context denied = new Guard.Context("untrusted", Collections.emptySet());
    failure(
        "UNAUTHORIZED",
        () -> runtime.documents.export(denied, "forbidden.txt", "txt", "x", task()));
    failure(
        "UNSAFE_PATH", () -> runtime.documents.export(owner, "../escape.txt", "txt", "x", task()));
    Map<String, Object> proposal =
        runtime.actions.propose(
            owner,
            Collections.singletonList(
                new Actions.Step("document.export", "confirmed.txt", "txt", "synthetic")));
    String id = (String) proposal.get("id");
    failure("CONFIRMATION_REQUIRED", () -> runtime.actions.run(owner, id, null, task()));
    Task cancelled = task();
    cancelled.cancel();
    failure(
        "CANCELLED",
        () ->
            runtime.actions.run(
                owner,
                id,
                runtime.guard.confirm(owner, "actions.run", runtime.actions.exact(owner, id)),
                cancelled));
    assertFalse(Files.exists(root.resolve("documents/confirmed.txt")));
    assertEquals("cancelled", runtime.actions.state(owner, id).get("status"));
  }

  @Test
  public void isolatedRecoveryPreservesIntegrity() throws Exception {
    Map<String, Object> d =
        runtime.documents.importBytes(
            owner, "source.txt", "isolated fixture".getBytes(StandardCharsets.UTF_8), task());
    Path original = root.resolve("documents").resolve((String) d.get("original"));
    String backup = runtime.recovery.backup(owner, task());
    Files.delete(original);
    runtime.recovery.restore(
        owner,
        backup,
        runtime.guard.confirm(owner, "storage.restore", Guard.digest(backup)),
        task());
    assertEquals(d.get("sha256"), Guard.digest(Files.readAllBytes(original)));
    assertEquals("ok", runtime.diagnostics.report(owner).get("storage_integrity"));
  }

  @Test
  public void bridgeSimulatorDisconnectsSafely() {
    String id = UUID.randomUUID().toString();
    assertEquals(
        "echo",
        runtime.bridge.send(
                owner, new Bridge.Message(id, "simulator-local", "echo", "echo"), task())
            .payload);
    runtime.bridge.disconnect(owner, "simulator-local");
    failure(
        "DISCONNECTED",
        () ->
            runtime.bridge.send(
                owner, new Bridge.Message(id, "simulator-local", "echo", "echo"), task()));
  }

  @Test
  public void resolverImportsPdfAndExportsPrivateFreeReport() throws Exception {
    DocumentAccess.Imported source =
        DocumentAccess.read(
            context.getContentResolver(), SyntheticDocumentProvider.PDF, 512 * 1024, task());
    assertEquals("sample.pdf", source.name);
    runtime.documents.importBytes(owner, source.name, source.bytes, task());
    DocumentAccess.writeTechnicalReport(
        context.getContentResolver(),
        SyntheticDocumentProvider.REPORT,
        runtime.diagnostics,
        owner,
        task());
    DocumentAccess.Imported report =
        DocumentAccess.read(
            context.getContentResolver(), SyntheticDocumentProvider.REPORT, 512 * 1024, task());
    String text = new String(report.bytes, StandardCharsets.UTF_8);
    assertTrue(text.contains("schema_version"));
    assertFalse(text.contains("JARVIS_002A_PRIVATE_SYNTHETIC"));
    assertFalse(text.contains("sample.pdf"));
  }

  @Test
  public void resolverRejectsUnboundedCancelledAndFileUris() throws Exception {
    try {
      DocumentAccess.read(context.getContentResolver(), SyntheticDocumentProvider.PDF, 1, task());
      fail("Expected bound");
    } catch (Failure e) {
      assertEquals("INPUT_TOO_LARGE", e.code);
    }
    Task cancelled = task();
    cancelled.cancel();
    try {
      DocumentAccess.read(
          context.getContentResolver(), SyntheticDocumentProvider.PDF, 512 * 1024, cancelled);
      fail("Expected cancel");
    } catch (Failure e) {
      assertEquals("CANCELLED", e.code);
    }
    try {
      DocumentAccess.read(
          context.getContentResolver(), android.net.Uri.parse("file:///etc/passwd"), 100, task());
      fail("Expected URI refusal");
    } catch (Failure e) {
      assertEquals("INVALID_URI", e.code);
    }
  }

  @Test
  public void nativeDocxRejectsDtdForUtf8AndUtf16() throws Exception {
    for (java.nio.charset.Charset charset :
        new java.nio.charset.Charset[] {StandardCharsets.UTF_8, StandardCharsets.UTF_16}) {
      String xml =
          "<?xml version=\"1.0\" encoding=\""
              + charset.name()
              + "\"?><!DOCTYPE w:document [<!ENTITY x SYSTEM 'file:///never-read'>]><w:document"
              + " xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:p>&x;</w:p></w:document>";
      ByteArrayOutputStream archive = new ByteArrayOutputStream();
      try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(archive)) {
        zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
        zip.write(xml.getBytes(charset));
        zip.closeEntry();
      }
      try {
        fr.jarvis.services.modules.documents.Documents.readDocx(archive.toByteArray());
        fail("DTD must be refused");
      } catch (org.xml.sax.SAXException error) {
        assertTrue(error.getMessage().contains("DTD_FORBIDDEN"));
      }
    }
  }
}
