package fr.jarvis.services.android;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import android.provider.DocumentsContract;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.pipeline.SafUriPort;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.io.*;
import java.util.*;

/** Only a trusted host constructs this after ACTION_OPEN_DOCUMENT_TREE and explicit selection.
 * Existing grants are required; this class never obtains or persists a permission itself.
 */
public final class SelectedTreeExportPort implements SafUriPort {
  private final Context context; private final ContentResolver resolver; private final Uri tree;
  private final String reference = UUID.randomUUID().toString();
  public SelectedTreeExportPort(Context context, Uri userSelectedTree) {
    if (userSelectedTree == null || !"content".equals(userSelectedTree.getScheme())
        || !DocumentsContract.isTreeUri(userSelectedTree)) throw new Failure("INVALID_URI");
    this.context = context; this.resolver = context.getContentResolver(); this.tree = userSelectedTree;
    requireGrant();
  }
  private void requireGrant() {
    if (context.checkUriPermission(tree, Process.myPid(), Process.myUid(),
        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED)
      throw new Failure("URI_PERMISSION_REQUIRED");
  }
  public String targetReference() { return reference; }
  public Transaction begin(String targetRef, String name, String mime, Task task) {
    task.check(); requireGrant();
    if (!reference.equals(targetRef) || name == null || name.length() > 200 || name.contains("/") || name.contains("\\"))
      throw new Failure("INVALID_URI_REFERENCE");
    Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    Set<String> existing = new HashSet<>();
    boolean[] creationAttempted = {false};
    try (Cursor cursor = resolver.query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
      if (cursor == null) throw new Failure("SAF_UNAVAILABLE");
      while (cursor.moveToNext()) {
        task.check(); if (existing.size() >= 1000) throw new Failure("SAF_DIRECTORY_LIMIT");
        existing.add(cursor.getString(0)); if (name.equals(cursor.getString(1))) throw new Failure("WOULD_OVERWRITE");
      }
      creationAttempted[0] = true;
      Uri created = DocumentsContract.createDocument(resolver, parent, mime, name);
      if (created == null) throw new Failure("SAF_CREATION_UNKNOWN");
      String id = DocumentsContract.getDocumentId(created);
      // Never open or delete a document that the provider identified as pre-existing.
      if (existing.contains(id)) throw new Failure("SAF_PROVIDER_UNSAFE");
      return new FreshDocument(created);
    } catch (Failure e) { throw e; }
    catch (Exception e) { throw new Failure(creationAttempted[0] ? "SAF_CREATION_UNKNOWN" : "SAF_UNAVAILABLE"); }
  }
  private final class FreshDocument implements Transaction {
    private final Uri uri; private boolean committed, rolledBack;
    FreshDocument(Uri uri) { this.uri = uri; }
    private void active() { if (committed || rolledBack) throw new Failure("INVALID_EXPORT_STATE"); requireGrant(); }
    public void write(byte[] bytes, Task task) {
      active(); if (bytes.length > Documents.MAX_BYTES) throw new Failure("INPUT_TOO_LARGE");
      try (OutputStream out = resolver.openOutputStream(uri, "w")) {
        if (out == null) throw new Failure("SAF_WRITE_FAILED");
        for (int offset = 0; offset < bytes.length; offset += 8192) {
          task.check(); out.write(bytes, offset, Math.min(8192, bytes.length - offset));
        }
        out.flush(); task.check();
      } catch (Failure e) { throw e; }
      catch (Exception e) { throw new Failure("SAF_WRITE_FAILED"); }
    }
    public String readbackSha256(Task task) {
      active();
      try (InputStream in = resolver.openInputStream(uri); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
        if (in == null) throw new Failure("SAF_READ_FAILED");
        byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) {
          task.check(); if (bytes.size() + n > Documents.MAX_BYTES) throw new Failure("INPUT_TOO_LARGE"); bytes.write(buffer, 0, n);
        }
        task.check(); return Guard.digest(bytes.toByteArray());
      } catch (Failure e) { throw e; }
      catch (Exception e) { throw new Failure("SAF_READ_FAILED"); }
    }
    public String commit(Task task) { active(); task.check(); committed = true; return uri.toString(); }
    public boolean rollback() {
      if (committed) return false;
      if (rolledBack) return true;
      try { rolledBack = DocumentsContract.deleteDocument(resolver, uri); return rolledBack; }
      catch (Exception e) { return false; }
    }
  }
}
