package cn.dreamingfish.launcher.downloads;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class DownloadBatchTest {
 @TempDir Path root;
 @Test void directModDownloadAndVanillaBatchShareFourFileSlots()throws Exception{
  var started=new CountDownLatch(4);var extra=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
  var store=new DownloadStore(root,(r,l,t,s)->{int count=calls.incrementAndGet();if(count>4)extra.countDown();started.countDown();try{while(!release.await(10,TimeUnit.MILLISECONDS))t.check();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.InterruptedIOException();}s.accept(ByteBuffer.wrap(new byte[]{(byte)Integer.parseInt(r.uri().getPath().substring(1))}));});
  var specs=new ArrayList<DownloadSpec>();for(int i=0;i<5;i++){var p=DownloadStoreTest.spec(new byte[]{(byte)i});specs.add(new DownloadSpec(p.service(),java.net.URI.create("https://piston-data.mojang.com/"+i),p.expectedSize(),p.maxBytes(),p.digest(),"fixture"));}
  try(var batch=new DownloadBatch(store,4);var callers=Executors.newFixedThreadPool(2)){
   var vanilla=callers.submit(()->batch.fetch(specs.subList(0,4),new CancellationToken(),n->{}));assertTrue(started.await(2,TimeUnit.SECONDS));var mod=callers.submit(()->store.fetch(specs.get(4),new CancellationToken(),n->{}));
   try{assertFalse(extra.await(200,TimeUnit.MILLISECONDS),"fifth transfer bypassed shared slots");}finally{release.countDown();}assertEquals(4,vanilla.get().size());assertEquals(specs.get(4).digest(),mod.get().sourceDigest());
  }
 }
 @Test void repeatedFailedBatchesReleaseCancelledQueuedSlots()throws Exception {
  var gate=new AtomicReference<CountDownLatch>();
  RemoteClient remote=(r,l,t,s)->{gate.get().countDown();if(r.uri().getPath().equals("/0")){try{gate.get().await();}catch(InterruptedException e){Thread.currentThread().interrupt();}throw new java.io.IOException("controlled first failure");}while(!t.isCancelled())java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);Thread.interrupted();java.util.concurrent.locks.LockSupport.parkNanos(200_000_000);t.check();};
  var specs=new ArrayList<DownloadSpec>();for(int i=0;i<8;i++){var p=DownloadStoreTest.spec(new byte[]{(byte)i});specs.add(new DownloadSpec(p.service(),java.net.URI.create("https://piston-data.mojang.com/"+i),p.expectedSize(),p.maxBytes(),p.digest(),"fixture"));}
  try(var batch=new DownloadBatch(new DownloadStore(root,remote),4);var caller=Executors.newSingleThreadExecutor()){
   for(int i=0;i<5;i++){gate.set(new CountDownLatch(4));var f=caller.submit(()->{assertThrows(java.io.IOException.class,()->batch.fetch(specs,new CancellationToken(),n->{}));});try{f.get(3,TimeUnit.SECONDS);}finally{if(!f.isDone())f.cancel(true);}Thread.sleep(250);}
  }
 }
 @Test void boundedSharedSlotsPreserveOrderAndCancelFailure()throws Exception {
  var active=new AtomicInteger();var peak=new AtomicInteger();var began=new CountDownLatch(4);var gate=new CountDownLatch(1);
  var specs=new ArrayList<DownloadSpec>();for(int i=0;i<20;i++)specs.add(DownloadStoreTest.spec(new byte[]{(byte)i}));
  RemoteClient remote=(r,l,t,s)->{int count=active.incrementAndGet();peak.accumulateAndGet(count,Math::max);began.countDown();try{while(!gate.await(10,TimeUnit.MILLISECONDS))t.check();s.accept(ByteBuffer.wrap(new byte[]{(byte)Integer.parseInt(r.uri().getPath().substring(1))}));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.InterruptedIOException();}finally{active.decrementAndGet();}};
  for(int i=0;i<specs.size();i++){var p=specs.get(i);specs.set(i,new DownloadSpec(p.service(),java.net.URI.create("https://piston-data.mojang.com/"+i),p.expectedSize(),p.maxBytes(),p.digest(),"fixture"));}
  try(var batch=new DownloadBatch(new DownloadStore(root,remote),4);var worker=Executors.newSingleThreadExecutor()){
   var future=worker.submit(()->batch.fetch(specs,new CancellationToken(),n->{}));assertTrue(began.await(2,TimeUnit.SECONDS));assertEquals(4,peak.get());gate.countDown();var result=future.get();assertEquals(20,result.size());for(int i=0;i<20;i++)assertEquals(specs.get(i).digest(),result.get(i).sourceDigest());assertTrue(peak.get()<=4);
  }
  var fail=new java.util.concurrent.atomic.AtomicBoolean(true);
  try(var batch=new DownloadBatch(new DownloadStore(root.resolve("fail"),(r,l,t,s)->{if(fail.get())throw new java.io.IOException("controlled");s.accept(ByteBuffer.wrap(new byte[]{(byte)Integer.parseInt(r.uri().getPath().substring(1))}));}),4);var worker=Executors.newSingleThreadExecutor()){
   assertThrows(java.io.IOException.class,()->batch.fetch(specs,new CancellationToken(),n->{}));fail.set(false);
   assertEquals(20,worker.submit(()->batch.fetch(specs,new CancellationToken(),n->{})).get(3,TimeUnit.SECONDS).size());
  }
 }
}
