package fr.jarvis.services.bridge;

import com.google.gson.stream.*;
import fr.jarvis.services.shared.api.Contracts.Failure;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict small lab envelope codec: duplicate keys, deep objects and non-integer numbers fail. */
final class LabJson {
  private LabJson() {}
  static Map<String,Object> parse(String json) {
    if (json == null || json.getBytes(StandardCharsets.UTF_8).length > 65536) throw new Failure("INPUT_TOO_LARGE");
    try (JsonReader reader = new JsonReader(new StringReader(json))) {
      reader.setStrictness(com.google.gson.Strictness.STRICT);
      Map<String,Object> value = object(reader, 0);
      if (reader.peek() != JsonToken.END_DOCUMENT) throw new Failure("INVALID_MESSAGE");
      return value;
    } catch (Failure e) { throw e; }
    catch (Exception e) { throw new Failure("INVALID_MESSAGE"); }
  }
  private static Map<String,Object> object(JsonReader reader, int depth) throws java.io.IOException {
    if (depth > 3) throw new Failure("INVALID_MESSAGE");
    reader.beginObject(); Map<String,Object> map = new TreeMap<>();
    while (reader.hasNext()) {
      String key = reader.nextName(); if (map.containsKey(key) || map.size() >= 16) throw new Failure("INVALID_MESSAGE");
      Object value;
      if (reader.peek() == JsonToken.STRING) value = reader.nextString();
      else if (reader.peek() == JsonToken.BEGIN_OBJECT) value = object(reader, depth + 1);
      else if (reader.peek() == JsonToken.NUMBER) {
        String number = reader.nextString(); if (!number.matches("0|[1-9][0-9]{0,15}")) throw new Failure("INVALID_NUMBER");
        value = Long.parseLong(number);
      } else throw new Failure("INVALID_MESSAGE");
      map.put(key, value);
    }
    reader.endObject(); return map;
  }
}
