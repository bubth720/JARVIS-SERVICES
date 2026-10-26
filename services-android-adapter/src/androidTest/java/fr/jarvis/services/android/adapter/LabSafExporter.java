package fr.jarvis.services.android.adapter;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.DocumentsContract;
import fr.jarvis.services.api.proposal.CommitAuthorization;
import fr.jarvis.services.modules.guard.AccessGuard;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Isolated qualification fixture ONLY, never compiled into the AAR. No provider rollback. Serial
 * worker, private durable receipts. COMMIT_STARTED survives crash as UNKNOWN. Fixture-only provider
 * allowlist deliberately prevents use on external/personal providers.
 */
final class LabSafExporter implements AutoCloseable {
  private final Context context;
  private final SQLiteDatabase db;
  private final RandomAccessFile leaseFile;
  private final java.nio.channels.FileLock lease;
  static final int LIMIT = 32;
  static final String AUTHORITY = "fr.jarvis.services.fixture.documents";

  LabSafExporter(Context c) throws Exception {
    context = c;
    leaseFile = new RandomAccessFile(new File(c.getFilesDir(), "fusion-export.lock"), "rw");
    java.nio.channels.FileLock acquired;
    try {
      acquired = leaseFile.getChannel().tryLock();
    } catch (Exception e) {
      leaseFile.close();
      throw e;
    }
    if (acquired == null) {
      leaseFile.close();
      throw new IOException("BUSY");
    }
    lease = acquired;
    SQLiteDatabase opened = null;
    try {
      // Explicit error handler: Android's default corruption handler may delete a database.
      // Refuse corruption without destructive recovery, even for this synthetic journal.
      File path = c.getDatabasePath("fusion_lab_exports.db");
      path.getParentFile().mkdirs();
      opened =
          SQLiteDatabase.openDatabase(
              path.getPath(),
              null,
              SQLiteDatabase.CREATE_IF_NECESSARY,
              damaged -> {
                throw new android.database.sqlite.SQLiteException("CORRUPT_JOURNAL_NO_RESET");
              });
      opened.execSQL(
          "CREATE TABLE IF NOT EXISTS receipts(id TEXT PRIMARY KEY,state TEXT NOT NULL,digest TEXT"
              + " NOT NULL,destination TEXT)");
      opened.execSQL(
          "UPDATE receipts SET state='UNKNOWN' WHERE state IN ('PREPARED','COMMIT_STARTED')");
      db = opened;
    } catch (Exception failure) {
      try {
        if (opened != null) opened.close();
      } finally {
        try {
          lease.release();
        } finally {
          leaseFile.close();
        }
      }
      throw failure;
    }
  }

  synchronized String state(String id) {
    try (Cursor c = db.rawQuery("SELECT state FROM receipts WHERE id=?", new String[] {id})) {
      return c.moveToFirst() ? c.getString(0) : null;
    }
  }

  private void state(String id, String state, Uri destination) {
    db.execSQL(
        "UPDATE receipts SET state=?, destination=COALESCE(?,destination) WHERE id=?",
        new Object[] {state, destination == null ? null : destination.toString(), id});
  }

  synchronized String export(
      CommitAuthorization.Preview p,
      CommitAuthorization.ConsentToken consent,
      CommitAuthorization authority,
      Uri source,
      Uri tree,
      String name,
      byte[] bytes)
      throws Exception {
    // Entire worker serialized. Authority owns its own short decision lock, not provider IO.
    if (authority == null
        || source == null
        || tree == null
        || !"content".equals(source.getScheme())
        || !"content".equals(tree.getScheme())
        || !LabSafExporter.AUTHORITY.equals(source.getAuthority())
        || !LabSafExporter.AUTHORITY.equals(tree.getAuthority())
        || !DocumentsContract.isTreeUri(tree)
        || !name.matches("result-[a-z0-9-]{36}\\.(csv|docx)")
        || !p.operation.equals("export." + name.substring(name.lastIndexOf('.') + 1))
        || !p.destination.equals(tree.toString() + "/" + name)
        || bytes.length > 2 * 1024 * 1024
        || !p.contentDigest.equals(AccessGuard.digest(bytes))
        || !p.sourceDigest.equals(AccessGuard.digest(read(source))))
      throw new SecurityException("INVALID_PREVIEW_OR_PROVIDER");
    if (state(p.request) != null) throw new SecurityException("DUPLICATE_REQUEST");
    try (Cursor c = db.rawQuery("SELECT count(*) FROM receipts", null)) {
      c.moveToFirst();
      if (c.getInt(0) >= LIMIT) throw new IOException("JOURNAL_FULL");
    }
    db.execSQL(
        "INSERT INTO receipts VALUES(?,'PREPARED',?,NULL)",
        new Object[] {p.request, p.contentDigest});
    AtomicBoolean committed = new AtomicBoolean();
    try {
      return authority.authorizeAtCommit(
          p,
          consent,
          () -> {
            committed.set(true);
            state(p.request, "COMMIT_STARTED", null); // durable before first provider mutation
            Uri parent =
                DocumentsContract.buildDocumentUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree));
            Uri dest =
                DocumentsContract.createDocument(
                    context.getContentResolver(),
                    parent,
                    name.endsWith(".csv")
                        ? "text/csv"
                        : "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    name);
            if (dest == null
                || dest.equals(source)
                || !dest.getAuthority().equals(tree.getAuthority()))
              throw new IOException("INVALID_DESTINATION_ACK");
            state(p.request, "COMMIT_STARTED", dest);
            try (OutputStream out = context.getContentResolver().openOutputStream(dest, "w")) {
              if (out == null) throw new IOException("NO_STREAM");
              out.write(bytes);
              out.flush();
            } // close must return successfully before readback
            byte[] observed = read(dest);
            if (!Arrays.equals(bytes, observed)
                || !p.contentDigest.equals(AccessGuard.digest(observed)))
              throw new IOException("ACK_UNVERIFIED");
            if (!p.sourceDigest.equals(AccessGuard.digest(read(source))))
              throw new IOException("SOURCE_CHANGED");
            state(p.request, "COMMITTED", dest);
            return dest.toString();
          });
    } catch (Exception e) {
      state(p.request, committed.get() ? "UNKNOWN" : "REFUSED", null);
      throw e;
    }
  }

  byte[] read(Uri u) throws IOException {
    try (InputStream in = context.getContentResolver().openInputStream(u);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      if (in == null) throw new IOException("NO_STREAM");
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) {
        if (out.size() + n > 2 * 1024 * 1024) throw new IOException("LIMIT");
        out.write(b, 0, n);
      }
      return out.toByteArray();
    }
  }

  // Test-only crash-window injection: same journal state that a killed worker leaves.
  synchronized void abandoned(String id) {
    db.execSQL(
        "INSERT INTO receipts VALUES(?,'COMMIT_STARTED',?,NULL)",
        new Object[] {id, AccessGuard.digest(new byte[0])});
  }

  public synchronized void close() throws Exception {
    try {
      db.close();
    } finally {
      try {
        lease.release();
      } finally {
        leaseFile.close();
      }
    }
  }
}
