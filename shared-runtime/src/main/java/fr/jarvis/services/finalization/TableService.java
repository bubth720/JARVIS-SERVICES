package fr.jarvis.services.finalization;

import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.pipeline.DataTransformEngine;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Explicit schemas and units; decimal strings remain exact through CSV/JSON round trips. */
public final class TableService {
  public enum Type {
    TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DATE
  }

  public static final class Column {
    public final Type type;
    public final String unit;

    public Column(Type type, String unit) {
      if (type == null
          || unit == null
          || !Arrays.asList("", "1", "EUR", "USD", "kg", "g", "m", "cm", "s", "ms", "%")
              .contains(unit)) throw new Failure("INVALID_UNIT");
      if (!unit.isEmpty() && type != Type.DECIMAL && type != Type.INTEGER)
        throw new Failure("INVALID_UNIT");
      this.type = type;
      this.unit = unit;
    }
  }

  public static final class Preview {
    public final Data.Table before, after;
    public final List<Map<String, String>> summaries;
    public final String sourceSha256, resultSha256;
    public final int changedCells, addedRows, removedRows;
    public final List<Integer> changedRowIndices;

    Preview(Data.Table before, DataTransformEngine.Result result, String source) {
      this.before = before;
      after = result.table;
      summaries = result.summaries;
      sourceSha256 = Guard.digest(source);
      resultSha256 = Guard.digest(after.csv());
      int changes = 0;
      List<Integer> indices = new ArrayList<>();
      for (int r = 0; r < Math.min(before.rows.size(), after.rows.size()); r++) {
        boolean changed = false;
        for (int c = 0; c < before.columns.size(); c++)
          if (!before.rows.get(r).get(c).equals(after.rows.get(r).get(c))) {
            changes++;
            changed = true;
          }
        if (changed && indices.size() < 100) indices.add(r + 1);
      }
      changedCells = changes;
      changedRowIndices = Collections.unmodifiableList(indices);
      addedRows = Math.max(0, after.rows.size() - before.rows.size());
      removedRows = Math.max(0, before.rows.size() - after.rows.size());
    }
  }

  private final Data data = new Data();

  public Preview transform(
      String csv,
      List<DataTransformEngine.Operation> recipe,
      Map<String, Column> schema,
      Task task) {
    task.check();
    Data.Table before = data.csv(csv);
    if (schema == null || !before.columns.containsAll(schema.keySet()))
      throw new Failure("INVALID_SCHEMA");
    DataTransformEngine.Result result = new DataTransformEngine(data).transform(csv, recipe, task);
    validate(result.table, schema, task);
    return new Preview(before, result, csv);
  }

  public void validate(Data.Table table, Map<String, Column> schema, Task task) {
    if (!inconsistencies(table, schema, task).isEmpty()) throw new Failure("TYPE_MISMATCH");
  }

  /** Bounded, value-free diagnostic rows; source hash remains in the analyst report. */
  public List<Map<String, Object>> inconsistencies(
      Data.Table table, Map<String, Column> schema, Task task) {
    if (schema == null || !table.columns.containsAll(schema.keySet()))
      throw new Failure("INVALID_SCHEMA");
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map.Entry<String, Column> entry : schema.entrySet()) {
      int c = table.columns.indexOf(entry.getKey());
      Column rule = entry.getValue();
      if (rule == null) throw new Failure("INVALID_SCHEMA");
      for (int rowIndex = 0; rowIndex < table.rows.size(); rowIndex++) {
        task.check();
        String value = table.rows.get(rowIndex).get(c);
        try {
          switch (rule.type) {
            case INTEGER:
              if (!value.matches("-?(0|[1-9][0-9]*)")) throw new Exception();
              decimal(value);
              break;
            case DECIMAL:
              decimal(value);
              break;
            case BOOLEAN:
              if (!value.equals("true") && !value.equals("false")) throw new Exception();
              break;
            case DATE:
              if (!LocalDate.parse(value).toString().equals(value)) throw new Exception();
              break;
            default:
              break;
          }
        } catch (Exception e) {
          Map<String, Object> issue = new TreeMap<>();
          issue.put("row", rowIndex + 1);
          issue.put("column", entry.getKey());
          issue.put("code", "TYPE_MISMATCH");
          out.add(issue);
          if (out.size() >= 100) return Collections.unmodifiableList(out);
        }
      }
    }
    return Collections.unmodifiableList(out);
  }

  public Data.Table convert(String input, String from, String to) {
    if (!Arrays.asList("csv", "json").contains(from) || !Arrays.asList("csv", "json").contains(to))
      throw new Failure("UNSUPPORTED_FORMAT");
    return from.equals("csv") ? data.csv(input) : data.json(Recipes.parse(input).toString());
  }

  public static BigDecimal decimal(String value) {
    if (value == null || value.length() > 100) throw new Failure("INVALID_NUMBER");
    try {
      BigDecimal n = new BigDecimal(value);
      if (Math.abs((long) n.scale()) > 100) throw new Exception();
      return n;
    } catch (Exception e) {
      throw new Failure("INVALID_NUMBER");
    }
  }
}
