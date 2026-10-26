package fr.jarvis.services.android.adapter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.util.concurrent.LinkedBlockingQueue;

/** Test APK only. No production UI, exported service or permission is added. */
public final class FusionPickerActivity extends Activity {
  static final LinkedBlockingQueue<Uri> results = new LinkedBlockingQueue<>();

  public void onCreate(Bundle state) {
    super.onCreate(state);
    if (state == null) {
      Intent i =
          new Intent(
              getIntent().getBooleanExtra("tree", false)
                  ? Intent.ACTION_OPEN_DOCUMENT_TREE
                  : Intent.ACTION_OPEN_DOCUMENT);
      if (Intent.ACTION_OPEN_DOCUMENT.equals(i.getAction())) {
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
      }
      i.addFlags(
          Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
      if (Intent.ACTION_OPEN_DOCUMENT_TREE.equals(i.getAction()))
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      startActivityForResult(i, 42);
    }
  }

  public void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    android.util.Log.i(
        "FUSION_PICKER", "request=" + request + " result=" + result + " data=" + data);
    if (request == 42 && result == RESULT_OK && data != null && data.getData() != null) {
      getContentResolver()
          .takePersistableUriPermission(
              data.getData(),
              data.getFlags()
                  & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                      | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
      results.add(data.getData());
    }
    finish();
  }
}
