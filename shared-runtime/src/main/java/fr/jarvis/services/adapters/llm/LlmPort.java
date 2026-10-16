package fr.jarvis.services.adapters.llm;

import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.util.*;

public interface LlmPort {
  final class Evidence {
    public final String source_id, revision, quote;

    public Evidence(String id, String revision, String quote) {
      source_id = id;
      this.revision = revision;
      this.quote = quote;
    }
  }

  final class Generation {
    public final String interpretation;
    public final List<Evidence> citations;
    public final boolean simulated;

    public Generation(String text, List<Evidence> citations, boolean simulated) {
      interpretation = text;
      this.citations = citations;
      this.simulated = simulated;
    }
  }

  boolean available();

  Generation generate(String question, List<Evidence> evidence, Task task);

  default void requestStop(String id) {}

  boolean awaitStopped(String id);

  default Set<String> capabilities() {
    return Collections.singleton("structured_evidence");
  }

  final class Mock implements LlmPort {
    public boolean available() {
      return true;
    }

    public boolean awaitStopped(String id) {
      return true;
    }

    public Generation generate(String question, List<Evidence> evidence, Task task) {
      task.check();
      return new Generation(
          "Simulation déterministe : comparer les extraits fournis.",
          new ArrayList<>(evidence),
          true);
    }
  }

  final class Unavailable implements LlmPort {
    public boolean available() {
      return false;
    }

    public boolean awaitStopped(String id) {
      return true;
    }

    public Generation generate(String q, List<Evidence> e, Task task) {
      throw new Failure("MODEL_UNAVAILABLE");
    }
  }
}
