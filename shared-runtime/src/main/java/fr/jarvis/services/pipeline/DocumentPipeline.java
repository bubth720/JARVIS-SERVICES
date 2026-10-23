package fr.jarvis.services.pipeline;

import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import java.text.Normalizer;
import java.util.*;

/** Closed, bounded recipes over caller-selected sources. No model or imported scripts. */
public final class DocumentPipeline {
  public enum Stage { EXTRACT, NORMALIZE, SEGMENT, TRANSFORM, REPORT }
  public static final class Source {
    public final String name; private final byte[] bytes;
    public Source(String name, byte[] bytes) {
      if (name == null || bytes == null || bytes.length > Documents.MAX_BYTES) throw new Failure("INVALID_SOURCE");
      this.name = name; this.bytes = bytes.clone();
    }
  }
  public static final class Fragment {
    public final DocumentTextExtractor.Page source;
    public final int start, end; public final String normalized;
    Fragment(DocumentTextExtractor.Page source, int start, int end) {
      this.source = source; this.start = start; this.end = end;
      normalized = Normalizer.normalize(source.text.substring(start, end).replace("\r\n", "\n"), Normalizer.Form.NFC);
    }
    public String exactQuote() { return source.text.substring(start, end); }
  }
  public static final class JobFailure extends RuntimeException {
    public final Stage stage; public final String code;
    JobFailure(Stage stage, String code) { super(code); this.stage = stage; this.code = code; }
  }
  public static final class Result {
    public final String jobId;
    public final EvidenceReportBuilder.Report report;
    public final List<Fragment> fragments;
    public final List<DataTransformEngine.Result> tables;
    public final List<Map<String,Object>> progress;
    Result(Task task, EvidenceReportBuilder.Report report, List<Fragment> fragments, List<DataTransformEngine.Result> tables) {
      jobId = task.id; this.report = report;
      this.fragments = Collections.unmodifiableList(fragments);
      this.tables = Collections.unmodifiableList(tables);
      progress = Collections.unmodifiableList(task.events());
    }
  }
  private final Guard guard;
  private final DocumentTextExtractor extractor;
  private final DataTransformEngine transforms;
  public DocumentPipeline(Guard guard, DocumentTextExtractor extractor, DataTransformEngine transforms) {
    this.guard = guard; this.extractor = extractor; this.transforms = transforms;
  }
  public Result run(Context host, List<Source> sources, List<DataTransformEngine.Operation> recipe, Task task) {
    guard.require(host, "documents.read");
    if (sources == null || sources.isEmpty() || sources.size() > 8 || recipe == null || recipe.size() > 16)
      throw new Failure("INVALID_RECIPE");
    List<Source> inputs = new ArrayList<>(sources);
    List<DataTransformEngine.Operation> operations = new ArrayList<>(recipe);
    long total = 0; for (Source source : inputs) { if (source == null) throw new Failure("INVALID_SOURCE"); total += source.bytes.length; }
    if (total > Documents.MAX_BYTES) throw new Failure("INPUT_TOO_LARGE");
    Stage[] stage = {Stage.EXTRACT};
    try {
      return task.heavy(() -> {
        task.progress("extract", 0);
        List<DocumentTextExtractor.Page> pages = new ArrayList<>(); long chars = 0;
        for (Source source : inputs) {
          task.check(); List<DocumentTextExtractor.Page> extracted = extractor.extract(source.name, source.bytes, task);
          for (DocumentTextExtractor.Page page : extracted) { chars += page.text.length(); if (chars > Documents.MAX_TEXT) throw new Failure("TEXT_TOO_LARGE"); }
          pages.addAll(extracted); if (pages.size() > 100) throw new Failure("SOURCE_LIMIT");
        }
        stage[0] = Stage.NORMALIZE; task.progress("normalize", 25);
        // Raw source remains immutable. Only derived fragment text is NFC/newline-normalized.
        List<Fragment> fragments = new ArrayList<>();
        stage[0] = Stage.SEGMENT; task.progress("segment", 40);
        for (DocumentTextExtractor.Page page : pages) {
          for (int start = 0; start < page.text.length();) {
            task.check(); int end = Math.min(start + 600, page.text.length());
            if (end < page.text.length() && Character.isHighSurrogate(page.text.charAt(end - 1))) end--;
            fragments.add(new Fragment(page, start, end)); start = end;
          }
        }
        stage[0] = Stage.TRANSFORM; task.progress("transform", 65);
        List<DataTransformEngine.Result> tables = new ArrayList<>();
        for (DocumentTextExtractor.Page page : pages) {
          if (page.sourceName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            guard.require(host, "data.read"); tables.add(transforms.transform(page.text, operations, task));
          }
        }
        stage[0] = Stage.REPORT; task.progress("report", 85);
        EvidenceReportBuilder.Report report = new EvidenceReportBuilder().build(pages, tables);
        task.progress("complete", 100);
        return new Result(task, report, fragments, tables);
      });
    } catch (Failure failure) { throw new JobFailure(stage[0], failure.code); }
    catch (RuntimeException failure) { throw new JobFailure(stage[0], "PROCESSING_ERROR"); }
  }
}
