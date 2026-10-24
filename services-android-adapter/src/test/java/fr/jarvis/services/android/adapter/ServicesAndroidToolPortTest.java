package fr.jarvis.services.android.adapter;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.*;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import androidx.test.core.app.ApplicationProvider;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import fr.jarvis.services.modules.guard.AccessGuard;
import fr.jarvis.services.pipeline.EvidenceReportBuilder;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.tasks.Task;
import fr.jarvis.services.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

/** Host Android API simulation; PDFBox and SQLite execute, no physical-device claim. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24,25,26,35})
public class ServicesAndroidToolPortTest {
  private Context context;
  private ServicesAndroidToolPort port;
  private SyntheticProvider provider;
  @Before public void open() {
    context = ApplicationProvider.getApplicationContext();
    context.deleteDatabase("jarvis_serv04a_journal_v1.db");
    provider = new SyntheticProvider(); ProviderInfo info = new ProviderInfo(); info.authority = "serv04a.fixture";
    provider.attachInfo(context, info);
    ShadowContentResolver.registerProviderInternal(info.authority, provider);
    port = new ServicesAndroidToolPort(context, "synthetic-host", grants("documents.read", "data.read", "documents.export", "confirm"));
  }
  @After public void close() throws Exception { if (port != null) port.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS); }
  private static Set<String> grants(String... values) { return new HashSet<>(Arrays.asList(values)); }
  private ToolInvocation call(String id, String field, String value) {
    return new ToolInvocation(UUID.randomUUID().toString(), id, "1.0.0", Collections.singletonMap(field,value), 10000, null);
  }
  private ToolDispatcher.Result result(ToolInvocation invocation) throws Exception {
    return port.dispatch(invocation).result.get(10, TimeUnit.SECONDS);
  }
  @Test public void csvUsesExistingAlgorithmAndDurableReceiptsWithoutBody() throws Exception {
    String csv = "name,value\n a ,0.10\n a ,0.10\n";
    ToolInvocation invocation = call("data.csv.normalize", "csv", csv);
    ToolDispatcher.Result result = result(invocation);
    assertEquals(ToolDispatcher.Status.SUCCESS, result.status);
    assertEquals("\"name\",\"value\"\r\n\"a\",\"0.10\"\r\n", result.value);
    assertEquals("SUCCESS", port.receipts().get(0).get("state"));
    assertFalse(port.receipts().toString().contains(csv));
    assertEquals("DUPLICATE_REQUEST", Assert.assertThrows(Failure.class, () -> port.dispatch(invocation)).code);
    port.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
    port = new ServicesAndroidToolPort(context,"synthetic-host",grants("data.read"));
    assertEquals("DUPLICATE_REQUEST", Assert.assertThrows(Failure.class, () -> port.dispatch(invocation)).code);
    assertEquals(ToolDispatcher.Status.SUCCESS, result(call("data.csv.normalize", "csv", "x\n1\n")).status);
  }
  @Test public void permissionsNeverComeFromToolArgumentsAndRefusalsAreJournalled() throws Exception {
    port.closeAsync().toCompletableFuture().get(5,TimeUnit.SECONDS);
    port = new ServicesAndroidToolPort(context,"denied-host",Collections.emptySet());
    assertEquals("UNAUTHORIZED", result(call("data.csv.normalize","csv","x\n1\n")).error);
    assertEquals("UNAUTHORIZED", Assert.assertThrows(Failure.class, () -> port.selectSource(Uri.parse("content://serv04a.fixture/text.txt"),new Task(1000))).code);
    assertFalse(port.audit().isEmpty());
    assertFalse(port.audit().toString().contains("denied-host"));
  }
  @Test public void selectedTextSearchPreservesProvenanceOffsetsAndRejectsDuplicate() throws Exception {
    Uri uri = provider.add("text.txt", "Préface 😀 alpha synthétique".getBytes(StandardCharsets.UTF_8));
    String sha = port.selectSource(uri,new Task(10000));
    ToolDispatcher.Result result = result(call("documents.search","query","alpha"));
    assertEquals(ToolDispatcher.Status.SUCCESS,result.status);
    List<?> hits = (List<?>) result.value; assertEquals(1,hits.size());
    EvidenceReportBuilder.Citation c = (EvidenceReportBuilder.Citation) hits.get(0);
    assertEquals(sha,c.sourceSha256); assertEquals(0,c.page); assertEquals(11,c.start);
    assertEquals("alpha synthétique",c.quote);
    assertEquals("DUPLICATE_SOURCE", Assert.assertThrows(Failure.class, () -> port.selectSource(uri,new Task(10000))).code);
  }
  @Test public void realTwoPagePdfExtractionReturnsCorrectPageAndSha() throws Exception {
    PDFBoxResourceLoader.init(context); byte[] bytes;
    try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      for (String text : new String[]{"first synthetic page", "alpha second synthetic page"}) {
        PDPage page = new PDPage(); pdf.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(pdf,page)) {
          stream.beginText(); stream.setFont(PDType1Font.HELVETICA,12); stream.newLineAtOffset(20,700); stream.showText(text); stream.endText();
        }
      }
      pdf.save(out); bytes = out.toByteArray();
    }
    port.selectSource(provider.add("synthetic.pdf",bytes),new Task(30000));
    List<?> hits = (List<?>) result(call("documents.search","query","alpha")).value;
    assertEquals(1,hits.size()); EvidenceReportBuilder.Citation c = (EvidenceReportBuilder.Citation) hits.get(0);
    assertEquals(2,c.page); assertEquals(AccessGuard.digest(bytes),c.sourceSha256); assertTrue(c.quote.startsWith("alpha"));
  }
  @Test public void cancellationBoundsUriRefusalsAndDocxFailClosed() {
    Task cancelled = new Task(1000); cancelled.cancel();
    assertEquals("CANCELLED", Assert.assertThrows(Failure.class, () -> port.selectSource(provider.add("cancel.txt",new byte[]{1}),cancelled)).code);
    assertEquals("INVALID_URI", Assert.assertThrows(Failure.class, () -> port.selectSource(Uri.parse("file:///private.txt"),new Task(1000))).code);
    assertEquals("URI_DENIED", Assert.assertThrows(Failure.class, () -> port.selectSource(Uri.parse("content://serv04a.fixture/denied.txt"),new Task(1000))).code);
    assertEquals("INPUT_TOO_LARGE", Assert.assertThrows(Failure.class, () -> port.selectSource(provider.add("huge.txt",new byte[2*1024*1024+1]),new Task(10000))).code);
    assertEquals("SAF_EXPORT_UNQUALIFIED", Assert.assertThrows(Failure.class, () -> port.exportDocx(call("documents.docx.export","text","synthetic"))).code);
    assertEquals("disabled:SAF_EXPORT_UNQUALIFIED",port.capabilities().get("docx_export"));
  }
  @Test public void rejectsConcurrentJournalOwnerUntilConfirmedClosure() throws Exception {
    assertEquals("JOURNAL_BUSY",Assert.assertThrows(Failure.class,()->new ServicesAndroidToolPort(context,"second-host",grants("data.read"))).code);
    port.closeAsync().toCompletableFuture().get(5,TimeUnit.SECONDS);
    port=new ServicesAndroidToolPort(context,"second-host",grants("data.read"));
    assertEquals(ToolDispatcher.Status.SUCCESS,result(call("data.csv.normalize","csv","x\n1\n")).status);
  }
  @Test public void interruptedReceiptBecomesUnknownAndIsNeverReplayed() throws Exception {
    port.closeAsync().toCompletableFuture().get(5,TimeUnit.SECONDS);
    ToolInvocation invocation=call("data.csv.normalize","csv","x\n1\n");
    JournalDatabase db = new JournalDatabase(context);
    fr.jarvis.services.shared.storage.Store store = fr.jarvis.services.shared.storage.Store.journal(db);
    Map<String,Object> row=new LinkedHashMap<>(); row.put("id",invocation.requestId);row.put("state","ACCEPTED");
    store.put("android_requests",invocation.requestId,row); db.close();
    port=new ServicesAndroidToolPort(context,"synthetic-host",grants("data.read"));
    assertEquals("UNKNOWN",port.receipts().get(0).get("state"));
    assertEquals("DUPLICATE_REQUEST",Assert.assertThrows(Failure.class,()->port.dispatch(invocation)).code);
  }
  @Test public void closeAndCancellationWaitForObservedStreamEnd() throws Exception {
    Uri uri=provider.add("blocked.txt","alpha synthetic".getBytes(StandardCharsets.UTF_8));
    provider.entered=new java.util.concurrent.CountDownLatch(1); provider.release=new java.util.concurrent.CountDownLatch(1);
    Task task=new Task(10000); java.util.concurrent.atomic.AtomicReference<String> error=new java.util.concurrent.atomic.AtomicReference<>();
    Thread select=new Thread(()->{try {port.selectSource(uri,task);}catch(Failure f){error.set(f.code);}});
    select.start();assertTrue(provider.entered.await(5,TimeUnit.SECONDS));
    java.util.concurrent.CompletableFuture<Void> closure=port.closeAsync().toCompletableFuture();
    assertFalse(closure.isDone()); task.cancel();provider.release.countDown();select.join(5000);
    assertFalse(select.isAlive());assertEquals("CANCELLED",error.get());closure.get(5,TimeUnit.SECONDS);
    port=new ServicesAndroidToolPort(context,"synthetic-host",grants("documents.read"));
    assertTrue(((List<?>)result(call("documents.search","query","alpha")).value).isEmpty());
  }
  @Test public void corruptJournalRefusesWithoutResetAndReleasesConstructorLease() throws Exception {
    port.closeAsync().toCompletableFuture().get(5,TimeUnit.SECONDS);port=null;
    try(SQLiteDatabase db=context.openOrCreateDatabase("jarvis_serv04a_journal_v1.db",0,null)){db.execSQL("UPDATE schema_meta SET version=99");}
    for(int i=0;i<2;i++)assertEquals("INCOMPATIBLE_MIGRATION",Assert.assertThrows(Failure.class,()->new ServicesAndroidToolPort(context,"synthetic-host",grants("data.read"))).code);
    try(SQLiteDatabase db=context.openOrCreateDatabase("jarvis_serv04a_journal_v1.db",0,null);Cursor c=db.rawQuery("SELECT version FROM schema_meta",null)){assertTrue(c.moveToFirst());assertEquals(99,c.getInt(0));}
  }
  @Test public void refusesExistingUnownedDatabaseWithoutMigratingIt() throws Exception {
    port.closeAsync().toCompletableFuture().get(5,TimeUnit.SECONDS); port=null;
    context.deleteDatabase("jarvis_serv04a_journal_v1.db");
    try(SQLiteDatabase db=context.openOrCreateDatabase("jarvis_serv04a_journal_v1.db",0,null)) { db.execSQL("CREATE TABLE historical(value TEXT)"); db.execSQL("INSERT INTO historical VALUES('synthetic original')"); }
    assertEquals("INCOMPATIBLE_JOURNAL", Assert.assertThrows(Failure.class,()->new ServicesAndroidToolPort(context,"synthetic-host",grants("data.read"))).code);
    try(SQLiteDatabase db=context.openOrCreateDatabase("jarvis_serv04a_journal_v1.db",0,null); Cursor c=db.rawQuery("SELECT value FROM historical",null)) { assertTrue(c.moveToFirst()); assertEquals("synthetic original",c.getString(0)); }
  }
  public static final class SyntheticProvider extends ContentProvider {
    private final Map<String,byte[]> data = new HashMap<>();
    volatile java.util.concurrent.CountDownLatch entered, release;
    Uri add(String name,byte[] bytes) { Uri uri=Uri.parse("content://serv04a.fixture/"+name);data.put(uri.toString(),bytes);return uri; }
    public boolean onCreate(){return true;}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
      if(uri.toString().contains("denied"))throw new SecurityException();
      MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});cursor.addRow(new Object[]{uri.getLastPathSegment()});return cursor;
    }
    public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
      if(entered!=null){entered.countDown();try {if(!release.await(5,TimeUnit.SECONDS))throw new FileNotFoundException();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new FileNotFoundException();}}
      byte[] bytes=data.get(uri.toString());if(bytes==null)throw new FileNotFoundException();
      try {File file=File.createTempFile("serv04a-synthetic-",".bin",getContext().getCacheDir());try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);}
      catch(IOException e){throw new FileNotFoundException();}
    }
    public String getType(Uri uri){return "application/octet-stream";}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
  }
}
