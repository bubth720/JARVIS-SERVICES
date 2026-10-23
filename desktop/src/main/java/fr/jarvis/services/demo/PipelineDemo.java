package fr.jarvis.services.demo;

import fr.jarvis.services.pipeline.*;
import fr.jarvis.services.platform.*;
import fr.jarvis.services.modules.data.Data;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** Fully synthetic PDF+CSV recipe, reuses the same production pipeline and export writer. */
public final class PipelineDemo {
  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("jarvis-pipeline-synthetic-");
    JdbcDatabase db = new JdbcDatabase(root.resolve("demo.sqlite"));
    try {
      Store store = new Store(db); Guard guard = new Guard(root, store);
      Guard.Context host = new Guard.Context("synthetic-demo", new HashSet<>(Arrays.asList("confirm", "documents.read", "documents.export", "data.read")));
      byte[] pdf;
      try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        PDPage page = new PDPage(); doc.addPage(page);
        try (PDPageContentStream text = new PDPageContentStream(doc, page)) {
          text.beginText(); text.setFont(PDType1Font.HELVETICA, 12); text.newLineAtOffset(50, 750); text.showText("Synthetic source alpha exact evidence"); text.endText();
        }
        doc.save(out); pdf = out.toByteArray();
      }
      DocumentPipeline pipeline = new DocumentPipeline(guard, new DocumentTextExtractor(new PdfBoxText()), new DataTransformEngine(new Data()));
      DocumentPipeline.Result result = pipeline.run(host, List.of(new DocumentPipeline.Source("source.pdf", pdf),
          new DocumentPipeline.Source("values.csv", "name,value\na,0.10\nb,0.20\n".getBytes(StandardCharsets.UTF_8))),
          List.of(new DataTransformEngine.Operation(DataTransformEngine.Kind.STATS, "value", null)), new Task(5000));
      ApprovalGate gate = new ApprovalGate(store, guard);
      ExportWriter writer = new ExportWriter(new Documents(guard, store, new PdfBoxText()), guard, gate);
      for (String format : List.of("md", "docx")) {
        ToolInvocation proposed = new ToolInvocation(UUID.randomUUID().toString(), ExportWriter.DESCRIPTOR.id, "1.0.0",
            Map.of("path", "report." + format, "format", format, "text", result.report.markdown), 3000, null);
        // Synthetic demo approval only. Real hosts must display this exact report and obtain a human decision.
        String receipt = gate.confirm(host, ExportWriter.DESCRIPTOR, proposed);
        ToolInvocation approved = new ToolInvocation(proposed.requestId, proposed.toolId, proposed.version, proposed.arguments, 3000, receipt);
        Map<String,Object> exported = writer.write(host, approved, new Task(3000));
        System.out.println(format + " export verified SHA256=" + exported.get("sha256"));
      }
      String text = Documents.readDocx(Files.readAllBytes(root.resolve("report.docx")));
      if (!text.contains("Synthetic source alpha") || !text.contains("0.30")) throw new IllegalStateException("Readback mismatch");
      System.out.println("PDF+CSV -> " + result.report.citations.size() + " exact citations; decimal sum=0.30; DOCX readback verified");
      System.out.println("Synthetic artifacts: " + root);
    } finally { db.close(); }
  }
}
