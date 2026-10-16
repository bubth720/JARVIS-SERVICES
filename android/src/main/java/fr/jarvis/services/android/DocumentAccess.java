package fr.jarvis.services.android;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import com.google.gson.GsonBuilder;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.diagnostics.Diagnostics;
import fr.jarvis.services.shared.tasks.Task;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** SAF boundary only. It cannot grant permissions or choose a source on behalf of the user. */
public final class DocumentAccess {
  private DocumentAccess() {}

  public static final class Imported {
    public final String name;
    public final byte[] bytes;

    Imported(String name, byte[] bytes) {
      this.name = name;
      this.bytes = bytes;
    }
  }

  public static Imported read(ContentResolver resolver, Uri uri, int byteLimit, Task task)
      throws IOException {
    if (uri == null || !"content".equals(uri.getScheme())) throw new Failure("INVALID_URI");
    if (byteLimit < 1 || byteLimit > Documents.MAX_BYTES) throw new Failure("INVALID_LIMIT");
    task.check();
    String name = "import.txt";
    try (Cursor cursor =
        resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = resolver.openInputStream(uri)) {
      if (in == null) throw new Failure("READ_FAILED");
      byte[] buffer = new byte[8192];
      int n;
      while ((n = in.read(buffer)) != -1) {
        task.check();
        if (bytes.size() + n > byteLimit) throw new Failure("INPUT_TOO_LARGE");
        bytes.write(buffer, 0, n);
      }
    }
    task.check();
    return new Imported(name, bytes.toByteArray());
  }

  public static void writeTechnicalReport(
      ContentResolver resolver, Uri uri, Diagnostics diagnostics, Guard.Context context, Task task)
      throws IOException {
    if (uri == null || !"content".equals(uri.getScheme())) throw new Failure("INVALID_URI");
    task.check();
    byte[] bytes =
        new GsonBuilder()
            .setPrettyPrinting()
            .create()
            .toJson(diagnostics.report(context))
            .getBytes(StandardCharsets.UTF_8);
    task.check();
    try (OutputStream out = resolver.openOutputStream(uri, "w")) {
      if (out == null) throw new Failure("WRITE_FAILED");
      out.write(bytes);
    }
  }
}
