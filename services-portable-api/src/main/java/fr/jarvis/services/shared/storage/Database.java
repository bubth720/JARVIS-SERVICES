package fr.jarvis.services.shared.storage;

import java.util.*;

public interface Database extends AutoCloseable {
  void execute(String sql, Object... args);

  List<Map<String, String>> query(String sql, Object... args);

  void transaction(Runnable action);

  void close();
}
