package fr.jarvis.services.api;

import fr.jarvis.services.tools.ToolDispatcher;
import fr.jarvis.services.tools.ToolInvocation;
import java.util.List;
import java.util.Map;

/** Trusted in-process host only. Arguments carry neither principals nor grants. */
public interface ServicesToolPort extends AutoCloseable {
  String VERSION = "jarvis.services.inprocess/1";
  List<Map<String, Object>> catalogue();
  ToolDispatcher.Handle dispatch(ToolInvocation invocation);
  /** Call only after exact trusted UI confirmation; not exposed to tools/documents/models. */
  String confirm(ToolInvocation invocation);
  void close();
}
