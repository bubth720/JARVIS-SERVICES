package fr.jarvis.services.modules.analyst;

import fr.jarvis.services.adapters.llm.LlmPort;
import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.modules.knowledge.Knowledge;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.util.*;

public final class Analyst {
  private final Documents documents;
  private final Guard guard;
  private final LlmPort llm;
  private final Knowledge knowledge;

  public Analyst(Documents documents, Guard guard, LlmPort llm) {
    this(documents, guard, llm, null);
  }

  public Analyst(Documents documents, Guard guard, LlmPort llm, Knowledge knowledge) {
    this.knowledge = knowledge;
    this.documents = documents;
    this.guard = guard;
    this.llm = llm;
  }

  public Map<String, Object> report(Context ctx, String question, List<String> ids, Task task) {
    guard.require(ctx, "analyst.read");
    if (question == null || question.length() > 1000 || ids.isEmpty() || ids.size() > 8)
      throw new Failure("INVALID_INPUT");
    List<LlmPort.Evidence> evidence = new ArrayList<>();
    List<Map<String, Object>> conflicts = new ArrayList<>();
    Map<String, Map<String, String>> values = new LinkedHashMap<>();
    long sourceChars = 0, quotedChars = 0;
    List<Map<String, Object>> differences = new ArrayList<>();
    for (String id : ids) {
      task.check();
      Map<String, Object> d = documents.get(ctx, id);
      String text = (String) d.get("text");
      String quote = text.substring(0, Math.min(text.length(), 600));
      sourceChars += text.length();
      quotedChars += quote.length();
      for (String line : text.split("\n")) {
        int colon = line.indexOf(':');
        if (colon > 0 && colon < 80) {
          String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
              value = line.substring(colon + 1).trim();
          if (!value.isEmpty())
            values.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(id, value);
        }
      }
      evidence.add(new LlmPort.Evidence(id, (String) d.get("text_sha256"), quote));
    }
    for (int i = 1; i < ids.size(); i++)
      differences.add(documents.compare(ctx, ids.get(0), ids.get(i)));
    for (Map.Entry<String, Map<String, String>> entry : values.entrySet())
      if (new HashSet<>(entry.getValue().values()).size() > 1) {
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("kind", "candidate_key_value_conflict");
        conflict.put("key", entry.getKey());
        conflict.put("source_values", entry.getValue());
        conflicts.add(conflict);
      }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("candidate_contradictions", conflicts);
    out.put("question", question);
    out.put("facts", evidence);
    out.put("differences", differences);
    out.put("hypotheses", Collections.emptyList());
    Map<String, Object> uncertainty = new LinkedHashMap<>();
    uncertainty.put(
        "quoted_character_coverage", sourceChars == 0 ? 0 : (double) quotedChars / sourceChars);
    uncertainty.put("confidence", null);
    uncertainty.put(
        "limitation",
        "Quoted prefixes only; key/value conflicts need human interpretation, no calibrated"
            + " probability");
    out.put("uncertainty", uncertainty);
    out.put("calculations", Collections.emptyMap());
    out.put("model_available", llm.available());
    if (llm.available()) {
      LlmPort.Generation g =
          task.heavy(() -> llm.generate(question, Collections.unmodifiableList(evidence), task));
      if (g.interpretation == null || g.interpretation.length() > 10000 || g.citations == null)
        throw new Failure("INVALID_MODEL_OUTPUT");
      for (LlmPort.Evidence c : g.citations) {
        boolean valid = false;
        for (LlmPort.Evidence e : evidence)
          if (e.source_id.equals(c.source_id)
              && e.revision.equals(c.revision)
              && c.quote != null
              && !c.quote.isEmpty()
              && e.quote.contains(c.quote)) valid = true;
        if (!valid) throw new Failure("FABRICATED_REFERENCE");
      }
      out.put("interpretation", g.interpretation);
      out.put("citations", g.citations);
      out.put("simulated", g.simulated);
    } else {
      out.put("interpretation", null);
      out.put("simulated", false);
    }
    return out;
  }

  /** Fixed, inspectable plan; no autonomous tool execution or LLM arithmetic. */
  public Map<String, Object> analyze(
      Context ctx,
      String question,
      String searchTerm,
      Data.Table table,
      String numericColumn,
      Task task) {
    guard.require(ctx, "analyst.read");
    task.check();
    if (knowledge == null) throw new Failure("KNOWLEDGE_UNAVAILABLE");
    List<String> sources = new ArrayList<>();
    for (Map<String, String> hit : knowledge.lexical(ctx, searchTerm, 8))
      sources.add(hit.get("id"));
    if (sources.isEmpty()) throw new Failure("NO_EVIDENCE");
    Map<String, Object> report = report(ctx, question, sources, task);
    List<String> plan =
        new ArrayList<>(
            Arrays.asList(
                "retrieve_fts5",
                "extract_evidence",
                "compare_sources",
                "interpret_with_local_adapter"));
    if (table != null) {
      task.check();
      Map<String, Object> calculation = calculate(ctx, table, numericColumn);
      calculation.put("input_sha256", Guard.digest(table.csv()));
      report.put("calculations", calculation);
      plan.add("calculate_exact_decimals");
    }
    report.put("executed_plan", plan);
    return report;
  }

  public Map<String, Object> calculate(Context ctx, Data.Table table, String column) {
    guard.require(ctx, "analyst.read");
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("kind", "deterministic_calculation");
    r.put("statistics", table.stats(column));
    r.put("history", table.history);
    return r;
  }
}
