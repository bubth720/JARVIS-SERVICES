package fr.jarvis.services.finalization;

import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.math.BigDecimal;
import java.util.*;

/** Calculation is independent of interpretation and contains sufficient source/recipe metadata. */
public final class StructuredAnalyst {
  public Map<String, Object> inspect(
      Data.Table table, Map<String, TableService.Column> schema, Task task) {
    Map<String, Object> report = new TreeMap<>();
    report.put("kind", "schema_validation");
    report.put("source_csv_sha256", Guard.digest(table.csv()));
    List<Map<String, Object>> issues = new TableService().inconsistencies(table, schema, task);
    report.put("inconsistencies", issues);
    report.put("diagnostic_limit", 100);
    report.put("may_be_truncated", issues.size() == 100);
    report.put("interpretation", Collections.emptyList());
    report.put("hypotheses", Collections.emptyList());
    return report;
  }

  public Map<String, Object> analyze(
      Data.Table table, Map<String, TableService.Column> schema, Task task) {
    new TableService().validate(table, schema, task);
    Map<String, Object> result = new TreeMap<>();
    Map<String, Object> columns = new TreeMap<>();
    result.put("kind", "deterministic_calculation");
    result.put("source_csv_sha256", Guard.digest(table.csv()));
    result.put("row_count", table.rows.size());
    result.put("operations", table.history);
    result.put("interpretation", Collections.emptyList());
    result.put("hypotheses", Collections.emptyList());
    for (Map.Entry<String, TableService.Column> e : schema.entrySet()) {
      task.check();
      if (e.getValue().type == TableService.Type.DECIMAL
          || e.getValue().type == TableService.Type.INTEGER) {
        Map<String, Object> stats = new TreeMap<>();
        stats.put("unit", e.getValue().unit);
        if (!table.rows.isEmpty()) {
          stats.putAll(table.stats(e.getKey()));
          BigDecimal sum = TableService.decimal((String) stats.get("sum"));
          stats.put("exact_mean_fraction", sum.toPlainString() + "/" + table.rows.size());
          try {
            stats.put(
                "exact_mean", sum.divide(BigDecimal.valueOf(table.rows.size())).toPlainString());
            stats.put("mean_is_exact", true);
          } catch (ArithmeticException ex) {
            stats.put("mean_is_exact", false);
          }
        } else stats.put("count", "0");
        columns.put(e.getKey(), stats);
      }
    }
    result.put("columns", columns);
    result.put("inconsistencies", Collections.emptyList());
    return result;
  }

  public Map<String, String> compare(
      Data.Table a, Data.Table b, String column, String unitA, String unitB) {
    if (unitA == null || !unitA.equals(unitB)) throw new Failure("UNIT_MISMATCH");
    BigDecimal left = TableService.decimal(a.stats(column).get("sum")),
        right = TableService.decimal(b.stats(column).get("sum"));
    Map<String, String> result = new TreeMap<>();
    result.put("left_sha256", Guard.digest(a.csv()));
    result.put("right_sha256", Guard.digest(b.csv()));
    result.put("sum_delta", right.subtract(left).toPlainString());
    result.put("unit", unitA);
    return result;
  }
}
