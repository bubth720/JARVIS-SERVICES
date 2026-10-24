package fr.jarvis.services.finalization;

import com.google.gson.*;
import com.google.gson.stream.*;
import fr.jarvis.services.pipeline.DataTransformEngine;
import fr.jarvis.services.shared.api.Contracts.Failure;
import java.io.StringReader;
import java.util.*;

/** Closed version-1 recipes. Duplicate keys/unknown fields never silently overwrite a decision. */
public final class Recipes {
  private Recipes() {}

  public static JsonElement parse(String text) {
    if (text == null || text.length() > 2 * 1024 * 1024) throw new Failure("INPUT_TOO_LARGE");
    try (JsonReader reader = new JsonReader(new StringReader(text))) {
      reader.setStrictness(Strictness.STRICT);
      JsonElement out = value(reader, 0);
      if (reader.peek() != JsonToken.END_DOCUMENT) throw new Failure("INVALID_JSON");
      return out;
    } catch (Failure e) {
      throw e;
    } catch (Exception e) {
      throw new Failure("INVALID_JSON");
    }
  }

  private static JsonElement value(JsonReader r, int depth) throws java.io.IOException {
    if (depth > 8) throw new Failure("INVALID_JSON");
    switch (r.peek()) {
      case BEGIN_OBJECT:
        JsonObject object = new JsonObject();
        r.beginObject();
        while (r.hasNext()) {
          String key = r.nextName();
          if (object.has(key) || object.size() >= 100)
            throw new Failure("DUPLICATE_OR_EXCESS_FIELDS");
          object.add(key, value(r, depth + 1));
        }
        r.endObject();
        return object;
      case BEGIN_ARRAY:
        JsonArray array = new JsonArray();
        r.beginArray();
        while (r.hasNext()) {
          if (array.size() >= 5001) throw new Failure("INPUT_TOO_LARGE");
          array.add(value(r, depth + 1));
        }
        r.endArray();
        return array;
      case STRING:
        return new JsonPrimitive(r.nextString());
      case NUMBER:
        String n = r.nextString();
        TableService.decimal(n);
        return JsonParser.parseString(n);
      case BOOLEAN:
        return new JsonPrimitive(r.nextBoolean());
      case NULL:
        r.nextNull();
        return JsonNull.INSTANCE;
      default:
        throw new Failure("INVALID_JSON");
    }
  }

  private static JsonObject object(JsonElement value) {
    if (!value.isJsonObject()) throw new Failure("INVALID_RECIPE");
    return value.getAsJsonObject();
  }

  private static String string(JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new Failure("INVALID_RECIPE");
    return value.getAsString();
  }

  public static Map<String, String> fields(String json) {
    JsonObject object = object(parse(json));
    if (object.size() > 64) throw new Failure("INVALID_TEMPLATE");
    Map<String, String> out = new TreeMap<>();
    for (Map.Entry<String, JsonElement> e : object.entrySet()) {
      if (!e.getKey().matches("[a-zA-Z][a-zA-Z0-9_]{0,39}")) throw new Failure("INVALID_TEMPLATE");
      out.put(e.getKey(), string(e.getValue()));
    }
    return out;
  }

  public static List<DataTransformEngine.Operation> operations(String json) {
    JsonElement parsed = parse(json);
    if (!parsed.isJsonArray() || parsed.getAsJsonArray().size() > 16)
      throw new Failure("INVALID_RECIPE");
    List<DataTransformEngine.Operation> out = new ArrayList<>();
    for (JsonElement e : parsed.getAsJsonArray()) {
      JsonObject o = object(e);
      if (!new HashSet<>(Arrays.asList("kind", "column", "value")).containsAll(o.keySet())
          || !o.has("kind")) throw new Failure("INVALID_OPERATOR");
      try {
        out.add(
            new DataTransformEngine.Operation(
                DataTransformEngine.Kind.valueOf(string(o.get("kind"))),
                o.has("column") && !o.get("column").isJsonNull() ? string(o.get("column")) : null,
                o.has("value") && !o.get("value").isJsonNull() ? string(o.get("value")) : null));
      } catch (IllegalArgumentException ex) {
        throw new Failure("INVALID_OPERATOR");
      }
    }
    return out;
  }

  public static Map<String, TableService.Column> schema(String json) {
    JsonObject object = object(parse(json));
    Map<String, TableService.Column> out = new TreeMap<>();
    for (Map.Entry<String, JsonElement> e : object.entrySet()) {
      JsonObject column = object(e.getValue());
      if (!new HashSet<>(Arrays.asList("type", "unit")).containsAll(column.keySet())
          || !column.has("type")) throw new Failure("INVALID_SCHEMA");
      try {
        out.put(
            e.getKey(),
            new TableService.Column(
                TableService.Type.valueOf(string(column.get("type"))),
                column.has("unit") ? string(column.get("unit")) : ""));
      } catch (IllegalArgumentException ex) {
        throw new Failure("INVALID_SCHEMA");
      }
    }
    return out;
  }
}
