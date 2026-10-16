package fr.jarvis.services.android;

import androidx.sqlite.SQLiteConnection;
import androidx.sqlite.SQLiteStatement;
import androidx.sqlite.driver.bundled.BundledSQLiteDriver;
import fr.jarvis.services.shared.storage.Database;
import java.util.*;

/** Bundled SQLite: FTS5 support does not depend on the phone vendor SQLite build. */
public final class AndroidDatabase implements Database {
  private final SQLiteConnection db;
  private boolean transaction;

  public AndroidDatabase(String file) {
    db = new BundledSQLiteDriver().open(file);
  }

  private void bind(SQLiteStatement s, Object[] args) {
    for (int i = 0; i < args.length; i++) {
      Object a = args[i];
      if (a == null) s.bindNull(i + 1);
      else if (a instanceof Number) s.bindLong(i + 1, ((Number) a).longValue());
      else s.bindText(i + 1, a.toString());
    }
  }

  public synchronized void execute(String sql, Object... args) {
    try (SQLiteStatement s = db.prepare(sql)) {
      bind(s, args);
      s.step();
    }
  }

  public synchronized List<Map<String, String>> query(String sql, Object... args) {
    try (SQLiteStatement s = db.prepare(sql)) {
      bind(s, args);
      List<Map<String, String>> rows = new ArrayList<>();
      while (s.step()) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < s.getColumnCount(); i++)
          row.put(s.getColumnName(i), s.isNull(i) ? null : s.getText(i));
        rows.add(row);
      }
      return rows;
    }
  }

  public synchronized void transaction(Runnable action) {
    if (transaction) {
      action.run();
      return;
    }
    execute("BEGIN IMMEDIATE");
    transaction = true;
    try {
      action.run();
      execute("COMMIT");
    } catch (Throwable e) {
      execute("ROLLBACK");
      throw e;
    } finally {
      transaction = false;
    }
  }

  public synchronized void close() {
    db.close();
  }
}
