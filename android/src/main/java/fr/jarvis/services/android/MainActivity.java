package fr.jarvis.services.android;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.widget.*;
import com.google.gson.GsonBuilder;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import fr.jarvis.services.adapters.llm.LlmPort;
import fr.jarvis.services.modules.actions.Actions;
import fr.jarvis.services.modules.bridge.Bridge;
import fr.jarvis.services.modules.documents.Documents;
import fr.jarvis.services.modules.guard.Guard.Context;
import fr.jarvis.services.shared.api.Contracts;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.api.Runtime;
import fr.jarvis.services.shared.tasks.Task;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean busy = new AtomicBoolean();
  private volatile Task running;
  private Runtime runtime;
  private AndroidDatabase database;
  private Context owner;
  private TextView status, output;
  private EditText query, csv;
  private LinearLayout layout;
  private String selectedId;
  private final List<String> imported = new ArrayList<>();
  private String profile = "ECO";

  private interface Work {
    Object run() throws Exception;
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    ScrollView scroll = new ScrollView(this);
    layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24, 24, 24, 24);
    scroll.addView(layout);
    setContentView(scroll);
    status = new TextView(this);
    status.setText("Initialisation locale — aucun modèle chargé");
    layout.addView(status);
    output = new TextView(this);
    output.setTextIsSelectable(true);
    query = new EditText(this);
    query.setHint("Recherche ou question locale");
    layout.addView(query);
    csv = new EditText(this);
    csv.setHint("CSV à transformer");
    csv.setText("name,value\na,0.1\na,0.1\nb,0.2");
    layout.addView(csv);
    button(
        "Profil ÉCO / ÉQUILIBRÉ / AUTO",
        () -> {
          profile =
              profile.equals("ECO") ? "BALANCED" : profile.equals("BALANCED") ? "AUTO" : "ECO";
          status.setText("Profil " + profile + " — un travail lourd maximum");
        });
    button("États, capacités et journaux", () -> submit(() -> runtime.diagnostics.report(owner)));
    button(
        "Importer un document (SAF)",
        () -> {
          if (runtime == null) return;
          Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
          i.setType("*/*");
          i.addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(i, 1);
        });
    button(
        "Rechercher les documents",
        () -> {
          String q = query.getText().toString();
          submit(() -> runtime.documents.search(owner, q));
        });
    button(
        "Recherche FTS5",
        () -> {
          String q = query.getText().toString();
          submit(() -> runtime.knowledge.lexical(owner, q, 10));
        });
    button(
        "Résumer le dernier document",
        () ->
            submit(
                () -> {
                  requireDocument();
                  return runtime.documents.summarize(owner, selectedId);
                }));
    button(
        "Rapport multi-documents (LLM simulé)",
        () -> {
          String q = query.getText().toString();
          submit(
              () -> {
                if (imported.isEmpty()) throw new Failure("IMPORT_REQUIRED");
                return runtime.analyst.report(
                    owner,
                    q,
                    new ArrayList<>(imported.subList(0, Math.min(8, imported.size()))),
                    running);
              });
        });
    button(
        "Normaliser et dédoublonner CSV",
        () -> {
          String input = csv.getText().toString();
          submit(
              () -> {
                var table = runtime.data.csv(input).normalize().deduplicate();
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("csv", table.csv());
                r.put("history", table.history);
                return r;
              });
        });
    button(
        "Mémoriser une préférence avec confirmation",
        () -> {
          String text = query.getText().toString();
          new AlertDialog.Builder(this)
              .setTitle("Confirmer le contenu exact")
              .setMessage(text)
              .setNegativeButton("Annuler", null)
              .setPositiveButton(
                  "Mémoriser 30 jours",
                  (d, w) ->
                      submit(
                          () -> {
                            String id = UUID.randomUUID().toString();
                            long expires = System.currentTimeMillis() + 30L * 86400000;
                            String exact =
                                runtime.memory.exact(
                                    id, "preference", text, "diagnostic-ui", expires);
                            return runtime.memory.save(
                                owner,
                                id,
                                "preference",
                                text,
                                "diagnostic-ui",
                                expires,
                                runtime.guard.confirm(owner, "memory.write", exact));
                          }))
              .show();
        });
    button("Lire les préférences confirmées", () -> submit(() -> runtime.memory.search(owner, "")));
    button(
        "Créer un DOCX avec confirmation",
        () -> {
          String text = query.getText().toString();
          String path = "diagnostic-" + UUID.randomUUID() + ".docx";
          new AlertDialog.Builder(this)
              .setTitle("Créer " + path)
              .setMessage(text)
              .setNegativeButton("Annuler", null)
              .setPositiveButton(
                  "Créer",
                  (d, w) ->
                      submit(
                          () -> {
                            Map<String, Object> p =
                                runtime.actions.propose(
                                    owner,
                                    Collections.singletonList(
                                        new Actions.Step("document.export", path, "docx", text)));
                            String id = (String) p.get("id");
                            return runtime.actions.run(
                                owner,
                                id,
                                runtime.guard.confirm(
                                    owner, "actions.run", runtime.actions.exact(owner, id)),
                                running);
                          }))
              .show();
        });
    button(
        "BRIDGE : simulateur local",
        () ->
            submit(
                () ->
                    runtime.bridge.send(
                        owner,
                        new Bridge.Message(
                            UUID.randomUUID().toString(), "simulator-local", "echo", "diagnostic"),
                        running)));
    button(
        "Exporter le rapport technique (SAF)",
        () -> {
          if (runtime == null) return;
          Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
          i.setType("application/json");
          i.addCategory(Intent.CATEGORY_OPENABLE);
          i.putExtra(Intent.EXTRA_TITLE, "jarvis-services-diagnostic.json");
          startActivityForResult(i, 2);
        });
    button(
        "Annuler le traitement",
        () -> {
          Task t = running;
          if (t != null) t.cancel();
        });
    layout.addView(output);
    busy.set(true);
    worker.submit(
        () -> {
          try {
            PDFBoxResourceLoader.init(getApplicationContext());
            File home = new File(getFilesDir(), "services");
            if (!home.exists() && !home.mkdirs()) throw new Failure("STORAGE_ERROR");
            database = new AndroidDatabase(new File(home, "services.sqlite").getPath());
            runtime =
                new Runtime(
                    new File(home, "documents").toPath(),
                    database,
                    new AndroidPdfText(),
                    new LlmPort.Mock());
            owner =
                new Context(
                    "diagnostic-user",
                    new HashSet<>(
                        Arrays.asList(
                            "confirm",
                            "documents.import",
                            "documents.read",
                            "documents.export",
                            "knowledge.read",
                            "knowledge.write",
                            "memory.read",
                            "memory.write",
                            "actions.run",
                            "analyst.read",
                            "bridge.discover",
                            "bridge.peer:simulator-local",
                            "diagnostics.read")));
            runtime.actions.recover(owner);
            show(runtime.diagnostics.report(owner));
          } catch (Throwable e) {
            error(e);
          } finally {
            busy.set(false);
          }
        });
  }

  private void button(String label, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    b.setOnClickListener(v -> action.run());
    layout.addView(b);
  }

  private void requireDocument() {
    if (selectedId == null) throw new Failure("IMPORT_REQUIRED");
  }

  private void admission() {
    ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo();
    ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(m);
    if (m.lowMemory || m.availMem < 128L * 1024 * 1024) throw new Failure("INSUFFICIENT_RESOURCES");
    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
    if (Build.VERSION.SDK_INT >= 29
        && pm.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE)
      throw new Failure("THERMAL_REFUSED");
    Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if (battery != null && battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) < 15)
      throw new Failure("BATTERY_REFUSED");
  }

  private void submit(Work work) {
    if (runtime == null || !busy.compareAndSet(false, true)) {
      output.setText("BUSY ou initialisation en cours");
      return;
    }
    running = new Task(30000);
    worker.submit(
        () -> {
          try {
            admission();
            running.check();
            show(work.run());
          } catch (Throwable e) {
            error(e);
          } finally {
            running = null;
            busy.set(false);
          }
        });
  }

  private void show(Object data) {
    String text = new GsonBuilder().setPrettyPrinting().create().toJson(data);
    runOnUiThread(
        () -> {
          if (!isFinishing() && !isDestroyed()) {
            status.setText("Contrats " + Contracts.VERSION + " — " + profile + " — local");
            output.setText(text);
          }
        });
  }

  private void error(Throwable e) {
    String code = e instanceof Failure ? ((Failure) e).code : "PROCESSING_ERROR";
    runOnUiThread(
        () -> {
          if (!isFinishing() && !isDestroyed()) output.setText("Erreur : " + code);
        });
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (request == 1)
      submit(
          () -> {
            String name = "import.txt";
            try (Cursor c =
                getContentResolver()
                    .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
              if (c != null && c.moveToFirst()) name = c.getString(0);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = getContentResolver().openInputStream(uri)) {
              if (in == null) throw new Failure("READ_FAILED");
              byte[] b = new byte[8192];
              int n;
              while ((n = in.read(b)) != -1) {
                running.check();
                int byteLimit = profile.equals("BALANCED") ? Documents.MAX_BYTES : 512 * 1024;
                if (bytes.size() + n > byteLimit) throw new Failure("INPUT_TOO_LARGE");
                bytes.write(b, 0, n);
              }
            }
            Map<String, Object> doc =
                runtime.documents.importBytes(owner, name, bytes.toByteArray(), running);
            runtime.knowledge.index(owner, doc, running);
            selectedId = (String) doc.get("id");
            imported.add(selectedId);
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("source_id", selectedId);
            receipt.put("format", doc.get("format"));
            receipt.put("sha256", doc.get("sha256"));
            return receipt;
          });
    if (request == 2)
      submit(
          () -> {
            String report =
                new GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(runtime.diagnostics.report(owner));
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
              if (out == null) throw new Failure("WRITE_FAILED");
              out.write(report.getBytes(StandardCharsets.UTF_8));
            }
            return "Rapport exporté sans contenu documentaire";
          });
  }

  @Override
  protected void onStop() {
    super.onStop();
    Task t = running;
    if (t != null) t.cancel();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    worker.submit(
        () -> {
          if (database != null) database.close();
        });
    worker.shutdown();
  }
}
