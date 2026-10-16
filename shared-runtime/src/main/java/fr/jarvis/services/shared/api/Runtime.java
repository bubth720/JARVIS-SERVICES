package fr.jarvis.services.shared.api;

import fr.jarvis.services.adapters.llm.LlmPort;
import fr.jarvis.services.modules.actions.Actions;
import fr.jarvis.services.modules.analyst.Analyst;
import fr.jarvis.services.modules.bridge.Bridge;
import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.knowledge.Knowledge;
import fr.jarvis.services.modules.memory.Memory;
import fr.jarvis.services.shared.diagnostics.Diagnostics;
import fr.jarvis.services.shared.storage.*;
import java.nio.file.Path;

public final class Runtime {
  public final Guard guard;
  public final Documents documents;
  public final Knowledge knowledge;
  public final Data data;
  public final Memory memory;
  public final Actions actions;
  public final Analyst analyst;
  public final Bridge bridge;
  public final Diagnostics diagnostics;
  public final Recovery recovery;

  public Runtime(Path root, Database database, Documents.PdfText pdf, LlmPort llm) {
    Store store = new Store(database);
    guard = new Guard(root, store);
    documents = new Documents(guard, store, pdf);
    knowledge = new Knowledge(store, guard, new Knowledge.ConceptEmbeddings());
    data = new Data();
    memory = new Memory(store, guard);
    actions = new Actions(store, guard, documents);
    analyst = new Analyst(documents, guard, llm, knowledge);
    bridge = new Bridge(guard, new Bridge.Simulator());
    diagnostics = new Diagnostics(store, guard);
    recovery = new Recovery(store, guard);
  }
}
