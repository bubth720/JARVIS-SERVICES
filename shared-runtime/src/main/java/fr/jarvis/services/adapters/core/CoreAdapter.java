package fr.jarvis.services.adapters.core;

import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts;
import fr.jarvis.services.shared.api.Runtime;
import fr.jarvis.services.shared.tasks.Task;
import java.util.*;

/** Trusted in-process host only. Not exported IPC and not a real CORE connection. */
public final class CoreAdapter {
  private final Runtime runtime;

  public CoreAdapter(Runtime runtime) {
    this.runtime = runtime;
  }

  public Contracts.Response call(Contracts.Request request, Context context, Task task) {
    long start = System.nanoTime();
    try {
      task.check();
      Object result;
      if (request.module.equals("guard") && request.action.equals("health"))
        result = runtime.diagnostics.report(context);
      else if (request.module.equals("documents")
          && request.action.equals("search")
          && request.parameters.size() == 1
          && request.parameters.get("query") instanceof String)
        result = runtime.documents.search(context, (String) request.parameters.get("query"));
      else throw new Contracts.Failure("UNSUPPORTED_CAPABILITY");
      task.check();
      if ((System.nanoTime() - start) / 1_000_000 >= request.timeout)
        throw new Contracts.Failure("EXPIRED");
      return new Contracts.Response(
          request.request_id, result, null, (System.nanoTime() - start) / 1_000_000);
    } catch (Contracts.Failure e) {
      return new Contracts.Response(
          request.request_id, null, e.code, (System.nanoTime() - start) / 1_000_000);
    }
  }
}
