package cn.dreamingfish.launcher.downloads;
import cn.dreamingfish.launcher.storage.LauncherStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class DownloadStoreTest {
 @TempDir Path root;
 @Test void untrustedBinarySourcesAreRejectedBeforeAJobExists()throws Exception {
  var valid=spec(data);
  for(String url:List.of("http://piston-data.mojang.com/a","https://attacker.invalid/a","https://user@piston-data.mojang.com/a"))assertThrows(IllegalArgumentException.class,()->new DownloadSpec(valid.service(),URI.create(url),valid.expectedSize(),valid.maxBytes(),valid.digest(),"bad"));
 }
 static byte[] data="verified-content".getBytes();
 static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 static DownloadSpec spec(byte[] bytes)throws Exception{return new DownloadSpec(RemoteService.MOJANG_FILES,URI.create("https://piston-data.mojang.com/file"),bytes.length,1024,new FileDigest(FileDigest.Algorithm.SHA256,hash(bytes)),"fixture");}
 static RemoteClient remote(byte[] bytes,AtomicInteger calls){return (r,l,t,s)->{calls.incrementAndGet();t.check();s.accept(ByteBuffer.wrap(bytes));};}
 @Test void verifiesStreamCacheAndSameLengthCorruption()throws Exception {
  var calls=new AtomicInteger();var store=new DownloadStore(root,remote(data,calls));var progress=new AtomicLong();var spec=spec(data);
  var got=store.fetch(spec,new CancellationToken(),progress::addAndGet);assertEquals(data.length,progress.get());assertArrayEquals(data,Files.readAllBytes(root.resolve(got.relativeObject())));
  store.fetch(spec,new CancellationToken(),progress::addAndGet);assertEquals(1,calls.get());assertEquals(data.length,progress.get());
  byte[] tampered=data.clone();tampered[0]^=1;Files.write(root.resolve(got.relativeObject()),tampered);
  assertThrows(IOException.class,()->store.fetch(spec,new CancellationToken(),n->{}));assertArrayEquals(tampered,Files.readAllBytes(root.resolve(got.relativeObject())));
 }
 @Test void lengthHashFailureCancellationAndConcurrentPublication()throws Exception {
  for(byte[] bad:List.of(new byte[1],new byte[40],"corrupt-content!".getBytes()))assertThrows(IOException.class,()->new DownloadStore(root.resolve(UUID.randomUUID().toString()),remote(bad,new AtomicInteger())).fetch(spec(data),new CancellationToken(),n->{}));
  var token=new CancellationToken();var cancelling=new DownloadStore(root.resolve("cancel"),(r,l,t,s)->{s.accept(ByteBuffer.wrap(data));t.cancel();});assertThrows(InterruptedIOException.class,()->cancelling.fetch(spec(data),token,n->{}));
  var store=new DownloadStore(root.resolve("shared"),remote(data,new AtomicInteger()));
  try(var workers=Executors.newFixedThreadPool(2)){var a=workers.submit(()->store.fetch(spec(data),new CancellationToken(),n->{}));var b=workers.submit(()->store.fetch(spec(data),new CancellationToken(),n->{}));assertEquals(a.get().sha256(),b.get().sha256());}
 }
 @Test void stagedFilesNeverReplaceAndCopiesAreIndependent()throws Exception {
  var storage=new LauncherStorage(root);storage.writeAtomically(Path.of("source"),data);storage.copyNew(Path.of("source"),Path.of("copy"));Files.write(root.resolve("copy"),new byte[]{1});assertArrayEquals(data,Files.readAllBytes(root.resolve("source")));
  try(var stage=storage.beginStagedWrite(Path.of("staging"))){stage.write(ByteBuffer.wrap(data));assertThrows(FileAlreadyExistsException.class,()->stage.publishNew(Path.of("source")));}assertArrayEquals(data,Files.readAllBytes(root.resolve("source")));
  try(var stage=storage.beginStagedWrite(Path.of("staging"))){assertThrows(IllegalArgumentException.class,()->stage.publishNew(Path.of("../escape")));}
 }
 @Test void junctionIsRejectedWithoutTouchingExternalSentinel()throws Exception {
  Path outside=Files.createDirectory(root.resolve("outside")),managed=Files.createDirectory(root.resolve("managed"));Files.write(outside.resolve("sentinel"),data);
  var process=new ProcessBuilder("cmd.exe","/c","mklink","/J",managed.resolve("downloads").toString(),outside.toString()).redirectErrorStream(true).start();assertEquals(0,process.waitFor(),new String(process.getInputStream().readAllBytes()));
  assertThrows(IOException.class,()->new DownloadStore(managed,remote(data,new AtomicInteger())).fetch(spec(data),new CancellationToken(),n->{}));assertArrayEquals(data,Files.readAllBytes(outside.resolve("sentinel")));
 }
 @Test void concurrentIndexPublicationDoesNotReplaceAnAlreadyPublishedIndex()throws Exception {
  var began=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
  var store=new DownloadStore(root,(r,l,t,s)->{if(calls.incrementAndGet()==1){began.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}}s.accept(ByteBuffer.wrap(data));});
  try(var workers=Executors.newFixedThreadPool(2)){
   var delayed=workers.submit(()->store.fetch(spec(data),new CancellationToken(),n->{}));assertTrue(began.await(2,TimeUnit.SECONDS));store.fetch(spec(data),new CancellationToken(),n->{});
   Path index=root.resolve("downloads/index/SHA256-"+hash(data)+".json");var time=java.nio.file.attribute.FileTime.fromMillis(1000);Files.setLastModifiedTime(index,time);
   var options=new HashSet<OpenOption>();options.add(StandardOpenOption.READ);if(System.getProperty("os.name").startsWith("Windows"))options.add(com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE);
   try(var held=java.nio.channels.FileChannel.open(index,options)){release.countDown();assertEquals(hash(data),delayed.get(3,TimeUnit.SECONDS).sha256());assertEquals(time,Files.getLastModifiedTime(index));}
   finally{release.countDown();if(!delayed.isDone())delayed.cancel(true);}
  }
 }
}
