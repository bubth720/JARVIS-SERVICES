package fr.jarvis.services.android;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import com.google.gson.*;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import fr.jarvis.services.finalization.*;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.modules.memory.Memory;
import fr.jarvis.services.pipeline.*;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import fr.jarvis.services.workflow.WorkflowEngine;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Standalone foreground workspace; file selections and approvals are always Android UI actions. */
public final class WorkspaceActivity extends Activity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean busy = new AtomicBoolean();
  private volatile LocalJobs jobs;
  private AndroidDatabase db;
  private Guard.Context owner;
  private volatile Task running;
  private volatile ToolDispatcher.Handle exportHandle;
  private String selected, sourceName;
  private byte[] source;
  private TextView status, preview;
  private ProgressBar progress;
  private EditText query,
      replacement,
      filterColumn,
      filterValue,
      sortColumn,
      numericColumn,
      groupColumn;
  private Spinner tool, outputFormat, sort, unit;
  private CheckBox normalize, deduplicate;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Gson json = new GsonBuilder().setPrettyPrinting().create();

  private interface Work {
    void run() throws Exception;
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(20, 20, 20, 20);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(body);
    setContentView(scroll);
    status = new TextView(this);
    status.setText(R.string.workspace_initializing);
    body.addView(status);
    tool = spinner(body, R.array.workspace_tools);
    query = field(body, R.string.workspace_query);
    replacement = field(body, R.string.workspace_replacement);
    filterColumn = field(body, R.string.workspace_filter_column);
    filterValue = field(body, R.string.workspace_filter_value);
    sortColumn = field(body, R.string.workspace_sort_column);
    numericColumn = field(body, R.string.workspace_numeric_column);
    groupColumn = field(body, R.string.workspace_group_column);
    sort = spinner(body, R.array.workspace_sort);
    unit = spinner(body, R.array.workspace_units);
    outputFormat = spinner(body, R.array.workspace_formats);
    normalize = new CheckBox(this);
    normalize.setText(R.string.workspace_normalize);
    normalize.setChecked(true);
    body.addView(normalize);
    deduplicate = new CheckBox(this);
    deduplicate.setText(R.string.workspace_deduplicate);
    deduplicate.setChecked(true);
    body.addView(deduplicate);
    button(
        body,
        R.string.workspace_select,
        () -> {
          if (!ready()) return;
          Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
          intent.setType("*/*");
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(intent, 101);
        });
    button(body, R.string.workspace_prepare, this::prepare);
    button(
        body,
        R.string.workspace_advance,
        () -> {
          String id = selected;
          submit(() -> advance(id, null));
        });
    button(
        body,
        R.string.workspace_export,
        () -> {
          if (!ready() || selected == null) return;
          Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
          startActivityForResult(intent, 102);
        });
    button(
        body,
        R.string.workspace_history,
        () ->
            submit(
                () -> {
                  List<Map<String, Object>> history = jobs.history(owner);
                  String[] names = new String[history.size()];
                  for (int i = 0; i < names.length; i++) {
                    Map<String, Object> row = history.get(i);
                    names[i] =
                        row.get("source_name")
                            + " · "
                            + row.get("workflow")
                            + " · "
                            + row.get("publication");
                  }
                  ui(
                      () ->
                          new AlertDialog.Builder(this)
                              .setTitle(R.string.workspace_history)
                              .setItems(
                                  names,
                                  (d, n) -> {
                                    selected = (String) history.get(n).get("id");
                                    submit(this::showSelected);
                                  })
                              .setNegativeButton(R.string.workspace_close, null)
                              .show());
                }));
    button(
        body,
        R.string.workspace_memory_save,
        () -> {
          if (!ready()) return;
          String text = query.getText().toString();
          confirm(
              getString(R.string.workspace_memory_scope) + "\n" + text,
              () ->
                  submit(
                      () -> {
                        Memory memory = new Memory(jobs.store, jobs.guard);
                        String id = UUID.randomUUID().toString();
                        long expires = System.currentTimeMillis() + 30L * 86400000;
                        String exact =
                            memory.exact(id, "preference", text, "standalone-local-ui", expires);
                        memory.save(
                            owner,
                            id,
                            "preference",
                            text,
                            "standalone-local-ui",
                            expires,
                            jobs.guard.confirm(owner, "memory.write", exact));
                        show(getString(R.string.workspace_memory_scope));
                      }));
        });
    button(
        body,
        R.string.workspace_memory_export,
        () ->
            submit(
                () -> {
                  selected =
                      jobs.prepareMemoryExport(owner, new Memory(jobs.store, jobs.guard), running);
                  showSelected();
                }));
    button(
        body,
        R.string.workspace_cancel,
        () -> {
          Task task = running;
          if (task != null) task.cancel();
          if (jobs != null) jobs.cancel();
          ToolDispatcher.Handle handle = exportHandle;
          if (handle != null) handle.cancel();
        });
    button(
        body,
        R.string.workspace_diagnostics,
        () -> startActivity(new Intent(this, MainActivity.class)));
    progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);
    body.addView(progress);
    preview = new TextView(this);
    preview.setTextIsSelectable(true);
    body.addView(preview);
    busy.set(true);
    worker.submit(
        () -> {
          try {
            PDFBoxResourceLoader.init(getApplicationContext());
            File base = new File(getFilesDir(), "workspace");
            if (!base.exists() && !base.mkdirs()) throw new Failure("STORAGE_ERROR");
            db = new AndroidDatabase(new File(base, "journal.sqlite").getPath());
            AndroidPdfText pdf = new AndroidPdfText();
            jobs = new LocalJobs(new File(base, "files").toPath(), db, pdf, pdf);
            owner =
                new Guard.Context(
                    "standalone-local-user",
                    new HashSet<>(
                        Arrays.asList(
                            "confirm",
                            "documents.import",
                            "documents.read",
                            "documents.export",
                            "knowledge.read",
                            "data.read",
                            "analyst.read",
                            "actions.run",
                            "memory.read",
                            "memory.write")));
            jobs.recover(owner);
            List<Map<String, Object>> history = jobs.history(owner);
            if (!history.isEmpty()) {
              selected = (String) history.get(0).get("id");
              showSelected();
            } else show(getString(R.string.workspace_ready));
          } catch (Exception e) {
            error(e);
          } finally {
            busy.set(false);
            ui(() -> status.setText(R.string.workspace_ready));
          }
        });
    tool.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(
              AdapterView<?> parent, android.view.View view, int position, long id) {
            boolean table = position == 3;
            for (android.view.View control :
                new android.view.View[] {
                  filterColumn,
                  filterValue,
                  sortColumn,
                  numericColumn,
                  groupColumn,
                  sort,
                  unit,
                  outputFormat,
                  normalize,
                  deduplicate
                })
              control.setVisibility(table ? android.view.View.VISIBLE : android.view.View.GONE);
            replacement.setVisibility(
                position == 2 ? android.view.View.VISIBLE : android.view.View.GONE);
            query.setVisibility(
                table || position == 1 ? android.view.View.GONE : android.view.View.VISIBLE);
            query.setHint(
                position == 1
                    ? R.string.workspace_template_fields
                    : position == 2
                        ? R.string.workspace_find
                        : position == 4 ? R.string.workspace_text : R.string.workspace_query);
          }

          public void onNothingSelected(AdapterView<?> parent) {}
        });
    handler.post(ticker);
  }

  private EditText field(LinearLayout body, int hint) {
    EditText edit = new EditText(this);
    edit.setHint(hint);
    body.addView(edit);
    return edit;
  }

  private Spinner spinner(LinearLayout body, int array) {
    Spinner spinner = new Spinner(this);
    ArrayAdapter<CharSequence> adapter =
        ArrayAdapter.createFromResource(this, array, android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinner.setAdapter(adapter);
    body.addView(spinner);
    return spinner;
  }

  private void button(LinearLayout body, int label, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    b.setOnClickListener(v -> action.run());
    body.addView(b);
  }

  private boolean ready() {
    if (jobs == null || busy.get()) {
      if (status != null) status.setText(R.string.busy);
      return false;
    }
    return true;
  }

  private void prepare() {
    if (!ready()) return;
    if (tool.getSelectedItemPosition() != 1) {
      prepare(Collections.emptyMap());
      return;
    }
    final byte[] bytes = source == null ? null : source.clone();
    final String name = sourceName;
    submit(
        () -> {
          if (bytes == null) throw new Failure("IMPORT_REQUIRED");
          List<DocumentTextExtractor.Page> pages =
              new DocumentService(new AndroidPdfText()).read(name, bytes, running);
          Set<String> fields = new TreeSet<>();
          java.util.regex.Pattern pattern =
              java.util.regex.Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]{0,39})\\}\\}");
          for (DocumentTextExtractor.Page page : pages) {
            java.util.regex.Matcher matcher = pattern.matcher(page.text);
            while (matcher.find()) {
              fields.add(matcher.group(1));
              if (fields.size() > 64) throw new Failure("INVALID_TEMPLATE");
            }
          }
          if (fields.isEmpty()) throw new Failure("MISSING_TEMPLATE_FIELDS");
          ui(
              () -> {
                LinearLayout form = new LinearLayout(this);
                form.setOrientation(LinearLayout.VERTICAL);
                Map<String, EditText> inputs = new TreeMap<>();
                for (String field : fields) {
                  EditText input = new EditText(this);
                  input.setHint(field);
                  form.addView(input);
                  inputs.put(field, input);
                }
                ScrollView scroll = new ScrollView(this);
                scroll.addView(form);
                new AlertDialog.Builder(this)
                    .setTitle(R.string.workspace_fill_model)
                    .setView(scroll)
                    .setNegativeButton(R.string.workspace_cancel, null)
                    .setPositiveButton(
                        R.string.workspace_prepare,
                        (dialog, which) -> {
                          Map<String, String> values = new TreeMap<>();
                          for (Map.Entry<String, EditText> e : inputs.entrySet())
                            values.put(e.getKey(), e.getValue().getText().toString());
                          prepare(values);
                        })
                    .show();
              });
        });
  }

  private void prepare(Map<String, String> templateFields) {
    if (!ready()) return;
    final int choice = tool.getSelectedItemPosition();
    final String question = query.getText().toString(), edit = replacement.getText().toString();
    final byte[] bytes = source == null ? null : source.clone();
    final String name = sourceName;
    final String sortingColumn = sortColumn.getText().toString();
    final String column = numericColumn.getText().toString(),
        group = groupColumn.getText().toString(),
        filter = filterColumn.getText().toString(),
        value = filterValue.getText().toString();
    final String selectedUnit = (String) unit.getSelectedItem(),
        format = (String) outputFormat.getSelectedItem();
    final int sorting = sort.getSelectedItemPosition();
    final boolean clean = normalize.isChecked(), unique = deduplicate.isChecked();
    submit(
        () -> {
          running.progress("prepare", 10);
          if (choice == 4) selected = jobs.prepareText(owner, question, running);
          else {
            if (bytes == null) throw new Failure("IMPORT_REQUIRED");
            if (choice == 3) {
              List<DataTransformEngine.Operation> recipe = new ArrayList<>();
              if (clean)
                recipe.add(
                    new DataTransformEngine.Operation(
                        DataTransformEngine.Kind.NORMALIZE, null, null));
              if (unique)
                recipe.add(
                    new DataTransformEngine.Operation(
                        DataTransformEngine.Kind.DEDUPLICATE, null, null));
              if (!filter.isEmpty())
                recipe.add(
                    new DataTransformEngine.Operation(
                        DataTransformEngine.Kind.FILTER, filter, value));
              if (sorting > 0)
                recipe.add(
                    new DataTransformEngine.Operation(
                        sorting == 1
                            ? DataTransformEngine.Kind.SORT_TEXT
                            : DataTransformEngine.Kind.SORT_NUMERIC,
                        sortingColumn,
                        null));
              if (!group.isEmpty())
                recipe.add(
                    new DataTransformEngine.Operation(
                        DataTransformEngine.Kind.AGGREGATE, group, column));
              Map<String, TableService.Column> schema = new TreeMap<>();
              if (!column.isEmpty())
                schema.put(
                    column, new TableService.Column(TableService.Type.DECIMAL, selectedUnit));
              selected = jobs.prepareTable(owner, name, bytes, recipe, schema, format, running);
            } else {
              Map<String, String> fields = new TreeMap<>();
              if (choice == 1) fields.putAll(templateFields);
              selected =
                  jobs.prepareDocument(
                      owner,
                      name,
                      bytes,
                      choice == 0 ? question : "",
                      fields,
                      choice == 2 ? question : null,
                      edit,
                      running);
            }
          }
          running.progress("prepared", 100);
          showSelected();
        });
  }

  private void showSelected() {
    if (selected == null) throw new Failure("IMPORT_REQUIRED");
    Map<String, Object> row = jobs.job(owner, selected);
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("source", row.get("source_name"));
    view.put("format", row.get("format"));
    view.put("preview", row.get("text"));
    view.put("differences", row.get("difference"));
    view.put("analysis", row.get("analysis"));
    view.put("workflow", jobs.states.snapshot(selected));
    view.put("publication", row.get("publication"));
    if ("UNKNOWN".equals(row.get("publication"))
        || "UNKNOWN".equals(jobs.states.snapshot(selected).get("status")))
      view.put("diagnostic", getString(R.string.workspace_unknown));
    show(json.toJson(view));
  }

  private void advance(String id, String receipt) {
    if (id == null) throw new Failure("IMPORT_REQUIRED");
    WorkflowEngine.Progress result = jobs.advance(owner, id, receipt);
    selected = id;
    showSelected();
    if (result.approvalRequest != null) {
      ToolInvocation request = result.approvalRequest;
      Map<String, Object> row = jobs.job(owner, id);
      ui(
          () ->
              confirm(
                  getString(
                      R.string.workspace_private_confirmation, row.get("output"), row.get("text")),
                  () -> submit(() -> advance(id, jobs.approve(owner, request)))));
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (request == 101)
      submit(
          () -> {
            DocumentAccess.Imported imported =
                DocumentAccess.read(getContentResolver(), uri, Documents.MAX_BYTES, running);
            source = imported.bytes;
            sourceName = imported.name;
            show(getString(R.string.workspace_source_selected, sourceName));
          });
    if (request == 102) {
      final String id = selected;
      submit(
          () -> {
            SelectedTreeExportPort port = new SelectedTreeExportPort(this, uri);
            ToolInvocation invocation = jobs.exportRequest(owner, id, port);
            Map<String, Object> row = jobs.job(owner, id);
            ui(
                () ->
                    confirm(
                        getString(
                            R.string.workspace_public_confirmation,
                            uri.toString(),
                            row.get("output"),
                            row.get("text")),
                        () ->
                            submit(
                                () -> {
                                  String token = jobs.approve(owner, invocation);
                                  exportHandle = jobs.export(owner, invocation, token);
                                  ToolDispatcher.Result outcome;
                                  try {
                                    outcome = exportHandle.result.get(35000, TimeUnit.MILLISECONDS);
                                  } catch (TimeoutException e) {
                                    exportHandle.cancel();
                                    throw new Failure("EXPORT_OUTCOME_UNKNOWN");
                                  }
                                  exportHandle = null;
                                  showSelected();
                                  if (outcome.status != ToolDispatcher.Status.SUCCESS)
                                    throw new Failure(
                                        outcome.error == null
                                            ? "EXPORT_OUTCOME_UNKNOWN"
                                            : outcome.error);
                                }),
                        () -> jobs.releaseTarget(owner, invocation.arguments.get("target_ref"))));
          });
    }
  }

  private void confirm(String text, Runnable approved) {
    confirm(text, approved, () -> {});
  }

  private void confirm(String text, Runnable approved, Runnable declined) {
    TextView content = new TextView(this);
    content.setText(text);
    content.setPadding(20, 20, 20, 20);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(content);
    new AlertDialog.Builder(this)
        .setTitle(R.string.workspace_confirm)
        .setView(scroll)
        .setNegativeButton(R.string.workspace_cancel, (d, w) -> declined.run())
        .setOnCancelListener(d -> declined.run())
        .setPositiveButton(R.string.workspace_confirm, (d, w) -> approved.run())
        .show();
  }

  private void admission() {
    ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
    ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(info);
    if (info.lowMemory || info.availMem < 128L * 1024 * 1024)
      throw new Failure("INSUFFICIENT_RESOURCES");
    PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
    if (Build.VERSION.SDK_INT >= 29
        && power.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE)
      throw new Failure("THERMAL_REFUSED");
    Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if (battery != null && battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) < 15)
      throw new Failure("BATTERY_REFUSED");
  }

  private void submit(Work work) {
    if (jobs == null || !busy.compareAndSet(false, true)) {
      status.setText(R.string.busy);
      return;
    }
    running = new Task(90000);
    progress.setProgress(0);
    progress.setIndeterminate(true);
    status.setText(R.string.workspace_working);
    worker.submit(
        () -> {
          try {
            admission();
            running.check();
            work.run();
          } catch (Exception e) {
            error(e);
          } finally {
            running = null;
            busy.set(false);
            ui(
                () -> {
                  status.setText(R.string.workspace_ready);
                  progress.setIndeterminate(false);
                });
          }
        });
  }

  private final Runnable ticker =
      new Runnable() {
        public void run() {
          Task task = running;
          if (task != null) {
            List<Map<String, Object>> events = task.events();
            if (!events.isEmpty()) {
              progress.setIndeterminate(false);
              progress.setProgress(
                  ((Number) events.get(events.size() - 1).get("percent")).intValue());
            }
          }
          handler.postDelayed(this, 400);
        }
      };

  private void show(String text) {
    ui(() -> preview.setText(text));
  }

  private void error(Exception e) {
    String code = e instanceof Failure ? ((Failure) e).code : "PROCESSING_ERROR";
    show(getString(R.string.error_status, code));
  }

  private void ui(Runnable action) {
    runOnUiThread(
        () -> {
          if (!isFinishing() && !isDestroyed()) action.run();
        });
  }

  @Override
  protected void onStop() {
    super.onStop();
    Task task = running;
    if (task != null) task.cancel();
    if (jobs != null) jobs.cancel();
    ToolDispatcher.Handle handle = exportHandle;
    if (handle != null) handle.cancel();
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacks(ticker);
    worker.submit(
        () -> {
          if (jobs != null) jobs.close();
          if (db != null) db.close();
        });
    worker.shutdown();
    super.onDestroy();
  }
}
