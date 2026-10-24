package fr.jarvis.services.android;

import android.database.*;
import android.os.*;
import android.provider.*;
import java.io.*;
import java.util.*;

/** Real DocumentsProvider in the test APK only. Android DocumentsUI alone grants access. */
public final class FinalDocumentsProvider extends DocumentsProvider {
  public static final String AUTHORITY = "fr.jarvis.services.diagnostic.test.finalfiles";
  private File root;

  @Override
  public boolean onCreate() {
    root = new File(getContext().getFilesDir(), "final-saf-fixtures");
    if (!root.exists() && !root.mkdirs()) return false;
    try {
      File csv = new File(root, "source.csv");
      if (!csv.exists())
        try (OutputStream out = new FileOutputStream(csv)) {
          out.write(
              "name,amount\n a ,0.10\n a ,0.10\n b ,0.20\n"
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
      File pdf = new File(root, "sample.pdf");
      if (!pdf.exists())
        try (InputStream in = getContext().getAssets().open("sample.pdf");
            OutputStream out = new FileOutputStream(pdf)) {
          byte[] b = new byte[8192];
          int n;
          while ((n = in.read(b)) != -1) out.write(b, 0, n);
        }
      File template = new File(root, "template.docx");
      if (!template.exists())
        try (InputStream in = getContext().getAssets().open("template.docx");
            OutputStream out = new FileOutputStream(template)) {
          byte[] b = new byte[8192];
          int n;
          while ((n = in.read(b)) != -1) out.write(b, 0, n);
        }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static final String[] ROOT_COLUMNS = {
    DocumentsContract.Root.COLUMN_ROOT_ID,
    DocumentsContract.Root.COLUMN_DOCUMENT_ID,
    DocumentsContract.Root.COLUMN_TITLE,
    DocumentsContract.Root.COLUMN_FLAGS,
    DocumentsContract.Root.COLUMN_MIME_TYPES,
    DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
  };
  private static final String[] DOC_COLUMNS = {
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
    DocumentsContract.Document.COLUMN_FLAGS,
    DocumentsContract.Document.COLUMN_SIZE,
    DocumentsContract.Document.COLUMN_LAST_MODIFIED
  };

  @Override
  public Cursor queryRoots(String[] projection) {
    MatrixCursor cursor = new MatrixCursor(projection == null ? ROOT_COLUMNS : projection);
    MatrixCursor.RowBuilder row = cursor.newRow();
    row.add(DocumentsContract.Root.COLUMN_ROOT_ID, "fixtures");
    row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "root");
    row.add(DocumentsContract.Root.COLUMN_TITLE, "JARVIS FINAL fixtures");
    row.add(
        DocumentsContract.Root.COLUMN_FLAGS,
        DocumentsContract.Root.FLAG_SUPPORTS_CREATE
            | DocumentsContract.Root.FLAG_LOCAL_ONLY
            | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD);
    row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*");
    row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, 64L * 1024 * 1024);
    return cursor;
  }

  @Override
  public Cursor queryDocument(String id, String[] projection) throws FileNotFoundException {
    MatrixCursor cursor = new MatrixCursor(projection == null ? DOC_COLUMNS : projection);
    add(cursor, id);
    return cursor;
  }

  @Override
  public Cursor queryChildDocuments(String parent, String[] projection, String order)
      throws FileNotFoundException {
    if (!"root".equals(parent)) throw new FileNotFoundException();
    MatrixCursor cursor = new MatrixCursor(projection == null ? DOC_COLUMNS : projection);
    File[] files = root.listFiles();
    if (files == null) throw new FileNotFoundException();
    // Keep immutable inputs visible even when repeated qualification leaves many outputs.
    Arrays.sort(
        files,
        Comparator.comparing((File f) -> f.getName().startsWith("result-"))
            .thenComparing(File::getName));
    for (File f : files) add(cursor, f.getName());
    return cursor;
  }

  private void add(MatrixCursor cursor, String id) throws FileNotFoundException {
    File f = file(id);
    boolean dir = id.equals("root");
    MatrixCursor.RowBuilder row = cursor.newRow();
    row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id);
    row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, dir ? "JARVIS FINAL fixtures" : id);
    row.add(
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        dir
            ? DocumentsContract.Document.MIME_TYPE_DIR
            : id.endsWith(".pdf")
                ? "application/pdf"
                : id.endsWith(".docx")
                    ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    : id.endsWith(".json") ? "application/json" : "text/csv");
    row.add(
        DocumentsContract.Document.COLUMN_FLAGS,
        dir
            ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            : id.startsWith("result-")
                ? DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                    | DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                : 0);
    row.add(DocumentsContract.Document.COLUMN_SIZE, dir ? 0 : f.length());
    row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, f.lastModified());
  }

  private File file(String id) throws FileNotFoundException {
    if ("root".equals(id)) return root;
    if (id == null || !id.matches("[a-zA-Z0-9._-]{1,200}") || id.equals(".") || id.equals(".."))
      throw new FileNotFoundException();
    File f = new File(root, id);
    if (!f.exists()) throw new FileNotFoundException();
    return f;
  }

  @Override
  public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal)
      throws FileNotFoundException {
    File f = file(id);
    if (!mode.equals("r") && !id.startsWith("result-"))
      throw new FileNotFoundException("Original is immutable");
    return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode));
  }

  @Override
  public String createDocument(String parent, String mime, String name)
      throws FileNotFoundException {
    if (!"root".equals(parent)
        || name == null
        || !name.matches("result-[a-z0-9-]{36}\\.(csv|json|docx)"))
      throw new FileNotFoundException();
    File f = new File(root, name);
    try {
      if (!f.createNewFile()) throw new FileNotFoundException("Would overwrite");
    } catch (IOException e) {
      throw new FileNotFoundException();
    }
    return name;
  }

  @Override
  public void deleteDocument(String id) throws FileNotFoundException {
    if (!id.startsWith("result-") || !file(id).delete()) throw new FileNotFoundException();
  }

  @Override
  public boolean isChildDocument(String parent, String child) {
    try {
      return "root".equals(parent) && !"root".equals(child) && file(child).isFile();
    } catch (FileNotFoundException e) {
      return false;
    }
  }
}
