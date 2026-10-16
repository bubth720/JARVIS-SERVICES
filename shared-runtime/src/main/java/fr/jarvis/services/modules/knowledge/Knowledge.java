package fr.jarvis.services.modules.knowledge;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import java.util.*;

public final class Knowledge {
  public interface Embeddings {
    String version();

    double[] embed(String text);
  }

  /** Offline feature hashing baseline; NOT a neural semantic model. Replace through port. */
  public static final class HashEmbeddings implements Embeddings {
    public String version() {
      return "lexical-hash-64-v1";
    }

    public double[] embed(String text) {
      double[] v = new double[64];
      for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
        if (!word.isEmpty()) v[Math.floorMod(word.hashCode(), 64)]++;
      return v;
    }
  }

  /** Small, explicit FR/EN concept dictionary; limited semantic baseline, not a neural model. */
  public static final class ConceptEmbeddings implements Embeddings {
    private final Map<String, String> concepts = new HashMap<>();

    public ConceptEmbeddings() {
      for (String group :
          Arrays.asList(
              "car automobile voiture auto",
              "house home maison domicile",
              "document fichier file",
              "cat chat feline",
              "dog chien canine",
              "purchase buy achat acheter",
              "price cost prix cout",
              "memory souvenir memoire",
              "cancel annuler cancellation annulation",
              "search chercher recherche",
              "delete supprimer suppression",
              "save enregistrer sauvegarder")) {
        String[] words = group.split(" ");
        for (String word : words) concepts.put(word, words[0]);
      }
    }

    public String version() {
      return "concept-hash-64-v1";
    }

    public double[] embed(String text) {
      String normalized =
          java.text.Normalizer.normalize(
                  text.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
              .replaceAll("\\p{M}", "");
      StringBuilder canonical = new StringBuilder();
      for (String token : normalized.split("[^\\p{L}\\p{N}]+"))
        canonical.append(concepts.getOrDefault(token, token)).append(' ');
      return new HashEmbeddings().embed(canonical.toString());
    }
  }

  public interface ZimArchive {
    List<Map<String, String>> search(String query, int limit);
  }

  private final Store store;
  private final Guard guard;
  private final Embeddings embeddings;

  public Knowledge(Store store, Guard guard, Embeddings embeddings) {
    this.store = store;
    this.guard = guard;
    this.embeddings = embeddings;
  }

  public boolean index(Context ctx, Map<String, Object> doc, Task task) {
    guard.require(ctx, "knowledge.write");
    return task.heavy(
        () -> {
          String id = (String) doc.get("id"), text = (String) doc.get("text");
          if (id == null || text == null || text.length() > 250000)
            throw new Failure("INVALID_INPUT");
          Map<String, Object> old = store.get("knowledge", id);
          String hash = Guard.digest(text);
          if (old != null
              && hash.equals(old.get("sha256"))
              && embeddings.version().equals(old.get("embedding_version"))) return false;
          task.check();
          Map<String, Object> record = new LinkedHashMap<>();
          record.put("id", id);
          record.put("text", text);
          record.put("sha256", hash);
          record.put("source_name", doc.get("name"));
          record.put("embedding_version", embeddings.version());
          record.put("vector", embeddings.embed(text));
          store.transaction(
              () -> {
                store.put("knowledge", id, record);
                store.index(id, text);
              });
          return true;
        });
  }

  public List<Map<String, String>> lexical(Context ctx, String query, int limit) {
    guard.require(ctx, "knowledge.read");
    List<Map<String, String>> hits = store.search(query, limit);
    for (Map<String, String> hit : hits) {
      Map<String, Object> record = store.get("knowledge", hit.get("id"));
      if (record != null) hit.put("source_revision", (String) record.get("sha256"));
    }
    return hits;
  }

  public List<Map<String, Object>> vector(Context ctx, String query, int limit, Task task) {
    guard.require(ctx, "knowledge.read");
    if (limit < 1 || limit > 50 || query.length() > 500) throw new Failure("INVALID_INPUT");
    return task.heavy(
        () -> {
          double[] q = embeddings.embed(query);
          List<Map<String, Object>> out = new ArrayList<>();
          for (Map<String, Object> r : store.scan("knowledge")) {
            task.check();
            if (!embeddings.version().equals(r.get("embedding_version"))) continue;
            List<?> stored = (List<?>) r.get("vector");
            if (stored.size() != q.length) throw new Failure("INCOMPATIBLE_VECTOR");
            double dot = 0, aa = 0, bb = 0;
            for (int i = 0; i < q.length; i++) {
              double v = ((Number) stored.get(i)).doubleValue();
              dot += q[i] * v;
              aa += q[i] * q[i];
              bb += v * v;
            }
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("source_id", r.get("id"));
            hit.put("source_revision", r.get("sha256"));
            hit.put("score", aa * bb == 0 ? 0 : dot / Math.sqrt(aa * bb));
            hit.put("embedding_version", embeddings.version());
            hit.put(
                "quote",
                ((String) r.get("text"))
                    .substring(0, Math.min(300, ((String) r.get("text")).length())));
            out.add(hit);
          }
          out.sort(
              (a, b) ->
                  Double.compare(
                      ((Number) b.get("score")).doubleValue(),
                      ((Number) a.get("score")).doubleValue()));
          return new ArrayList<>(out.subList(0, Math.min(limit, out.size())));
        });
  }

  public void delete(Context ctx, String id, String token) {
    guard.consume(ctx, "knowledge.write", id, token);
    store.transaction(
        () -> {
          store.delete("knowledge", id);
          store.unindex(id);
        });
  }

  public void rebuild(Context ctx, Task task) {
    guard.require(ctx, "knowledge.write");
    task.heavy(
        () -> {
          for (Map<String, Object> r : store.scan("knowledge")) {
            task.check();
            r.put("embedding_version", embeddings.version());
            r.put("vector", embeddings.embed((String) r.get("text")));
            store.put("knowledge", (String) r.get("id"), r);
            store.index((String) r.get("id"), (String) r.get("text"));
          }
          return null;
        });
  }
}
