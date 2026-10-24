package fr.jarvis.services.android;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Drives the real Android DocumentsUI picker and its URI grants, not a mocked ContentResolver. */
@RunWith(AndroidJUnit4.class)
public class FinalSafWorkflowTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context context = instrumentation.getTargetContext();
  private UiAutomation ui;
  private Activity activity;
  private long started;

  @Before
  public void start() {
    ui = instrumentation.getUiAutomation();
    started = System.currentTimeMillis();
    Intent intent = new Intent(context, WorkspaceActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    activity = instrumentation.startActivitySync(intent);
    await("Atelier local prêt — hors ligne", 30000);
  }

  @After
  public void stop() {
    if (activity != null) instrumentation.runOnMainSync(() -> activity.finish());
  }

  @Test
  public void csvUsesActualPickerGrantPreviewConfirmationAndFreshTreeExport() throws Exception {
    click("PDF / consultation sourcée → DOCX");
    click("CSV / JSON → tableau et calculs");
    selectSource("source.csv");
    setText("Colonne à trier ou calculer", "amount");
    click("Préparer et prévisualiser");
    await("removed_rows", 30000);
    assertTrue(allText().contains("0.30"));
    Path output = publishPrivate("csv");
    exportTree();
    String csv = new String(readExport(output.getFileName().toString()), StandardCharsets.UTF_8);
    assertEquals(2, new fr.jarvis.services.modules.data.Data().csv(csv).rows.size());
    assertEquals(
        "0.30", new fr.jarvis.services.modules.data.Data().csv(csv).stats("amount").get("sum"));
    assertEquals(
        "name,amount\n a ,0.10\n a ,0.10\n b ,0.20\n",
        new String(readSource("source.csv"), StandardCharsets.UTF_8));
  }

  @Test
  public void pdfToSourcedDocxReadsBothActualSafInputAndOutput() throws Exception {
    selectSource("sample.pdf");
    byte[] original = readSource("sample.pdf");
    String digest = Guard.digest(original);
    click("Préparer et prévisualiser");
    await(digest, 30000);
    Path output = publishPrivate("docx");
    exportTree();
    String text = Documents.readDocx(readExport(output.getFileName().toString()));
    assertTrue(text.contains(digest));
    assertTrue(text.contains("page : 1"));
    assertEquals(digest, Guard.digest(readSource("sample.pdf")));
  }

  @Test
  public void modelFieldsUseRealDocxAndFreshSafExport() throws Exception {
    click("PDF / consultation sourcée → DOCX");
    click("Remplir un modèle texte / DOCX");
    selectSource("template.docx");
    String original = Guard.digest(readSource("template.docx"));
    click("Préparer et prévisualiser");
    await("Remplir les champs du modèle", 15000);
    setText("name", "Alice & Bob");
    setText("amount", "0.30 EUR");
    click("Préparer et prévisualiser");
    await("0.30 EUR", 20000);
    Path output = publishPrivate("docx");
    exportTree();
    String text = Documents.readDocx(readExport(output.getFileName().toString()));
    assertTrue(text.contains("Bonjour Alice & Bob"));
    assertTrue(text.contains("0.30 EUR"));
    assertFalse(text.contains("{{name}}"));
    assertEquals(original, Guard.digest(readSource("template.docx")));
  }

  private void selectSource(String file) {
    click("Sélectionner un fichier local");
    waitPackage("com.android.documentsui", 30000);
    if (find(file, false) == null && !clickIfPresent("JARVIS FINAL fixtures")) {
      clickAny(
          new String[] {"Show roots", "Afficher les racines", "Open navigation drawer"}, 15000);
      click("JARVIS FINAL fixtures");
    }
    click(file);
    await("Fichier sélectionné : " + file, 30000);
  }

  private Path publishPrivate(String format) throws Exception {
    click("Étape suivante / reprendre");
    await("SUCCEEDED", 15000);
    click("Étape suivante / reprendre");
    await("Créer une nouvelle sortie privée", 15000);
    clickConfirm();
    Path directory = context.getFilesDir().toPath().resolve("workspace/files");
    long deadline = SystemClock.uptimeMillis() + 30000;
    while (SystemClock.uptimeMillis() < deadline) {
      try (java.util.stream.Stream<Path> files = Files.list(directory)) {
        Path p =
            files
                .filter(
                    x ->
                        x.getFileName().toString().startsWith("result-")
                            && x.toString().endsWith("." + format))
                .filter(
                    x -> {
                      try {
                        return Files.getLastModifiedTime(x).toMillis() >= started;
                      } catch (IOException e) {
                        return false;
                      }
                    })
                .findFirst()
                .orElse(null);
        if (p != null) {
          await("Atelier local prêt — hors ligne", 15000);
          return p;
        }
      }
      SystemClock.sleep(200);
    }
    throw new AssertionError("No newly published private output");
  }

  private void exportTree() {
    click("Exporter dans un dossier choisi");
    waitPackage("com.android.documentsui", 30000);
    if (find("source.csv", false) == null
        && find("sample.pdf", false) == null
        && !clickIfPresent("JARVIS FINAL fixtures")) {
      clickAny(
          new String[] {"Show roots", "Afficher les racines", "Open navigation drawer"}, 15000);
      click("JARVIS FINAL fixtures");
    }
    clickAny(
        new String[] {"SELECT", "Select", "Use this folder", "UTILISER CE DOSSIER", "SÉLECTIONNER"},
        15000);
    if (find("Allow", false) != null) click("Allow");
    await("Créer un nouveau fichier dans le dossier sélectionné", 20000);
    clickConfirm();
    await("PUBLISHED", 30000);
  }

  private byte[] readSource(String id) throws Exception {
    return read(DocumentsContract.buildDocumentUri(FinalDocumentsProvider.AUTHORITY, id));
  }

  private byte[] readExport(String name) throws Exception {
    Uri tree = DocumentsContract.buildTreeDocumentUri(FinalDocumentsProvider.AUTHORITY, "root");
    assertEquals(
        android.content.pm.PackageManager.PERMISSION_GRANTED,
        context.checkUriPermission(
            tree,
            android.os.Process.myPid(),
            android.os.Process.myUid(),
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
    Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, "root");
    String id = null;
    try (Cursor c =
        context
            .getContentResolver()
            .query(
                children,
                new String[] {
                  DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                  DocumentsContract.Document.COLUMN_DISPLAY_NAME
                },
                null,
                null,
                null)) {
      assertNotNull(c);
      while (c.moveToNext()) if (name.equals(c.getString(1))) id = c.getString(0);
    }
    assertNotNull("Fresh exported document visible via real tree grant", id);
    return read(DocumentsContract.buildDocumentUriUsingTree(tree, id));
  }

  private byte[] read(Uri uri) throws Exception {
    try (InputStream in = context.getContentResolver().openInputStream(uri);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      assertNotNull(in);
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) {
        assertTrue(out.size() + n <= Documents.MAX_BYTES);
        out.write(b, 0, n);
      }
      return out.toByteArray();
    }
  }

  private void clickConfirm() {
    long deadline = SystemClock.uptimeMillis() + 15000;
    while (SystemClock.uptimeMillis() < deadline) {
      AccessibilityNodeInfo root = ui.getRootInActiveWindow();
      AccessibilityNodeInfo b = findNode(root, "Confirmer cette opération", true);
      if (b != null && b.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
      SystemClock.sleep(100);
    }
    throw new AssertionError("Confirmation button missing");
  }

  private void setText(String hint, String value) {
    AccessibilityNodeInfo n = locate(hint, 15000);
    Bundle args = new Bundle();
    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
    assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));
  }

  private void click(String label) {
    AccessibilityNodeInfo n = locate(label, 20000), target = n;
    for (int i = 0; n != null && i < 4; i++, n = n.getParent())
      if (n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
        SystemClock.sleep(200);
        return;
      }
    if (tap(target)) return;
    throw new AssertionError("Not clickable: " + label);
  }

  private boolean clickIfPresent(String label) {
    AccessibilityNodeInfo n = find(label, false), target = n;
    if (n == null) return false;
    for (int i = 0; n != null && i < 4; i++, n = n.getParent())
      if (n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
        SystemClock.sleep(200);
        return true;
      }
    return tap(target);
  }

  private boolean tap(AccessibilityNodeInfo n) {
    if (n == null) return false;
    n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
    SystemClock.sleep(150);
    if (!n.refresh()) return false;
    android.graphics.Rect bounds = new android.graphics.Rect();
    n.getBoundsInScreen(bounds);
    if (bounds.isEmpty()
        || bounds.centerY() < 0
        || bounds.centerY() >= context.getResources().getDisplayMetrics().heightPixels)
      return false;
    long now = SystemClock.uptimeMillis();
    android.view.MotionEvent down =
        android.view.MotionEvent.obtain(
            now, now, android.view.MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
    down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
    android.view.MotionEvent up =
        android.view.MotionEvent.obtain(
            now,
            now + 80,
            android.view.MotionEvent.ACTION_UP,
            bounds.centerX(),
            bounds.centerY(),
            0);
    up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
    boolean accepted = ui.injectInputEvent(down, true) && ui.injectInputEvent(up, true);
    down.recycle();
    up.recycle();
    SystemClock.sleep(250);
    return accepted;
  }

  private void clickAny(String[] labels, long timeout) {
    long deadline = SystemClock.uptimeMillis() + timeout;
    while (SystemClock.uptimeMillis() < deadline) {
      for (String label : labels) if (clickIfPresent(label)) return;
      SystemClock.sleep(150);
    }
    throw new AssertionError(
        "Missing picker control: " + Arrays.toString(labels) + "; UI=" + allText());
  }

  private AccessibilityNodeInfo locate(String label, long timeout) {
    long deadline = SystemClock.uptimeMillis() + timeout;
    int attempts = 0;
    while (SystemClock.uptimeMillis() < deadline) {
      AccessibilityNodeInfo n = find(label, false);
      if (n != null) {
        n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
        SystemClock.sleep(100);
        n.refresh();
        android.graphics.Rect bounds = new android.graphics.Rect();
        n.getBoundsInScreen(bounds);
        if (n.isVisibleToUser()
            && !bounds.isEmpty()
            && bounds.centerY() >= 0
            && bounds.centerY() < context.getResources().getDisplayMetrics().heightPixels) return n;
      }
      AccessibilityNodeInfo root = ui.getRootInActiveWindow();
      scroll(
          root,
          attempts++ % 20 < 10
              ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
              : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
      SystemClock.sleep(150);
    }
    throw new AssertionError("Missing UI label: " + label + "; UI=" + allText());
  }

  private boolean scroll(AccessibilityNodeInfo n, int action) {
    if (n == null) return false;
    if (n.isScrollable() && n.performAction(action)) return true;
    for (int i = 0; i < n.getChildCount(); i++) if (scroll(n.getChild(i), action)) return true;
    return false;
  }

  private AccessibilityNodeInfo find(String label, boolean clickable) {
    return findNode(ui.getRootInActiveWindow(), label, clickable);
  }

  private AccessibilityNodeInfo findNode(AccessibilityNodeInfo n, String label, boolean clickable) {
    if (n == null) return null;
    String text = n.getText() == null ? "" : n.getText().toString(),
        description = n.getContentDescription() == null ? "" : n.getContentDescription().toString(),
        hint = n.getHintText() == null ? "" : n.getHintText().toString();
    if ((text.equalsIgnoreCase(label)
            || description.equalsIgnoreCase(label)
            || hint.equalsIgnoreCase(label))
        && (!clickable || n.isClickable())) return n;
    for (int i = 0; i < n.getChildCount(); i++) {
      AccessibilityNodeInfo found = findNode(n.getChild(i), label, clickable);
      if (found != null) return found;
    }
    return null;
  }

  private String allText() {
    StringBuilder out = new StringBuilder();
    texts(ui.getRootInActiveWindow(), out);
    return out.toString();
  }

  private void texts(AccessibilityNodeInfo n, StringBuilder out) {
    if (n == null || out.length() > 100000) return;
    if (n.getText() != null) out.append(n.getText()).append('\n');
    if (n.getContentDescription() != null) out.append(n.getContentDescription()).append('\n');
    for (int i = 0; i < n.getChildCount(); i++) texts(n.getChild(i), out);
  }

  private void await(String text, long timeout) {
    long deadline = SystemClock.uptimeMillis() + timeout;
    while (SystemClock.uptimeMillis() < deadline) {
      if (allText().contains(text)) return;
      SystemClock.sleep(150);
    }
    throw new AssertionError("Missing expected UI output: " + text + "; UI=" + allText());
  }

  private void waitPackage(String name, long timeout) {
    long deadline = SystemClock.uptimeMillis() + timeout;
    while (SystemClock.uptimeMillis() < deadline) {
      AccessibilityNodeInfo root = ui.getRootInActiveWindow();
      if (root != null && name.contentEquals(root.getPackageName())) return;
      SystemClock.sleep(150);
    }
    throw new AssertionError("DocumentsUI did not launch");
  }
}
