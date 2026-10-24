package fr.jarvis.services.android.adapter;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import fr.jarvis.services.shared.storage.Database;
import fr.jarvis.services.shared.api.Contracts.Failure;
import java.util.*;
import java.io.*;
import java.nio.channels.*;

/** New SDK-owned entry journal only; never opens, upgrades or imports a historical database. */
final class JournalDatabase implements Database {
  private final SQLiteDatabase database;
  private final RandomAccessFile leaseFile;
  private final FileLock lease;
  private boolean closed;
  JournalDatabase(Context context) {
    database = context.openOrCreateDatabase("jarvis_serv04a_journal_v1.db", Context.MODE_PRIVATE, null);
    RandomAccessFile acquiredFile = null; FileLock acquiredLock = null;
    try {
      acquiredFile = new RandomAccessFile(new File(context.getDatabasePath("jarvis_serv04a_journal_v1.db").getParentFile(), ".serv04a-journal.lock"), "rw");
      acquiredLock = acquiredFile.getChannel().tryLock();
      if (acquiredLock == null) throw new Failure("JOURNAL_BUSY");
    } catch (IOException | OverlappingFileLockException | Failure failure) {
      if (acquiredFile != null) try { acquiredFile.close(); } catch (IOException ignored) {}
      database.close(); throw new Failure("JOURNAL_BUSY");
    }
    leaseFile = acquiredFile; lease = acquiredLock;
    try {
      transaction(() -> {
        List<Map<String, String>> tables = query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name<>'android_metadata'");
        if (tables.isEmpty()) {
          execute("CREATE TABLE sdk_owner(contract TEXT PRIMARY KEY)");
          execute("INSERT INTO sdk_owner VALUES(?)", "jarvis.services.android-journal/1");
        } else {
          boolean marker = false;
          for (Map<String, String> table : tables) {
            String name = table.get("name");
            if ("sdk_owner".equals(name)) marker = true;
            else if (!"entries".equals(name) && !"schema_meta".equals(name)) throw new Failure("INCOMPATIBLE_JOURNAL");
          }
          if (!marker || query("SELECT contract FROM sdk_owner").size() != 1
              || !"jarvis.services.android-journal/1".equals(query("SELECT contract FROM sdk_owner").get(0).get("contract")))
            throw new Failure("INCOMPATIBLE_JOURNAL");
        }
      });
    } catch (RuntimeException failure) { close(); throw failure; }
  }
  public synchronized void execute(String sql, Object... args) { database.execSQL(sql, args); }
  public synchronized List<Map<String, String>> query(String sql, Object... args) {
    String[] parameters = new String[args.length];
    for (int i = 0; i < args.length; i++) parameters[i] = args[i] == null ? null : String.valueOf(args[i]);
    List<Map<String, String>> rows = new ArrayList<>();
    try (Cursor cursor = database.rawQuery(sql, parameters)) {
      while (cursor.moveToNext()) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int c = 0; c < cursor.getColumnCount(); c++)
          row.put(cursor.getColumnName(c), cursor.isNull(c) ? null : cursor.getString(c));
        rows.add(row);
      }
    }
    return rows;
  }
  public synchronized void transaction(Runnable action) {
    if (database.inTransaction()) { action.run(); return; }
    database.beginTransaction();
    try { action.run(); database.setTransactionSuccessful(); }
    finally { database.endTransaction(); }
  }
  public synchronized void close() {
    if (closed) return;
    try { database.close(); }
    finally {
      try { lease.release(); } catch (IOException e) { throw new Failure("JOURNAL_CLOSE_FAILED"); }
      finally { try { leaseFile.close(); } catch (IOException e) { throw new Failure("JOURNAL_CLOSE_FAILED"); } }
      closed = true;
    }
  }
}
