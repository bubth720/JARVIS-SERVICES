package fr.jarvis.services.android;

import android.content.Context;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import fr.jarvis.services.pipeline.DocumentTextExtractor;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

/** Compiled locally; execution requires the isolated Android instrumentation runner. */
@RunWith(AndroidJUnit4.class)
public class PipelineAndroidTest {
  @Test public void readonlyTextPreservesUnicodeAndUsesNoDocumentStore() {
    DocumentTextExtractor extractor = new DocumentTextExtractor(new AndroidPdfText());
    List<DocumentTextExtractor.Page> pages = extractor.extract("source.txt", "Athéna 😀 e\u0301".getBytes(StandardCharsets.UTF_8), new Task(3000));
    assertEquals(1, pages.size()); assertEquals(0, pages.get(0).page);
    assertEquals("Athéna 😀 e\u0301", pages.get(0).text);
  }
  @Test public void fileUriCannotConstructASafTarget() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    try { new SelectedTreeExportPort(context, Uri.parse("file:///private/forbidden")); fail("Expected refusal"); }
    catch (Failure e) { assertEquals("INVALID_URI", e.code); }
  }
  @Test public void ungrantedTreeCannotConstructASafTarget() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    try { new SelectedTreeExportPort(context, Uri.parse("content://invalid.example/tree/forbidden")); fail("Expected refusal"); }
    catch (Failure e) { assertEquals("URI_PERMISSION_REQUIRED", e.code); }
  }
}
