package fr.jarvis.services.platform;

import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Database;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

public final class JdbcDatabase implements Database {
  private final Connection connection;

  public JdbcDatabase(Path path) {
    try {
      connection = DriverManager.getConnection("jdbc:sqlite:" + path);
    } catch (SQLException e) {
      throw new Failure("STORAGE_ERROR");
    }
  }

  public synchronized void execute(String sql, Object... args) {
    try (PreparedStatement s = connection.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
      s.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("SQL execution failed", e);
    }
  }

  public synchronized List<Map<String, String>> query(String sql, Object... args) {
    try (PreparedStatement s = connection.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
      try (ResultSet rs = s.executeQuery()) {
        List<Map<String, String>> out = new ArrayList<>();
        while (rs.next()) {
          Map<String, String> row = new LinkedHashMap<>();
          for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
            row.put(rs.getMetaData().getColumnLabel(i), rs.getString(i));
          out.add(row);
        }
        return out;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("SQL query failed", e);
    }
  }

  public synchronized void transaction(Runnable action) {
    try {
      if (!connection.getAutoCommit()) {
        action.run();
        return;
      }
      connection.setAutoCommit(false);
      try {
        action.run();
        connection.commit();
      } catch (Throwable e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(true);
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public void close() {
    try {
      connection.close();
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}
