package fr.jarvis.services.android;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Installed in the test APK only, serves two synthetic files and rejects every other path. */
public final class SyntheticDocumentProvider extends ContentProvider {
  public static final String AUTHORITY = "fr.jarvis.services.diagnostic.test.synthetic";
  public static final Uri PDF = Uri.parse("content://" + AUTHORITY + "/sample.pdf");
  public static final Uri REPORT = Uri.parse("content://" + AUTHORITY + "/report.json");

  @Override
  public boolean onCreate() {
    return true;
  }

  private File file(Uri uri) throws FileNotFoundException {
    String path = uri.getPath();
    if (!"/sample.pdf".equals(path) && !"/report.json".equals(path))
      throw new FileNotFoundException("Synthetic fixture only");
    return new File(getContext().getCacheDir(), path.substring(1));
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    File target = file(uri);
    if (PDF.equals(uri)) {
      if (!"r".equals(mode)) throw new FileNotFoundException("Immutable fixture");
      if (!target.exists())
        try (InputStream in = getContext().getAssets().open("sample.pdf");
            OutputStream out = new FileOutputStream(target)) {
          byte[] b = new byte[4096];
          int n;
          while ((n = in.read(b)) != -1) out.write(b, 0, n);
        } catch (IOException e) {
          throw new FileNotFoundException("Fixture unavailable");
        }
    }
    return ParcelFileDescriptor.open(target, ParcelFileDescriptor.parseMode(mode));
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
    try {
      file(uri);
    } catch (FileNotFoundException e) {
      throw new IllegalArgumentException("Unknown fixture");
    }
    String[] columns =
        projection == null ? new String[] {OpenableColumns.DISPLAY_NAME} : projection;
    MatrixCursor cursor = new MatrixCursor(columns);
    Object[] row = new Object[columns.length];
    for (int i = 0; i < columns.length; i++)
      if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = uri.getLastPathSegment();
    cursor.addRow(row);
    return cursor;
  }

  @Override
  public String getType(Uri uri) {
    return PDF.equals(uri) ? "application/pdf" : "application/json";
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] args) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String selection, String[] args) {
    throw new UnsupportedOperationException();
  }
}
