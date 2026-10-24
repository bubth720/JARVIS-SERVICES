package fr.jarvis.services.pipeline;

import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.util.*;

/** Closed recipes made of existing exact decimal/table operators, never script text. */
public final class DataTransformEngine {
  public enum Kind { NORMALIZE, DEDUPLICATE, FILTER, SORT_TEXT, SORT_NUMERIC, STATS, AGGREGATE }
  public static final class Operation {
    public final Kind kind; public final String column, value;
    public Operation(Kind kind, String column, String value) {
      if (kind == null || (column != null && column.length() > 200)
          || (value != null && value.length() > 1000)) throw new Failure("INVALID_OPERATOR");
      this.kind = kind; this.column = column; this.value = value;
    }
  }
  public static final class Result {
    public final Data.Table table; public final List<Map<String,String>> summaries;
    Result(Data.Table table, List<Map<String,String>> summaries) {
      this.table = table; this.summaries = Collections.unmodifiableList(summaries);
    }
  }
  private final Data data;
  public DataTransformEngine(Data data) { this.data = data; }
  public Result transform(String csv, List<Operation> operations, Task task) {
    if (operations == null || operations.size() > 16) throw new Failure("INVALID_RECIPE");
    Data.Table table = data.csv(csv); List<Map<String,String>> summaries = new ArrayList<>();
    for (Operation op : operations) {
      task.check(); if (op == null) throw new Failure("INVALID_OPERATOR");
      switch (op.kind) {
        case NORMALIZE: table = table.normalize(); break;
        case DEDUPLICATE: table = table.deduplicate(); break;
        case FILTER: if (op.value == null) throw new Failure("INVALID_OPERATOR"); table = table.filter(op.column, op.value); break;
        case SORT_TEXT: table = table.sort(op.column, false); break;
        case SORT_NUMERIC: table = table.sort(op.column, true); break;
        case STATS: summaries.add(Collections.unmodifiableMap(table.stats(op.column))); break;
        case AGGREGATE: summaries.add(Collections.unmodifiableMap(table.aggregate(op.column, op.value))); break;
        default: throw new Failure("INVALID_OPERATOR");
      }
    }
    task.check(); return new Result(table, summaries);
  }
}
