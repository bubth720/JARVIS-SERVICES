package fr.jarvis.services.android.adapter;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import fr.jarvis.services.tools.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Compile-only until an explicitly authorized isolated Android lab is available. */
@RunWith(AndroidJUnit4.class)
public class AndroidToolPortInstrumentedTest {
  @Test public void csvAndPermissionsOnPlatformSQLite() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    ServicesAndroidToolPort port = new ServicesAndroidToolPort(context,"instrumented-synthetic",Collections.singleton("data.read"));
    try {
      ToolInvocation request = new ToolInvocation(UUID.randomUUID().toString(),"data.csv.normalize","1.0.0",Collections.singletonMap("csv","a\n1\n1\n"),10000,null);
      ToolDispatcher.Result result = port.dispatch(request).result.get(10,TimeUnit.SECONDS);
      assertEquals(ToolDispatcher.Status.SUCCESS,result.status);
      assertEquals("\"a\"\r\n\"1\"\r\n",result.value);
      assertEquals("SUCCESS",port.receipts().get(port.receipts().size()-1).get("state"));
      assertEquals(Boolean.FALSE,port.capabilities().get("network"));
    } finally { port.closeAsync().toCompletableFuture().get(10,TimeUnit.SECONDS); }
  }
}
