package fr.jarvis.services.android.adapter;

import static org.junit.Assert.*;

import android.app.UiAutomation;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import fr.jarvis.services.api.proposal.CommitAuthorization;
import fr.jarvis.services.modules.documents.DocumentContent;
import fr.jarvis.services.modules.guard.AccessGuard;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FusionSafInstrumentedTest {
  Context context;
  UiAutomation ui;

  @Before
  public void setup() throws Exception {
    context = InstrumentationRegistry.getInstrumentation().getContext();
    ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    int providerUid =
        context.getPackageManager().getApplicationInfo("fr.jarvis.services.fixture", 0).uid;
    assertNotEquals(
        "SAF provider must have a distinct UID", android.os.Process.myUid(), providerUid);
    Bundle metrics = new Bundle();
    metrics.putString("worker_uid", String.valueOf(android.os.Process.myUid()));
    metrics.putString("fixture_provider_uid", String.valueOf(providerUid));
    InstrumentationRegistry.getInstrumentation().sendStatus(2, metrics);
    setMode("normal");
    context.deleteDatabase("fusion_lab_exports.db");
  }

  // Lab host authority, not CORE or SERVICES production permissions.
  static final class Authority implements CommitAuthorization {
    long generation;
    boolean cancelled;
    final Set<String> used = new HashSet<>();

    static final class Token implements ConsentToken {
      final Preview preview;

      Token(Preview p) {
        preview = p;
      }
    }

    synchronized Token confirm(Preview p) {
      return new Token(p);
    }

    synchronized void revoke() {
      generation++;
    }

    synchronized void cancel() {
      cancelled = true;
    }

    public <T> T authorizeAtCommit(Preview p, ConsentToken t, Callable<T> effect) throws Exception {
      synchronized (this) {
        if (!(t instanceof Token)
            || !((Token) t).preview.equals(p)
            || generation != p.generation
            || cancelled
            || System.nanoTime() >= p.deadlineNanos
            || !used.add(p.request)) throw new SecurityException("REFUSED");
      }
      return effect.call();
    }
  }

  CommitAuthorization.Preview preview(
      Uri source, Uri tree, String name, byte[] bytes, LabSafExporter e) throws Exception {
    return new CommitAuthorization.Preview(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        0,
        name.endsWith(".csv") ? "export.csv" : "export.docx",
        AccessGuard.digest(e.read(source)),
        AccessGuard.digest(bytes),
        tree + "/" + name,
        System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
  }

  @Test
  public void csvRealSafExportExactSumAndOriginalIntact() throws Exception {
    Uri source = pick(false, "source.csv"), tree = pick(true, null);
    try (LabSafExporter e = new LabSafExporter(context)) {
      byte[] original = e.read(source);
      ServicesAndroidToolPort port =
          new ServicesAndroidToolPort(context, "fusion-lab", Collections.singleton("data.read"));
      byte[] bytes;
      try {
        ToolInvocation i =
            new ToolInvocation(
                UUID.randomUUID().toString(),
                "data.csv.normalize",
                "1.0.0",
                Collections.singletonMap("csv", new String(original, StandardCharsets.UTF_8)),
                10000,
                null);
        ToolDispatcher.Result r = port.dispatch(i).result.get(10, TimeUnit.SECONDS);
        assertEquals(ToolDispatcher.Status.SUCCESS, r.status);
        bytes = ((String) r.value).getBytes(StandardCharsets.UTF_8);
        BigDecimal sum = BigDecimal.ZERO;
        for (String row : new String(bytes, StandardCharsets.UTF_8).split("\\r?\\n"))
          if (row.contains("0."))
            sum =
                sum.add(new BigDecimal(row.substring(row.lastIndexOf(',') + 1).replace("\"", "")));
        assertEquals(new BigDecimal("0.30"), sum);
      } finally {
        port.closeAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
      }
      String name = "result-" + UUID.randomUUID() + ".csv";
      CommitAuthorization.Preview p = preview(source, tree, name, bytes, e);
      Authority a = new Authority();
      Uri dest = Uri.parse(e.export(p, a.confirm(p), a, source, tree, name, bytes));
      assertArrayEquals(bytes, e.read(dest));
      assertArrayEquals(original, e.read(source));
      assertEquals("COMMITTED", e.state(p.request));
      try {
        e.export(p, a.confirm(p), a, source, tree, name, bytes);
        fail("replay");
      } catch (SecurityException expected) {
      }
    }
  }

  @Test
  public void docxNoConsentNoCreationThenConfirmedReadback() throws Exception {
    Uri source = pick(false, "source.txt"), tree = pick(true, null);
    try (LabSafExporter e = new LabSafExporter(context)) {
      byte[] original = e.read(source),
          bytes = DocumentContent.docx("FUSION24 exact synthetic conclusion");
      String name = "result-" + UUID.randomUUID() + ".docx";
      CommitAuthorization.Preview p = preview(source, tree, name, bytes, e);
      Authority a = new Authority();
      int before = count(tree);
      try {
        e.export(p, null, a, source, tree, name, bytes);
        fail("consent");
      } catch (SecurityException expected) {
      }
      assertEquals(before, count(tree));
      assertEquals("REFUSED", e.state(p.request));
      p = preview(source, tree, name, bytes, e);
      Uri dest = Uri.parse(e.export(p, a.confirm(p), a, source, tree, name, bytes));
      assertArrayEquals(bytes, e.read(dest));
      assertTrue(DocumentContent.readDocx(e.read(dest)).contains("FUSION24"));
      assertArrayEquals(original, e.read(source));
    }
  }

  @Test
  public void deceptiveAckUnknownAndNoRetryAfterReopen() throws Exception {
    Uri source = pick(false, "source.txt"), tree = pick(true, null);
    String id;
    try (LabSafExporter e = new LabSafExporter(context)) {
      byte[] bytes = DocumentContent.docx("synthetic ACK loss");
      String name = "result-" + UUID.randomUUID() + ".docx";
      CommitAuthorization.Preview p = preview(source, tree, name, bytes, e);
      id = p.request;
      Authority a = new Authority();
      setMode("corrupt");
      try {
        e.export(p, a.confirm(p), a, source, tree, name, bytes);
        fail("fake ACK");
      } catch (java.io.IOException expected) {
      }
      assertEquals("UNKNOWN", e.state(id));
      setMode("normal");
      try {
        e.export(p, a.confirm(p), a, source, tree, name, bytes);
        fail("retry");
      } catch (SecurityException expected) {
      }
    }
    try (LabSafExporter e = new LabSafExporter(context)) {
      assertEquals("UNKNOWN", e.state(id));
    }
  }

  @Test
  public void abandonedCommitAndJournalQuotaFailClosed() throws Exception {
    String id = UUID.randomUUID().toString();
    try (LabSafExporter e = new LabSafExporter(context)) {
      e.abandoned(id);
    }
    try (LabSafExporter e = new LabSafExporter(context)) {
      assertEquals("UNKNOWN", e.state(id));
    }
    Uri source = pick(false, "source.txt"), tree = pick(true, null);
    try (LabSafExporter e = new LabSafExporter(context)) {
      for (int i = 1; i < LabSafExporter.LIMIT; i++) e.abandoned(UUID.randomUUID().toString());
      byte[] bytes = DocumentContent.docx("quota");
      String name = "result-" + UUID.randomUUID() + ".docx";
      CommitAuthorization.Preview p = preview(source, tree, name, bytes, e);
      Authority a = new Authority();
      int before = count(tree);
      try {
        e.export(p, a.confirm(p), a, source, tree, name, bytes);
        fail("quota");
      } catch (java.io.IOException expected) {
        assertEquals("JOURNAL_FULL", expected.getMessage());
      }
      assertEquals(before, count(tree));
    }
  }

  @Test
  public void realTxtAndTwoPagePdfViaPort() throws Exception {
    Uri text = pick(false, "source.txt"), pdf = pick(false, "sample.pdf");
    ServicesAndroidToolPort port =
        new ServicesAndroidToolPort(
            context, "fusion-doc-lab", Collections.singleton("documents.read"));
    try {
      assertEquals(64, port.selectSource(text, new Task(10000)).length());
      String pdfDigest = port.selectSource(pdf, new Task(10000));
      assertEquals(64, pdfDigest.length());
      try (java.io.InputStream in = context.getContentResolver().openInputStream(pdf);
          com.tom_roush.pdfbox.pdmodel.PDDocument doc =
              com.tom_roush.pdfbox.pdmodel.PDDocument.load(in)) {
        assertEquals(2, doc.getNumberOfPages());
      }
      ToolInvocation i =
          new ToolInvocation(
              UUID.randomUUID().toString(),
              "documents.search",
              "1.0.0",
              Collections.singletonMap("query", "FUSION24"),
              10000,
              null);
      ToolDispatcher.Result r = port.dispatch(i).result.get(10, TimeUnit.SECONDS);
      assertEquals(ToolDispatcher.Status.SUCCESS, r.status);
      java.util.List<fr.jarvis.services.pipeline.EvidenceReportBuilder.Citation> hits =
          (java.util.List<fr.jarvis.services.pipeline.EvidenceReportBuilder.Citation>) r.value;
      assertEquals(3, hits.size());
      for (fr.jarvis.services.pipeline.EvidenceReportBuilder.Citation hit : hits) {
        assertTrue(hit.quote.startsWith("FUSION24"));
        assertEquals(0, hit.start);
        assertEquals(64, hit.textSha256.length());
      }
      assertEquals(2, hits.stream().filter(h -> h.sourceSha256.equals(pdfDigest)).count());
    } finally {
      port.closeAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
  }

  @Test
  public void revokeAtBarrierBeforeCommitHasNoEffect() throws Exception {
    Uri source = pick(false, "source.txt"), tree = pick(true, null);
    try (LabSafExporter e = new LabSafExporter(context)) {
      byte[] bytes = DocumentContent.docx("revoked");
      String name = "result-" + UUID.randomUUID() + ".docx";
      CommitAuthorization.Preview p = preview(source, tree, name, bytes, e);
      Authority a = new Authority();
      Authority.Token t = a.confirm(p);
      CountDownLatch ready = new CountDownLatch(1), go = new CountDownLatch(1);
      ExecutorService pool = Executors.newSingleThreadExecutor();
      int before = count(tree);
      try {
        Future<Boolean> f =
            pool.submit(
                () -> {
                  ready.countDown();
                  if (!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("barrier");
                  try {
                    e.export(p, t, a, source, tree, name, bytes);
                    return false;
                  } catch (SecurityException expected) {
                    return true;
                  }
                });
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        a.revoke();
        go.countDown();
        assertTrue(f.get(10, TimeUnit.SECONDS));
        assertEquals(before, count(tree));
        assertEquals("REFUSED", e.state(p.request));
      } finally {
        go.countDown();
        pool.shutdownNow();
      }
    }
  }

  @Test
  public void revokeAfterCommitReportsCommittedRatherThanRollback() throws Exception {
    Uri source = pick(false, "source.txt"), tree = pick(true, null);
    try (LabSafExporter e = new LabSafExporter(context)) {
      byte[] bytes = DocumentContent.docx("committed then revoked");
      String name = "result-" + UUID.randomUUID() + ".docx";
      CommitAuthorization.Preview p = preview(source, tree, name, bytes, e);
      Authority a = new Authority();
      Authority.Token t = a.confirm(p);
      CountDownLatch started = new CountDownLatch(1), go = new CountDownLatch(1);
      ExecutorService pool = Executors.newSingleThreadExecutor();
      CommitAuthorization fence =
          new CommitAuthorization() {
            public <T> T authorizeAtCommit(Preview v, ConsentToken c, Callable<T> effect)
                throws Exception {
              return a.authorizeAtCommit(
                  v,
                  c,
                  () -> {
                    started.countDown();
                    if (!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("barrier");
                    return effect.call();
                  });
            }
          };
      try {
        Future<String> f = pool.submit(() -> e.export(p, t, fence, source, tree, name, bytes));
        assertTrue(started.await(5, TimeUnit.SECONDS));
        a.revoke();
        go.countDown();
        Uri dest = Uri.parse(f.get(10, TimeUnit.SECONDS));
        assertArrayEquals(bytes, e.read(dest));
        assertEquals("COMMITTED", e.state(p.request));
      } finally {
        go.countDown();
        pool.shutdownNow();
      }
    }
  }

  @Test
  public void maliciousUriAndProviderDenialHaveNoEffect() throws Exception {
    Uri source = pick(false, "source.txt"), tree = pick(true, null);
    try (LabSafExporter e = new LabSafExporter(context)) {
      byte[] bytes = DocumentContent.docx("denied");
      String name = "result-" + UUID.randomUUID() + ".docx";
      CommitAuthorization.Preview p = preview(source, tree, name, bytes, e);
      Authority a = new Authority();
      int before = count(tree);
      try {
        e.export(p, a.confirm(p), a, Uri.parse("file:///private/original"), tree, name, bytes);
        fail("unsafe URI");
      } catch (SecurityException expected) {
      }
      setMode("deny");
      try {
        e.export(p, a.confirm(p), a, source, tree, name, bytes);
        fail("provider denial");
      } catch (java.io.FileNotFoundException expected) {
      }
      setMode("normal");
      assertEquals(before, count(tree));
      assertNull(e.state(p.request));
    }
  }

  @Test
  public void lostAuthorityAckIsUnknownEvenWhenBytesCommitted() throws Exception {
    Uri source = pick(false, "source.txt"), tree = pick(true, null);
    try (LabSafExporter e = new LabSafExporter(context)) {
      byte[] bytes = DocumentContent.docx("lost ACK");
      String name = "result-" + UUID.randomUUID() + ".docx";
      CommitAuthorization.Preview p = preview(source, tree, name, bytes, e);
      Authority a = new Authority();
      CommitAuthorization lost =
          new CommitAuthorization() {
            public <T> T authorizeAtCommit(Preview v, ConsentToken c, Callable<T> effect)
                throws Exception {
              a.authorizeAtCommit(v, c, effect);
              throw new java.io.IOException("ACK_LOST");
            }
          };
      int before = count(tree);
      try {
        e.export(p, a.confirm(p), lost, source, tree, name, bytes);
        fail("lost ACK");
      } catch (java.io.IOException expected) {
      }
      assertEquals(before + 1, count(tree));
      assertEquals("UNKNOWN", e.state(p.request));
      try {
        e.export(p, a.confirm(p), a, source, tree, name, bytes);
        fail("retry");
      } catch (SecurityException expected) {
      }
    }
  }

  @Test
  public void cancellationAndSecondWorkerRefused() throws Exception {
    Uri source = pick(false, "source.txt"), tree = pick(true, null);
    try (LabSafExporter e = new LabSafExporter(context)) {
      try (LabSafExporter second = new LabSafExporter(context)) {
        fail("second worker");
      } catch (java.nio.channels.OverlappingFileLockException expected) {
      }
      byte[] bytes = DocumentContent.docx("cancelled");
      String name = "result-" + UUID.randomUUID() + ".docx";
      CommitAuthorization.Preview p = preview(source, tree, name, bytes, e);
      Authority a = new Authority();
      Authority.Token t = a.confirm(p);
      a.cancel();
      int before = count(tree);
      try {
        e.export(p, t, a, source, tree, name, bytes);
        fail("cancelled");
      } catch (SecurityException expected) {
      }
      assertEquals(before, count(tree));
      assertEquals("REFUSED", e.state(p.request));
    }
  }

  @Test
  public void corruptLabJournalRefusesWithoutDeletingEvidenceOrLeakingLease() throws Exception {
    java.io.File path = context.getDatabasePath("fusion_lab_exports.db");
    path.getParentFile().mkdirs();
    byte[] corrupt = "synthetic corrupted SQLite bytes".getBytes(StandardCharsets.UTF_8);
    try (java.io.OutputStream out = new java.io.FileOutputStream(path)) {
      out.write(corrupt);
    }
    for (int i = 0; i < 2; i++) {
      try (LabSafExporter e = new LabSafExporter(context)) {
        fail("corrupt journal accepted");
      } catch (android.database.sqlite.SQLiteException expected) {
      }
      try (java.io.InputStream in = new java.io.FileInputStream(path)) {
        byte[] read = new byte[corrupt.length];
        assertEquals(read.length, in.read(read));
        assertArrayEquals(corrupt, read);
      }
    }
  }

  @Test
  public void portablePermissionsAndUnqualifiedExportStillRefuse() throws Exception {
    ServicesAndroidToolPort port =
        new ServicesAndroidToolPort(context, "denied-native", Collections.emptySet());
    try {
      ToolInvocation i =
          new ToolInvocation(
              UUID.randomUUID().toString(),
              "data.csv.normalize",
              "1.0.0",
              Collections.singletonMap("csv", "a\n1\n"),
              10000,
              null);
      assertEquals(
          ToolDispatcher.Status.REFUSED, port.dispatch(i).result.get(10, TimeUnit.SECONDS).status);
      try {
        port.exportDocx(i);
        fail("export without grant");
      } catch (fr.jarvis.services.shared.api.Contracts.Failure expected) {
      }
    } finally {
      port.closeAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
    port =
        new ServicesAndroidToolPort(
            context, "unqualified-native", Collections.singleton("documents.export"));
    try {
      try {
        port.exportDocx(null);
        fail("runtime export enabled");
      } catch (fr.jarvis.services.shared.api.Contracts.Failure expected) {
        assertEquals("SAF_EXPORT_UNQUALIFIED", expected.code);
      }
    } finally {
      port.closeAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
  }

  @Test
  public void runtimeJournalCorruptionPreservesEvidenceAndReleasesLease() throws Exception {
    String name = "jarvis_serv04a_journal_v1.db";
    context.deleteDatabase(name);
    java.io.File path = context.getDatabasePath(name);
    path.getParentFile().mkdirs();
    byte[] corrupt = "synthetic corrupt SDK journal".getBytes(StandardCharsets.UTF_8);
    try (java.io.OutputStream out = new java.io.FileOutputStream(path)) {
      out.write(corrupt);
    }
    try {
      for (int i = 0; i < 2; i++) {
        try {
          JournalDatabase d = new JournalDatabase(context);
          d.close();
          fail("corrupt accepted");
        } catch (RuntimeException expected) {
          if (expected instanceof fr.jarvis.services.shared.api.Contracts.Failure)
            assertEquals(
                "CORRUPT_JOURNAL_NO_RESET",
                ((fr.jarvis.services.shared.api.Contracts.Failure) expected).code);
          else assertTrue(expected instanceof android.database.sqlite.SQLiteException);
        }
        try (java.io.InputStream in = new java.io.FileInputStream(path)) {
          byte[] b = new byte[corrupt.length];
          assertEquals(b.length, in.read(b));
          assertArrayEquals(corrupt, b);
        }
      }
    } finally {
      context.deleteDatabase(name);
    }
  }

  @Test
  public void realPersistedGrantRevocationAndHostFenceRefuse() throws Exception {
    Uri source = pick(false, "source.txt"), tree = pick(true, null);
    assertTrue(
        context.getContentResolver().getPersistedUriPermissions().stream()
            .anyMatch(g -> g.getUri().equals(tree) && g.isWritePermission()));
    try (LabSafExporter e = new LabSafExporter(context)) {
      byte[] bytes = DocumentContent.docx("revoked grant");
      String name = "result-" + UUID.randomUUID() + ".docx";
      CommitAuthorization.Preview p = preview(source, tree, name, bytes, e);
      Authority a = new Authority();
      Authority.Token token = a.confirm(p);
      int before = count(tree);
      revokeGrant(tree);
      assertEquals(
          android.content.pm.PackageManager.PERMISSION_DENIED,
          context.checkUriPermission(
              tree,
              android.os.Process.myPid(),
              android.os.Process.myUid(),
              Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
      a.revoke();
      try {
        e.export(p, token, a, source, tree, name, bytes);
        fail("revoked before commit");
      } catch (SecurityException expected) {
      }
      assertEquals("REFUSED", e.state(p.request));
      Uri explicitlyRegranted = pick(true, null);
      assertEquals(before, count(explicitlyRegranted));
      revokeGrant(source);
      ServicesAndroidToolPort port =
          new ServicesAndroidToolPort(
              context, "revoked-source-native", Collections.singleton("documents.read"));
      try {
        try {
          port.selectSource(source, new Task(10000));
          fail("revoked source");
        } catch (fr.jarvis.services.shared.api.Contracts.Failure expected) {
          assertEquals("URI_DENIED", expected.code);
        }
      } finally {
        port.closeAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
      }
    }
  }

  void setMode(String mode) throws Exception {
    labControl(new Intent("fr.jarvis.services.fixture.MODE").putExtra("mode", mode));
  }

  void revokeGrant(Uri uri) throws Exception {
    labControl(new Intent("fr.jarvis.services.fixture.REVOKE").putExtra("uri", uri.toString()));
  }

  void labControl(Intent control) throws Exception {
    CountDownLatch ack = new CountDownLatch(1);
    java.util.concurrent.atomic.AtomicInteger result =
        new java.util.concurrent.atomic.AtomicInteger();
    control.setComponent(
        new ComponentName(
            "fr.jarvis.services.fixture", "fr.jarvis.services.fixture.FixtureControlReceiver"));
    context.sendOrderedBroadcast(
        control,
        null,
        new BroadcastReceiver() {
          public void onReceive(Context c, Intent i) {
            result.set(getResultCode());
            ack.countDown();
          }
        },
        new Handler(Looper.getMainLooper()),
        0,
        null,
        null);
    assertTrue("Signature-protected lab control ACK", ack.await(10, TimeUnit.SECONDS));
    assertEquals(android.app.Activity.RESULT_OK, result.get());
  }

  int count(Uri tree) throws Exception {
    Uri children =
        android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, android.provider.DocumentsContract.getTreeDocumentId(tree));
    try (android.database.Cursor c =
        context
            .getContentResolver()
            .query(children, new String[] {"document_id"}, null, null, null)) {
      assertNotNull(c);
      return c.getCount();
    }
  }

  Uri pick(boolean tree, String name) throws Exception {
    FusionPickerActivity.results.clear();
    Intent i =
        new Intent(context, FusionPickerActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("tree", tree);
    context.startActivity(i);
    SystemClock.sleep(800);
    clickAny(new String[] {"Show roots", "Open navigation drawer"}, 12000);
    clickAny(new String[] {"FUSION fixtures"}, 12000);
    // Exercise the supported DocumentsUI list UI consistently across API levels.
    if (find(ui.getRootInActiveWindow(), "List view") != null)
      clickAny(new String[] {"List view"}, 12000);
    SystemClock.sleep(500);
    if (tree)
      clickAny(new String[] {"SELECT", "Select", "USE THIS FOLDER", "Use this folder"}, 12000);
    else clickAny(new String[] {name}, 12000);
    Uri uri = null;
    long selectionDeadline = SystemClock.uptimeMillis() + 15000;
    while (uri == null && SystemClock.uptimeMillis() < selectionDeadline) {
      uri = FusionPickerActivity.results.poll(200, TimeUnit.MILLISECONDS);
      // Newer DocumentsUI versions require an additional explicit grant dialog.
      if (uri == null && tree && find(ui.getRootInActiveWindow(), "Allow") != null)
        clickAny(new String[] {"Allow"}, 5000);
    }
    assertNotNull("SAF selection; UI=" + texts(ui.getRootInActiveWindow()), uri);
    return uri;
  }

  void clickAny(String[] labels, long timeout) {
    long end = SystemClock.uptimeMillis() + timeout;
    while (SystemClock.uptimeMillis() < end) {
      for (String label : labels) {
        AccessibilityNodeInfo n = find(ui.getRootInActiveWindow(), label);
        if (n == null) continue;
        if (!label.contains(".")) {
          AccessibilityNodeInfo p = n;
          for (int a = 0; p != null && a < 4; a++, p = p.getParent())
            if (p.isClickable() && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
              SystemClock.sleep(200);
              return;
            }
        }
        n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
        SystemClock.sleep(100);
        if (!n.refresh()) continue;
        // Activate the nearest focusable document row/card with the real platform
        // keyboard path. Grid and list have different nameplate ancestry on API24.
        if (label.contains(".")) {
          AccessibilityNodeInfo row = n;
          for (int depth = 0; row != null && depth < 5; depth++, row = row.getParent()) {
            if (row.isScrollable()) break;
            if (row.isFocusable() && row.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
              boolean accepted =
                  ui.injectInputEvent(
                          new android.view.KeyEvent(
                              SystemClock.uptimeMillis(),
                              SystemClock.uptimeMillis(),
                              android.view.KeyEvent.ACTION_DOWN,
                              android.view.KeyEvent.KEYCODE_ENTER,
                              0),
                          true)
                      && ui.injectInputEvent(
                          new android.view.KeyEvent(
                              SystemClock.uptimeMillis(),
                              SystemClock.uptimeMillis(),
                              android.view.KeyEvent.ACTION_UP,
                              android.view.KeyEvent.KEYCODE_ENTER,
                              0),
                          true);
              if (accepted) {
                SystemClock.sleep(300);
                return;
              }
            }
          }
        }
        android.graphics.Rect b = new android.graphics.Rect();
        n.getBoundsInScreen(b);
        long now = SystemClock.uptimeMillis();
        if (!b.isEmpty()
            && b.centerY() > 0
            && b.centerY() < context.getResources().getDisplayMetrics().heightPixels) {
          android.view.MotionEvent
              d = android.view.MotionEvent.obtain(now, now, 0, b.centerX(), b.centerY(), 0),
              u = android.view.MotionEvent.obtain(now, now + 80, 1, b.centerX(), b.centerY(), 0);
          d.setSource(4098);
          u.setSource(4098);
          boolean ok = ui.injectInputEvent(d, true) && ui.injectInputEvent(u, true);
          d.recycle();
          u.recycle();
          if (ok) {
            SystemClock.sleep(200);
            return;
          }
        }
      }
      SystemClock.sleep(150);
    }
    throw new AssertionError("Missing SAF control " + Arrays.toString(labels));
  }

  String texts(AccessibilityNodeInfo n) {
    if (n == null) return "";
    StringBuilder s = new StringBuilder();
    if (n.getText() != null) s.append(n.getText()).append(";");
    if (n.getContentDescription() != null) s.append(n.getContentDescription()).append(";");
    for (int i = 0; i < n.getChildCount(); i++) s.append(texts(n.getChild(i)));
    return s.toString();
  }

  AccessibilityNodeInfo find(AccessibilityNodeInfo n, String label) {
    if (n == null) return null;
    if (label.equalsIgnoreCase(String.valueOf(n.getText()))
        || label.equalsIgnoreCase(String.valueOf(n.getContentDescription()))) return n;
    for (int i = 0; i < n.getChildCount(); i++) {
      AccessibilityNodeInfo r = find(n.getChild(i), label);
      if (r != null) return r;
    }
    return null;
  }
}
