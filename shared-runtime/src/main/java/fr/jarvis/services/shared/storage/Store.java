package fr.jarvis.services.shared.storage;

import com.google.gson.Gson;
import fr.jarvis.services.shared.api.Contracts.Failure;
import java.util.*;

/** Modules use this repository, never raw shared table handles. */
public final class Store {
  private final Database db;
  private final Gson json = new Gson();

  public Store(Database db) {
    this.db = db;
    migrate(1);
  }

  public synchronized void migrate(int target) {
    if (target < 0 || target > 1) throw new Failure("INCOMPATIBLE_MIGRATION");
    db.transaction(
        () -> {
          db.execute("CREATE TABLE IF NOT EXISTS schema_meta(version INTEGER NOT NULL)");
          if (db.query("SELECT version FROM schema_meta").isEmpty())
            db.execute("INSERT INTO schema_meta VALUES(0)");
          int version =
              Integer.parseInt(db.query("SELECT version FROM schema_meta").get(0).get("version"));
          if (version > 1) throw new Failure("INCOMPATIBLE_MIGRATION");
          if (version == 0 && target == 1) {
            db.execute(
                "CREATE TABLE entries(namespace TEXT NOT NULL,id TEXT NOT NULL,body TEXT NOT"
                    + " NULL,PRIMARY KEY(namespace,id))");
            db.execute("CREATE VIRTUAL TABLE search USING fts5(id UNINDEXED,text)");
          } else if (version == 1 && target == 0) {
            if (!db.query("SELECT id FROM entries LIMIT 1").isEmpty())
              throw new Failure("BACKUP_REQUIRED");
            db.execute("DROP TABLE search");
            db.execute("DROP TABLE entries");
          }
          db.execute("UPDATE schema_meta SET version=?", target);
        });
  }

  public synchronized void put(String ns, String id, Object body) {
    db.execute("INSERT OR REPLACE INTO entries VALUES(?,?,?)", ns, id, json.toJson(body));
  }

  @SuppressWarnings("unchecked")
  public synchronized Map<String, Object> get(String ns, String id) {
    List<Map<String, String>> rows =
        db.query("SELECT body FROM entries WHERE namespace=? AND id=?", ns, id);
    return rows.isEmpty() ? null : json.fromJson(rows.get(0).get("body"), Map.class);
  }

  public synchronized List<Map<String, Object>> all(String ns) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, String> row :
        db.query("SELECT body FROM entries WHERE namespace=? ORDER BY id", ns))
      out.add(json.fromJson(row.get("body"), Map.class));
    return out;
  }

  /** Keyset cursor, at most eight document bodies decoded at a time. */
  public Iterable<Map<String, Object>> scan(String namespace) {
    return () ->
        new Iterator<Map<String, Object>>() {
          private String cursor;
          private List<Map<String, String>> page = Collections.emptyList();
          private int position;
          private boolean finished;

          public boolean hasNext() {
            if (position < page.size()) return true;
            if (finished) return false;
            page = scanPage(namespace, cursor);
            position = 0;
            if (page.isEmpty()) {
              finished = true;
              return false;
            }
            return true;
          }

          @SuppressWarnings("unchecked")
          public Map<String, Object> next() {
            if (!hasNext()) throw new NoSuchElementException();
            Map<String, String> row = page.get(position++);
            cursor = row.get("id");
            return json.fromJson(row.get("body"), Map.class);
          }
        };
  }

  private synchronized List<Map<String, String>> scanPage(String namespace, String cursor) {
    return cursor == null
        ? db.query("SELECT id,body FROM entries WHERE namespace=? ORDER BY id LIMIT 8", namespace)
        : db.query(
            "SELECT id,body FROM entries WHERE namespace=? AND id>? ORDER BY id LIMIT 8",
            namespace,
            cursor);
  }

  public synchronized int count(String ns) {
    return Integer.parseInt(
        db.query("SELECT count(*) AS n FROM entries WHERE namespace=?", ns).get(0).get("n"));
  }

  public synchronized void delete(String ns, String id) {
    db.execute("DELETE FROM entries WHERE namespace=? AND id=?", ns, id);
  }

  public synchronized void index(String id, String text) {
    db.transaction(
        () -> {
          db.execute("DELETE FROM search WHERE id=?", id);
          db.execute("INSERT INTO search(id,text) VALUES(?,?)", id, text);
        });
  }

  public synchronized void unindex(String id) {
    db.execute("DELETE FROM search WHERE id=?", id);
  }

  public synchronized List<Map<String, String>> search(String term, int limit) {
    if (term == null || term.length() > 500 || limit < 1 || limit > 50)
      throw new Failure("INVALID_INPUT");
    String literal = "\"" + term.replace("\"", "\"\"") + "\"";
    return db.query(
        "SELECT id, snippet(search,1,'[',']','...',24) AS excerpt, bm25(search) AS rank FROM search"
            + " WHERE search MATCH ? ORDER BY bm25(search) LIMIT ?",
        literal,
        limit);
  }

  public synchronized void trimAudit(int keep) {
    db.execute(
        "DELETE FROM entries WHERE namespace='audit' AND rowid NOT IN (SELECT rowid FROM entries"
            + " WHERE namespace='audit' ORDER BY rowid DESC LIMIT ?)",
        keep);
  }

  public synchronized void transaction(Runnable r) {
    db.transaction(r);
  }

  public synchronized String integrity() {
    return db.query("PRAGMA integrity_check").get(0).values().iterator().next();
  }

  public synchronized String exportSnapshot() {
    Map<String, Object> snapshot = new TreeMap<>();
    for (String ns : Arrays.asList("documents", "knowledge", "memory", "actions", "audit"))
      snapshot.put(ns, all(ns));
    return json.toJson(snapshot);
  }

  public synchronized void restoreSnapshot(String snapshot) {
    Map<?, ?> parsed = json.fromJson(snapshot, Map.class);
    if (parsed == null
        || !parsed
            .keySet()
            .equals(
                new HashSet<>(
                    Arrays.asList("documents", "knowledge", "memory", "actions", "audit"))))
      throw new Failure("INVALID_BACKUP");
    db.transaction(
        () -> {
          db.execute("DELETE FROM entries");
          db.execute("DELETE FROM search");
          for (Object ns : parsed.keySet())
            for (Object item : (List<?>) parsed.get(ns)) {
              Map<?, ?> m = (Map<?, ?>) item;
              String id = (String) m.get("id");
              if (id == null) throw new Failure("INVALID_BACKUP");
              put((String) ns, id, item);
              if (ns.equals("knowledge")) index(id, (String) m.get("text"));
            }
        });
  }
}
