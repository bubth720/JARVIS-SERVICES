package fr.jarvis.services.shared.diagnostics;

import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts;
import fr.jarvis.services.shared.storage.Store;
import java.util.*;

public final class Diagnostics {
  private final Store store;
  private final Guard guard;

  public Diagnostics(Store store, Guard guard) {
    this.store = store;
    this.guard = guard;
  }

  public Map<String, Object> report(Context ctx) {
    guard.require(ctx, "diagnostics.read");
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("schema_version", Contracts.VERSION);
    r.put("storage_integrity", store.integrity());
    r.put("network_required", false);
    Map<String, Object> modules = new LinkedHashMap<>();
    for (String m : Contracts.MODULES) modules.put(m, "available_in_process");
    r.put("modules", modules);
    Map<String, Object> capabilities = new LinkedHashMap<>();
    capabilities.put(
        "documents",
        Arrays.asList(
            "txt",
            "md",
            "pdf_text",
            "docx",
            "csv",
            "json",
            "extractive_summary",
            "compare",
            "export"));
    capabilities.put(
        "knowledge",
        Arrays.asList("incremental_fts5", "limited_concept_vector", "delete", "reindex"));
    capabilities.put(
        "data",
        Arrays.asList(
            "csv_json",
            "filter",
            "sort",
            "deduplicate",
            "normalize",
            "decimal_stats",
            "aggregate",
            "history"));
    capabilities.put(
        "actions",
        Arrays.asList("confirmed_document_export_workflow", "cancel", "crash_recovery_no_replay"));
    capabilities.put(
        "memory",
        Arrays.asList(
            "volatile_session", "confirmed_persistent", "revise", "delete", "export", "retention"));
    capabilities.put(
        "analyst",
        Arrays.asList(
            "evidence_comparison", "verified_citations", "deterministic_calculation", "mock_llm"));
    capabilities.put(
        "guard",
        Arrays.asList("tool_grants", "path_scope", "single_use_content_confirmation", "audit"));
    capabilities.put(
        "bridge", Arrays.asList("authorized_discovery", "versioned_echo_simulator", "disconnect"));
    r.put("capabilities", capabilities);
    r.put("llm", "adapter_only_no_model");
    r.put("core", "simulation_only");
    r.put("bridge", "simulator_only");
    r.put("embeddings", "limited_fr_en_concept_hash_not_neural");
    r.put("extensions_unavailable", Arrays.asList("OCR", "XLSX", "ZIM_driver", "hardware_bridge"));
    r.put("audit", store.all("audit"));
    return r;
  }
}
