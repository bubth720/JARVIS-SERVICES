package fr.jarvis.services.android;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import fr.jarvis.services.shared.storage.Store;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Must run on emulator/device; compilation alone is not runtime validation. */
@RunWith(AndroidJUnit4.class)
public final class OfflineSmokeTest {
  private Context context() {
    return InstrumentationRegistry.getInstrumentation().getTargetContext();
  }

  @Test
  public void bundledFts5AndMigration() {
    File f = new File(context().getCacheDir(), "smoke-" + System.nanoTime() + ".sqlite");
    AndroidDatabase db = new AndroidDatabase(f.getPath());
    try {
      Store store = new Store(db);
      store.index("source", "offline android");
      assertEquals("source", store.search("offline", 1).get(0).get("id"));
      assertEquals("ok", store.integrity());
      store.migrate(0);
      store.migrate(1);
    } finally {
      db.close();
      f.delete();
    }
  }

  @Test
  public void noInternetPermission() throws Exception {
    String[] permissions =
        context()
            .getPackageManager()
            .getPackageInfo(
                context().getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions;
    if (permissions != null)
      for (String p : permissions) assertFalse("android.permission.INTERNET".equals(p));
  }
  private boolean contains(android.view.View view, String text) {
    if (view instanceof android.widget.TextView && ((android.widget.TextView)view).getText().toString().contains(text)) return true;
    if (view instanceof android.view.ViewGroup) { android.view.ViewGroup group=(android.view.ViewGroup)view; for(int i=0;i<group.getChildCount();i++) if(contains(group.getChildAt(i),text))return true; }
    return false;
  }
  @Test public void diagnosticActivityInitializesAllModules() throws Exception {
    android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    android.content.Intent intent=new android.content.Intent(context(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
    android.app.Activity activity=instrumentation.startActivitySync(intent);
    try {
      java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
      long deadline=android.os.SystemClock.uptimeMillis()+10000;
      while(!ready.get() && android.os.SystemClock.uptimeMillis()<deadline){
        instrumentation.runOnMainSync(()->ready.set(contains(activity.getWindow().getDecorView(),"available_in_process")&&contains(activity.getWindow().getDecorView(),"bridge")));
        if(!ready.get())Thread.sleep(50);
      }
      assertTrue("Diagnostic did not initialize modules",ready.get());
    } finally { instrumentation.runOnMainSync(activity::finish); }
  }
}
