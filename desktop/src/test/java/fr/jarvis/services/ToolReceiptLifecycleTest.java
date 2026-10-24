package fr.jarvis.services;

import fr.jarvis.services.platform.JdbcDatabase;
import fr.jarvis.services.modules.guard.Guard;
import fr.jarvis.services.shared.api.Contracts.Failure;
import fr.jarvis.services.shared.storage.Store;
import fr.jarvis.services.tools.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ToolReceiptLifecycleTest {
  @TempDir Path root;
  private static ToolInvocation request() {
    return new ToolInvocation(UUID.randomUUID().toString(),"data.receipt.fixture","1.0.0",Collections.singletonMap("csv","synthetic"),10000,null);
  }
  @Test void uncertainReceiptNeverDeliversSuccessOrValue() throws Exception {
    try(JdbcDatabase db=new JdbcDatabase(root.resolve("receipt.sqlite"))) {
      Store store=new Store(db);Guard guard=new Guard(root,store);ToolRegistry registry=new ToolRegistry(store);
      registry.register(new ToolDescriptor("data.receipt.fixture","1.0.0","data","data.read",Collections.singleton("csv"),"fixture",false,1024),(host,args,task)->"synthetic result");
      try(ToolDispatcher dispatcher=new ToolDispatcher(registry,guard,new ApprovalGate(store,guard),result->{throw new Failure("STORAGE_ERROR");})) {
        ToolDispatcher.Result result=dispatcher.dispatch(new Guard.Context("fixture",Collections.singleton("data.read")),request()).result.get(5,TimeUnit.SECONDS);
        assertEquals(ToolDispatcher.Status.UNKNOWN,result.status);assertEquals("RECEIPT_UNCONFIRMED",result.error);assertNull(result.value);
      }
    }
  }
  @Test void drainCallbackWaitsForActuallyReturnedCancelledOperation() throws Exception {
    CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),drained=new CountDownLatch(1);
    try(JdbcDatabase db=new JdbcDatabase(root.resolve("drain.sqlite"))) {
      Store store=new Store(db);Guard guard=new Guard(root,store);ToolRegistry registry=new ToolRegistry(store);
      registry.register(new ToolDescriptor("data.receipt.fixture","1.0.0","data","data.read",Collections.singleton("csv"),"fixture",false,1024),(host,args,task)->{
        entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new Failure("EXPIRED");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new Failure("CANCELLED");}task.check();return "synthetic result";
      });
      try(ToolDispatcher dispatcher=new ToolDispatcher(registry,guard,new ApprovalGate(store,guard))) {
        ToolDispatcher.Handle handle=dispatcher.dispatch(new Guard.Context("fixture",Collections.singleton("data.read")),request());
        assertTrue(entered.await(5,TimeUnit.SECONDS));handle.cancel();dispatcher.closeWhenDrained(drained::countDown);
        assertEquals(1,drained.getCount());release.countDown();
        assertEquals("CANCELLED",handle.result.get(5,TimeUnit.SECONDS).error);assertTrue(drained.await(5,TimeUnit.SECONDS));
      } finally {release.countDown();}
    }
  }
}
