package fr.jarvis.services.finalization;

import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.modules.knowledge.Knowledge;
import fr.jarvis.services.pipeline.DocumentTextExtractor;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.util.*;

/** Reuses the existing lexical lookup contract. Bindings are supplied by the trusted caller. */
public final class ExistingKnowledgeAdapter implements ConsultationService.SearchPort {
  public static final class Binding {
    final String sourceSha256, indexRevision;

    public Binding(String sourceSha256, String indexRevision) {
      if (sourceSha256 == null
          || indexRevision == null
          || !sourceSha256.matches("[a-f0-9]{64}")
          || !indexRevision.matches("[a-f0-9]{64}")) throw new Failure("INVALID_SOURCE");
      this.sourceSha256 = sourceSha256;
      this.indexRevision = indexRevision;
    }
  }

  private final Knowledge knowledge;
  private final Context host;
  private final Map<String, Binding> bindings;

  public ExistingKnowledgeAdapter(
      Knowledge existing, Context trustedHost, Map<String, Binding> allowedBindings) {
    if (existing == null
        || trustedHost == null
        || allowedBindings == null
        || allowedBindings.size() > 128
        || allowedBindings.values().stream().anyMatch(Objects::isNull))
      throw new Failure("INVALID_INPUT");
    knowledge = existing;
    host = trustedHost;
    bindings = Collections.unmodifiableMap(new HashMap<>(allowedBindings));
  }

  public List<ConsultationService.Passage> search(
      String query, List<DocumentTextExtractor.Page> selected, Task task) {
    if (query.isEmpty())
      return new ConsultationService.SelectedFilesSearch().search(query, selected, task);
    Set<String> permitted = new HashSet<>();
    for (Map<String, String> hit : knowledge.lexical(host, query, 20)) {
      task.check();
      Binding binding = bindings.get(hit.get("id"));
      if (binding == null) continue;
      if (!binding.indexRevision.equals(hit.get("source_revision")))
        throw new Failure("STALE_SEARCH_REVISION");
      permitted.add(binding.sourceSha256);
    }
    List<DocumentTextExtractor.Page> matched = new ArrayList<>();
    for (DocumentTextExtractor.Page p : selected)
      if (permitted.contains(p.sourceSha256)) matched.add(p);
    return new ConsultationService.SelectedFilesSearch().search(query, matched, task);
  }
}
