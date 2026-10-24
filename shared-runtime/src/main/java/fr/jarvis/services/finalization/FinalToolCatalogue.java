package fr.jarvis.services.finalization;

import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.memory.Memory;
import fr.jarvis.services.pipeline.DocumentTextExtractor;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.tools.*;
import java.util.*;

/**
 * Additional reusable v1 tools; no tool accepts a Context, executable code or a permission grant.
 */
public final class FinalToolCatalogue {
  private FinalToolCatalogue() {}

  public static void register(
      ToolRegistry registry,
      Guard guard,
      DocumentService documents,
      ConsultationService consultation,
      Memory memory) {
    registry.register(
        ServicesTools.descriptor(
            "documents.template.render",
            "documents",
            "documents.read",
            false,
            "derived_text_v1",
            "template",
            "fields"),
        (ctx, args, task) -> {
          task.check();
          return documents.template(args.get("template"), Recipes.fields(args.get("fields")));
        });
    registry.register(
        ServicesTools.descriptor(
            "documents.text.replace",
            "documents",
            "documents.read",
            false,
            "derived_text_v1",
            "text",
            "literal",
            "replacement"),
        (ctx, args, task) -> {
          task.check();
          return documents.replace(args.get("text"), args.get("literal"), args.get("replacement"));
        });
    registry.register(
        ServicesTools.descriptor(
            "knowledge.selected.consult",
            "knowledge",
            "knowledge.read",
            false,
            "verified_passages_v1",
            "name",
            "text",
            "query"),
        (ctx, args, task) -> {
          guard.require(ctx, "documents.read");
          List<DocumentTextExtractor.Page> pages =
              documents.read(
                  args.get("name"),
                  args.get("text").getBytes(java.nio.charset.StandardCharsets.UTF_8),
                  task);
          return consultation.consult(ctx, args.get("query"), pages, task);
        });
    registry.register(
        ServicesTools.descriptor(
            "data.table.transform",
            "data",
            "data.read",
            false,
            "table_preview_v1",
            "csv",
            "recipe",
            "schema",
            "format"),
        (ctx, args, task) -> {
          TableService.Preview result =
              new TableService()
                  .transform(
                      args.get("csv"),
                      Recipes.operations(args.get("recipe")),
                      Recipes.schema(args.get("schema")),
                      task);
          String format = args.get("format");
          if (!Arrays.asList("csv", "json").contains(format))
            throw new Failure("UNSUPPORTED_FORMAT");
          Map<String, Object> out = new TreeMap<>();
          out.put("preview", result);
          out.put("output", format.equals("csv") ? result.after.csv() : result.after.json());
          return out;
        });
    registry.register(
        ServicesTools.descriptor(
            "analyst.table.report",
            "analyst",
            "analyst.read",
            false,
            "sourced_calculation_v1",
            "csv",
            "schema"),
        (ctx, args, task) -> {
          guard.require(ctx, "data.read");
          return new StructuredAnalyst()
              .analyze(new Data().csv(args.get("csv")), Recipes.schema(args.get("schema")), task);
        });
    registry.register(
        ServicesTools.descriptor(
            "analyst.table.inspect",
            "analyst",
            "analyst.read",
            false,
            "schema_inconsistencies_v1",
            "csv",
            "schema"),
        (ctx, args, task) -> {
          guard.require(ctx, "data.read");
          return new StructuredAnalyst()
              .inspect(new Data().csv(args.get("csv")), Recipes.schema(args.get("schema")), task);
        });
    registry.register(
        ServicesTools.descriptor(
            "memory.local.read", "memory", "memory.read", false, "local_memory_v1", "query"),
        (ctx, args, task) -> {
          task.check();
          return memory.search(ctx, args.get("query"));
        });
    registry.register(
        ServicesTools.descriptor(
            "memory.local.save",
            "memory",
            "memory.write",
            true,
            "local_memory_receipt_v1",
            "id",
            "type",
            "text",
            "source",
            "expires"),
        (ctx, args, task) -> {
          task.check();
          long expires;
          try {
            expires = Long.parseLong(args.get("expires"));
          } catch (NumberFormatException e) {
            throw new Failure("INVALID_MEMORY");
          }
          String exact =
              memory.exact(
                  args.get("id"), args.get("type"), args.get("text"), args.get("source"), expires);
          return memory.save(
              ctx,
              args.get("id"),
              args.get("type"),
              args.get("text"),
              args.get("source"),
              expires,
              guard.confirm(ctx, "memory.write", exact));
        });
    registry.register(
        ServicesTools.descriptor(
            "guard.workspace.validate",
            "guard",
            "documents.read",
            false,
            "safe_destination_v1",
            "relative"),
        (ctx, args, task) -> {
          task.check();
          guard.path(ctx, "documents.read", args.get("relative"));
          return Collections.singletonMap("safe_private_destination", true);
        });
    registry.register(
        ServicesTools.descriptor(
            "bridge.offline.capabilities",
            "bridge",
            "bridge.file",
            false,
            "offline_protocol_capabilities_v1"),
        (ctx, args, task) -> {
          task.check();
          Map<String, Object> out = new TreeMap<>();
          out.put("protocol", fr.jarvis.services.bridge.OfflineBridge.VERSION);
          out.put("network_transport_enabled", false);
          out.put("remote_execution_enabled", false);
          out.put("requires_host_provisioned_pairing_key", true);
          return out;
        });
  }
}
