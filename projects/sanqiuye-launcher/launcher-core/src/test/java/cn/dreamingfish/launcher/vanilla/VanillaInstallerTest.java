package cn.dreamingfish.launcher.vanilla;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.instances.*;
import cn.dreamingfish.launcher.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VanillaInstallerTest {
 @TempDir Path root;
 static List<InstallationReceipt.FileEntry> oversizedEntries(){
  String prefix="resources/"+("a".repeat(200)+"/").repeat(4)+"b".repeat(140);
  var files=new ArrayList<InstallationReceipt.FileEntry>();for(int i=0;i<33500;i++)files.add(new InstallationReceipt.FileEntry(prefix+i,1,"0".repeat(64)));return files;
 }
 @Test void receiptWriterRefusesARecordItsReaderCannotReopen()throws Exception{
  var receipt=new InstallationReceipt(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"fixture","a".repeat(40),java.time.Instant.now(),VanillaFixtures.WINDOWS,21,oversizedEntries());
  assertThrows(IOException.class,receipt::bytes);
 }
 @Test void oversizedReceiptFailsBeforeDownloadsAndPreservesTheActiveReference()throws Exception{
  var f=VanillaFixtures.setup(root,false,false,false);var instances=new FileInstanceCatalog(root,VanillaFixtures.CLOCK);String id=instances.create("keep","26.3",f.catalog().load(false).manifest()).instance().id();
  try(var batch=new DownloadBatch(f.store(),4)){
   var installer=new VanillaInstaller(root,instances,f.store(),batch,VanillaFixtures.CLOCK);var plan=new VanillaPlanner(f.catalog(),f.store(),VanillaFixtures.WINDOWS).plan("26.3",new CancellationToken());installer.install(id,plan,new Context());Path active=root.resolve("instances/"+id+"/active-installation.json");byte[] prior=Files.readAllBytes(active);
   var oversized=new VanillaPlan(plan.versionId(),plan.metadataSha1(),plan.platform(),plan.requiredJavaMajor(),oversizedEntries().stream().map(e->new InstallFile(Path.of(e.path()),new DownloadSpec(RemoteService.MOJANG_FILES,java.net.URI.create("https://piston-data.mojang.com/one"),1,1024,new FileDigest(FileDigest.Algorithm.SHA1,"a".repeat(40)),"one"))).toList(),0);
   var context=new Context(){@Override public void progress(TaskState s,long b,Long t,long f,Long n){throw new AssertionError("Oversized record must fail before downloads");}};
   assertEquals("RECORD_TOO_LARGE",assertThrows(TaskFailure.class,()->installer.install(id,oversized,context)).code());assertArrayEquals(prior,Files.readAllBytes(active));assertFalse(context.committed);
  }
 }
 static class Context implements TaskContext {
  final CancellationToken token=new CancellationToken();final UUID id=UUID.randomUUID();boolean committed;boolean cancel;
  public UUID id(){return id;}public CancellationToken token(){return token;}public void progress(TaskState s,long b,Long t,long f,Long c)throws IOException{token.check();}
  public void publish(IoAction action)throws IOException{if(cancel)token.cancel();token.check();action.run();committed=true;}
 }
 @Test void isolatedInstallReopenReinstallAndCorruptionDoNotModifyOriginals()throws Exception {
  var f=VanillaFixtures.setup(root,false,true,true);var instances=new FileInstanceCatalog(root,VanillaFixtures.CLOCK);var instance=instances.create("中文世界","26.3",f.catalog().load(false).manifest());String id=instance.instance().id();Path metadata=root.resolve("instances/"+id+"/instance.json");byte[] before=Files.readAllBytes(metadata);Path sentinel=instance.instance().directory().resolve("options.txt");Files.writeString(sentinel,"keep my settings");
  try(var batch=new DownloadBatch(f.store(),4)){
   var installer=new VanillaInstaller(root,instances,f.store(),batch,VanillaFixtures.CLOCK);var plan=new VanillaPlanner(f.catalog(),f.store(),VanillaFixtures.WINDOWS).plan("26.3",new CancellationToken());var ctx=new Context();var receipt=installer.install(id,plan,ctx);assertTrue(ctx.committed);assertEquals(plan.files().size(),receipt.files().size());assertArrayEquals(before,Files.readAllBytes(metadata));assertEquals("keep my settings",Files.readString(sentinel));
   var reopened=new VanillaInstaller(root,new FileInstanceCatalog(root,VanillaFixtures.CLOCK),f.store(),batch,VanillaFixtures.CLOCK);assertEquals(receipt,reopened.verifyActive(id,new CancellationToken()));
   Path active=root.resolve("instances/"+id+"/active-installation.json");byte[] prior=Files.readAllBytes(active);var cancelled=new Context();cancelled.cancel=true;assertThrows(InterruptedIOException.class,()->installer.install(id,plan,cancelled));assertArrayEquals(prior,Files.readAllBytes(active));assertFalse(cancelled.committed);
   var again=installer.install(id,plan,new Context());assertNotEquals(receipt.installationId(),again.installationId());assertTrue(Files.isDirectory(root.resolve("instances/"+id+"/installations/"+receipt.installationId())));
   Path file=root.resolve("instances/"+id+"/installations/"+again.installationId()+"/versions/client.jar");byte[] changed=Files.readAllBytes(file);changed[0]^=1;Files.write(file,changed);assertThrows(IOException.class,()->installer.verifyActive(id,new CancellationToken()));assertArrayEquals(changed,Files.readAllBytes(file));
   Path bad=Files.createDirectories(root.resolve("instances/"+id+"/installations/"+UUID.randomUUID()));Files.writeString(bad.resolve("installation.json"),"broken");assertEquals(1,installer.listing(id).installations().size());assertEquals(1,installer.listing(id).problems().size());
   try(var lock=new LauncherStorage(root).lock(Path.of("instances/"+id+"/installations/.install.lock"))){assertThrows(LauncherBusyException.class,()->installer.install(id,plan,new Context()));}
   assertThrows(IllegalArgumentException.class,()->installer.install("../outside",plan,new Context()));assertArrayEquals(before,Files.readAllBytes(metadata));
  }
 }
 @Test void failedActivePointerLeavesNoInstalledClaimAndUnreferencedDataIsNotActive()throws Exception{
  var f=VanillaFixtures.setup(root,true,false,false);var instances=new FileInstanceCatalog(root,VanillaFixtures.CLOCK);String id=instances.create("legacy","1.5.2",f.catalog().load(false).manifest()).instance().id();var plan=new VanillaPlanner(f.catalog(),f.store(),VanillaFixtures.WINDOWS).plan("1.5.2",new CancellationToken());Files.createDirectory(root.resolve("instances/"+id+"/active-installation.json"));
  try(var batch=new DownloadBatch(f.store(),4)){var installer=new VanillaInstaller(root,instances,f.store(),batch,VanillaFixtures.CLOCK);var context=new Context();assertThrows(IOException.class,()->installer.install(id,plan,context));assertFalse(context.committed);assertTrue(installer.listing(id).installations().isEmpty());assertFalse(installer.listing(id).problems().isEmpty());assertThrows(IOException.class,()->installer.verifyActive(id,new CancellationToken()));}
 }
 @Test void concurrentProgressCannotRegressTheTaskCounter()throws Exception{
  var f=VanillaFixtures.setup(root,false,false,false);var instances=new FileInstanceCatalog(root,VanillaFixtures.CLOCK);String id=instances.create("parallel","26.3",f.catalog().load(false).manifest()).instance().id();var original=new VanillaPlanner(f.catalog(),f.store(),VanillaFixtures.WINDOWS).plan("26.3",new CancellationToken());var files=new ArrayList<>(original.files());
  for(int i=1;i<=2;i++){byte[] bytes={(byte)i};var uri=java.net.URI.create("https://piston-data.mojang.com/extra"+i+".jar");f.responses().put(uri.toString(),bytes);files.add(new InstallFile(Path.of("versions/extra"+i+".jar"),new DownloadSpec(RemoteService.MOJANG_FILES,uri,1,1024,new FileDigest(FileDigest.Algorithm.SHA1,VanillaFixtures.sha1(bytes)),"extra")));}
  var plan=new VanillaPlan(original.versionId(),original.metadataSha1(),original.platform(),original.requiredJavaMajor(),files,0);
  var context=new Context(){final java.util.concurrent.atomic.AtomicBoolean delay=new java.util.concurrent.atomic.AtomicBoolean();long last;
   @Override public void progress(TaskState s,long bytes,Long total,long completed,Long count)throws IOException{
    if(s==TaskState.DOWNLOADING&&bytes>0&&delay.compareAndSet(false,true))try{Thread.sleep(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}
    synchronized(this){if(bytes<last)throw new IOException("progress regressed");last=bytes;}super.progress(s,bytes,total,completed,count);
   }
  };
  try(var batch=new DownloadBatch(f.store(),4)){new VanillaInstaller(root,instances,f.store(),batch,VanillaFixtures.CLOCK).install(id,plan,context);assertTrue(context.committed);}
 }
}
