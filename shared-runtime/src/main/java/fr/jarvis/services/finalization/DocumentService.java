package fr.jarvis.services.finalization;

import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.pipeline.*;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.util.*;
import java.util.regex.*;

/** Text-only document editing; never claims to preserve an arbitrary DOCX layout. */
public final class DocumentService {
  private final DocumentTextExtractor extractor;

  public DocumentService(DocumentTextExtractor.PdfPages pdf) {
    extractor = new DocumentTextExtractor(pdf);
  }

  public List<DocumentTextExtractor.Page> read(String name, byte[] bytes, Task task) {
    return extractor.extract(name, bytes, task);
  }

  public String template(String template, Map<String, String> fields) {
    bounded(template);
    if (fields == null || fields.size() > 64) throw new Failure("INVALID_TEMPLATE");
    Matcher matcher = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]{0,39})\\}\\}").matcher(template);
    Set<String> used = new HashSet<>();
    StringBuffer out = new StringBuffer();
    while (matcher.find()) {
      String key = matcher.group(1), value = fields.get(key);
      if (value == null) throw new Failure("MISSING_TEMPLATE_FIELD");
      used.add(key);
      matcher.appendReplacement(out, Matcher.quoteReplacement(value));
      if (out.length() > Documents.MAX_TEXT) throw new Failure("TEXT_TOO_LARGE");
    }
    matcher.appendTail(out);
    if (!used.equals(fields.keySet()) || out.indexOf("{{") >= 0 || out.indexOf("}}") >= 0)
      throw new Failure("INVALID_TEMPLATE");
    bounded(out.toString());
    return out.toString();
  }

  public String replace(String text, String literal, String replacement) {
    bounded(text);
    bounded(replacement);
    if (literal == null || literal.isEmpty() || literal.length() > 1000)
      throw new Failure("INVALID_REPLACEMENT");
    if (!text.contains(literal)) throw new Failure("TEXT_NOT_FOUND");
    // Bound expansion before allocating; literal replacement is never a regex or a script.
    int count = 0, from = 0, at;
    while ((at = text.indexOf(literal, from)) >= 0) {
      count++;
      from = at + literal.length();
    }
    if ((long) text.length() + (long) count * (replacement.length() - literal.length())
        > Documents.MAX_TEXT) throw new Failure("TEXT_TOO_LARGE");
    return text.replace(literal, replacement);
  }

  public static void bounded(String text) {
    if (text == null || text.length() > Documents.MAX_TEXT) throw new Failure("TEXT_TOO_LARGE");
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if ((c < 32 && c != '\n' && c != '\r' && c != '\t') || c == 0xfffe || c == 0xffff)
        throw new Failure("INVALID_TEXT");
      if (Character.isHighSurrogate(c)) {
        if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))
          throw new Failure("INVALID_TEXT");
      } else if (Character.isLowSurrogate(c)) throw new Failure("INVALID_TEXT");
    }
  }
}
