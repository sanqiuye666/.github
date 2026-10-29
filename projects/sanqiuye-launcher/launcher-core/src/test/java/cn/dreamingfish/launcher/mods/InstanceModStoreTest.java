package cn.dreamingfish.launcher.mods;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.instances.*;
import cn.dreamingfish.launcher.storage.*;
import cn.dreamingfish.launcher.vanilla.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class InstanceModStoreTest {
 @TempDir Path root;
 static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"),ZoneOffset.UTC);
 static String sha(byte[] bytes){return HexFormat.of().formatHex(new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest().digest(bytes));}
 static class Context implements TaskContext {
  final CancellationToken token=new CancellationToken();boolean cancel,committed;
  public UUID id(){return UUID.fromString("09de3cda-a495-451a-a1b0-e3f8da4ef8d4");}public CancellationToken token(){return token;}
  public void progress(TaskState state,long b,Long total,long f,Long count)throws IOException{token.check();}
  public void publish(IoAction action)throws IOException{if(cancel)token.cancel();token.check();action.run();committed=true;}
 }
 static String instance(Path root)throws IOException{
  String id=UUID.randomUUID().toString();Path base=root.resolve("instances/"+id);Files.createDirectories(base.resolve(".minecraft"));
  Files.write(base.resolve("instance.json"),LauncherJson.write(Map.of("schemaVersion",1,"id",id,"name","测试实例","gameVersion","1.20.6","loader","VANILLA","createdAt",CLOCK.instant().toString(),"state","PREPARED")));
  String install=UUID.randomUUID().toString();Path directory=Files.createDirectories(base.resolve("installations/"+install+"/versions"));byte[] profile="{}".getBytes();Files.write(directory.resolve("fabric.json"),profile);
  var receipt=new InstallationReceipt(id,install,"1.20.6","1".repeat(40),CLOCK.instant(),Platform.current(),21,List.of(new InstallationReceipt.FileEntry("versions/fabric.json",profile.length,sha(profile))),"0.16.14");
  byte[] bytes=receipt.bytes();Files.write(directory.getParent().resolve("installation.json"),bytes);Files.write(base.resolve("active-installation.json"),LauncherJson.write(Map.of("schemaVersion",1,"instanceId",id,"installationId",install,"versionId","1.20.6","receiptSha256",sha(bytes))));return id;
 }
 static byte[] jar(String id)throws IOException{var bytes=new ByteArrayOutputStream();try(var jar=new JarOutputStream(bytes)){jar.putNextEntry(new JarEntry("fabric.mod.json"));jar.write(LauncherJson.write(Map.of("schemaVersion",1,"id",id,"version","1")));jar.closeEntry();}return bytes.toByteArray();}
 static ModFile file(String project,String version,byte[] bytes){
  var digest=new FileDigest(FileDigest.Algorithm.SHA256,sha(bytes));String name=project+"-"+version+".jar";
  return new ModFile(new ModFileRef(ModSource.MODRINTH,project,version,ModProtocol.fileKey(name,digest,"mod")),project,name,List.of("1.20.6"),List.of("fabric"),"release",CLOCK.instant(),(long)bytes.length,digest,List.of(),true,null,new DownloadSpec(RemoteService.MODRINTH_CDN,URI.create("https://cdn.modrinth.com/data/"+project+"/"+version+".jar"),bytes.length,1024*1024,digest,name));
 }
 ModInstallPlan graph(ModFile... files){return new ModInstallPlan("1.20.6","fabric",List.of(files),List.of());}
 Path mods(String id){return root.resolve("instances/"+id+"/.minecraft/mods");}
 @Test void installsUpdatesAndRemovesOnlyOwnedFilesPreservingNestedManualAndOtherInstance()throws Exception{
  String id=instance(root),other=instance(root);Files.createDirectories(mods(id).resolve("manual/empty"));Files.writeString(mods(id).resolve("manual/notes.txt"),"keep nested");Files.writeString(mods(other).getParent().resolve("options.txt"),"other instance");byte[] bytes=jar("demo"),updated=jar("updated");var one=file("demo","v1",bytes);var two=file("demo","v2",updated);
  var store=new DownloadStore(root,(r,l,t,s)->s.accept(ByteBuffer.wrap(r.uri().getPath().endsWith("v1.jar")?bytes:updated)));
  try(var batch=new DownloadBatch(store,2)){
   var installer=new VanillaInstaller(root,new FileInstanceCatalog(root,CLOCK),store,batch,CLOCK);var target=new InstanceModStore(root,installer,store,batch,CLOCK);
   var plan=target.prepare(id,graph(one),new CancellationToken());var context=new Context();target.install(plan,context);assertTrue(context.committed);assertArrayEquals(bytes,Files.readAllBytes(mods(id).resolve(one.fileName())));assertEquals(1,target.listing(id,new CancellationToken()).entries().size());
   String before=target.verifyForLaunch(installer.listing(id).installations().getFirst(),new CancellationToken());
   target.install(target.prepare(id,graph(two),new CancellationToken()),new Context());assertFalse(Files.exists(mods(id).resolve(one.fileName())));assertArrayEquals(updated,Files.readAllBytes(mods(id).resolve(two.fileName())));assertNotEquals(before,target.verifyForLaunch(installer.listing(id).installations().getFirst(),new CancellationToken()));
   target.remove(target.removal(id,new CancellationToken()),new Context());assertFalse(Files.exists(mods(id).resolve(two.fileName())));assertEquals("keep nested",Files.readString(mods(id).resolve("manual/notes.txt")));assertTrue(Files.isDirectory(mods(id).resolve("manual/empty")));assertEquals("other instance",Files.readString(mods(other).getParent().resolve("options.txt")));assertTrue(target.listing(id,new CancellationToken()).entries().isEmpty());
   try(var backups=Files.walk(root.resolve("instances/"+id+"/mod-transactions"))){assertTrue(backups.anyMatch(p->p.endsWith("previous/"+one.fileName())));}
  }
 }
 @Test void stalePlansCancellationBadBytesAndManualNameCollisionNeverReplaceOriginal()throws Exception{
  String id=instance(root);byte[] bytes=jar("demo");var mod=file("demo","v1",bytes);var store=new DownloadStore(root,(r,l,t,s)->s.accept(ByteBuffer.wrap(bytes)));
  try(var batch=new DownloadBatch(store,1)){
   var target=new InstanceModStore(root,new VanillaInstaller(root,new FileInstanceCatalog(root,CLOCK),store,batch,CLOCK),store,batch,CLOCK);var plan=target.prepare(id,graph(mod),new CancellationToken());Files.createDirectories(mods(id));Files.writeString(mods(id).resolve("added.txt"),"new manual change");assertEquals("PLAN_CHANGED",assertThrows(TaskFailure.class,()->target.install(plan,new Context())).code());
   var current=target.prepare(id,graph(mod),new CancellationToken());var cancelled=new Context();cancelled.cancel=true;assertThrows(InterruptedIOException.class,()->target.install(current,cancelled));assertFalse(Files.exists(mods(id).resolve(mod.fileName())));assertEquals("new manual change",Files.readString(mods(id).resolve("added.txt")));
   Files.writeString(mods(id).resolve(mod.fileName()),"manual same name");assertEquals("MOD_NAME_CONFLICT",assertThrows(TaskFailure.class,()->target.prepare(id,graph(mod),new CancellationToken())).code());assertEquals("manual same name",Files.readString(mods(id).resolve(mod.fileName())));
  }
  var bad=new DownloadStore(root.resolve("bad-cache"),(r,l,t,s)->s.accept(ByteBuffer.wrap("bad".getBytes()))); // 主缓存已有对象不能冒充损坏网络的验收。
  assertThrows(IOException.class,()->bad.fetch(mod.downloadSpec(),new CancellationToken(),n->{}));
 }
 @Test void rejectsTamperingRunningLockAndWrongLoaderBinding()throws Exception{
  String id=instance(root);byte[] bytes=jar("demo");var mod=file("demo","v1",bytes);var store=new DownloadStore(root,(r,l,t,s)->s.accept(ByteBuffer.wrap(bytes)));
  try(var batch=new DownloadBatch(store,1)){
   var installer=new VanillaInstaller(root,new FileInstanceCatalog(root,CLOCK),store,batch,CLOCK);var target=new InstanceModStore(root,installer,store,batch,CLOCK);
   try(var lock=new LauncherStorage(root).lock(Path.of("instances/"+id+"/.running.lock"))){assertThrows(LauncherBusyException.class,()->target.prepare(id,graph(mod),new CancellationToken()));}
   assertThrows(IllegalArgumentException.class,()->target.prepare("../outside",graph(mod),new CancellationToken()));target.install(target.prepare(id,graph(mod),new CancellationToken()),new Context());
   var core=installer.listing(id).installations().getFirst();var vanilla=new InstallationReceipt(id,core.installationId(),core.versionId(),core.metadataSha1(),core.installedAt(),core.platform(),core.requiredJavaMajor(),List.of(new InstallationReceipt.FileEntry("versions/client.jar",1,"a".repeat(64))));assertEquals("MOD_LOADER_CHANGED",assertThrows(TaskFailure.class,()->target.verifyForLaunch(vanilla,new CancellationToken())).code());
   Files.writeString(mods(id).resolve(mod.fileName()),"tampered");assertEquals("MOD_FILES_CHANGED",assertThrows(TaskFailure.class,()->target.removal(id,new CancellationToken())).code());assertEquals("tampered",Files.readString(mods(id).resolve(mod.fileName())));
  }
 }
 @Test void recoversInterruptedDirectorySwapWithoutDiscardingOriginal()throws Exception{
  String id=instance(root);Files.createDirectories(mods(id));Files.writeString(mods(id).resolve("manual.txt"),"original");var storage=new LauncherStorage(root);String before=ModDirectorySnapshot.capture(storage,Path.of("instances/"+id+"/.minecraft/mods"),new CancellationToken()).fingerprint();String operation=UUID.randomUUID().toString();Path transaction=root.resolve("instances/"+id+"/mod-transactions/"+operation);Files.createDirectories(transaction);Files.move(mods(id),transaction.resolve("previous"));Files.createDirectories(mods(id));Files.writeString(mods(id).resolve("new.txt"),"staged");String after=ModDirectorySnapshot.capture(storage,Path.of("instances/"+id+"/.minecraft/mods"),new CancellationToken()).fingerprint();
  Files.write(root.resolve("instances/"+id+"/mods-transaction.json"),LauncherJson.write(Map.of("schemaVersion",1,"instanceId",id,"operationId",operation,"phase","PREPARED","hadOriginal",true,"beforeSha256",before,"afterSha256",after)));
  var store=new DownloadStore(root,(r,l,t,s)->{throw new AssertionError();});try(var batch=new DownloadBatch(store,1)){
   var target=new InstanceModStore(root,new VanillaInstaller(root,new FileInstanceCatalog(root,CLOCK),store,batch,CLOCK),store,batch,CLOCK);target.recoverPending(id,new CancellationToken());assertEquals("original",Files.readString(mods(id).resolve("manual.txt")));assertFalse(Files.exists(mods(id).resolve("new.txt")));assertEquals("staged",Files.readString(transaction.resolve("discarded/new.txt")));target.recoverPending(id,new CancellationToken());assertEquals("original",Files.readString(mods(id).resolve("manual.txt")));
  }
 }
 @Test void dependencyUpdatesCheckTheFinalUnionAndRejectPinnedConflicts()throws Exception{
  String id=instance(root);byte[] bytes=jar("dependency");var dependency=file("dep","v1",bytes);var update=file("dep","v2",bytes);var plain=file("host","v1",bytes);
  var host=new ModFile(plain.ref(),plain.displayName(),plain.fileName(),plain.gameVersions(),plain.loaders(),plain.releaseChannel(),plain.publishedAt(),plain.size(),plain.sourceDigest(),List.of(new ModDependency(ModDependency.Kind.REQUIRED,"dep",null,null)),true,null,plain.downloadSpec());
  var store=new DownloadStore(root,(r,l,t,s)->s.accept(ByteBuffer.wrap(bytes)));try(var batch=new DownloadBatch(store,1)){
   var target=new InstanceModStore(root,new VanillaInstaller(root,new FileInstanceCatalog(root,CLOCK),store,batch,CLOCK),store,batch,CLOCK);
   target.install(target.prepare(id,graph(host,dependency),new CancellationToken()),new Context());
   target.install(target.prepare(id,graph(update),new CancellationToken()),new Context());assertEquals(Set.of("host","dep"),target.listing(id,new CancellationToken()).entries().stream().map(ManagedModReceipt.Entry::projectId).collect(java.util.stream.Collectors.toSet()));
   var pinned=new ModFile(plain.ref(),plain.displayName(),plain.fileName(),plain.gameVersions(),plain.loaders(),plain.releaseChannel(),plain.publishedAt(),plain.size(),plain.sourceDigest(),List.of(new ModDependency(ModDependency.Kind.REQUIRED,"dep","v2",null)),true,null,plain.downloadSpec());
   target.install(target.prepare(id,graph(pinned,update),new CancellationToken()),new Context());
   assertEquals("DEPENDENCY_CONFLICT",assertThrows(TaskFailure.class,()->target.prepare(id,graph(dependency),new CancellationToken())).code());assertTrue(Files.exists(mods(id).resolve(update.fileName())));
  }
 }
 @Test void invalidJarAndCorruptJournalNeverReplaceOrEnableLaunch()throws Exception{
  String id=instance(root);Files.createDirectories(mods(id));Files.writeString(mods(id).resolve("manual.txt"),"preserve");byte[] bytes="not a jar".getBytes();var mod=file("bad","v1",bytes);var store=new DownloadStore(root,(r,l,t,s)->s.accept(ByteBuffer.wrap(bytes)));
  try(var batch=new DownloadBatch(store,1)){
   var installer=new VanillaInstaller(root,new FileInstanceCatalog(root,CLOCK),store,batch,CLOCK);var target=new InstanceModStore(root,installer,store,batch,CLOCK);
   assertEquals("MOD_JAR_INVALID",assertThrows(TaskFailure.class,()->target.install(target.prepare(id,graph(mod),new CancellationToken()),new Context())).code());assertEquals("preserve",Files.readString(mods(id).resolve("manual.txt")));assertFalse(Files.exists(mods(id).resolve(mod.fileName())));
   Files.writeString(root.resolve("instances/"+id+"/mods-transaction.json"),"{\"phase\":\"unknown\"}");
   assertThrows(IOException.class,()->target.verifyForLaunch(installer.listing(id).installations().getFirst(),new CancellationToken()));assertThrows(IOException.class,()->target.listing(id,new CancellationToken()));
  }
 }

 @Test void corruptBackupOrUnexpectedCurrentContentNeverGetsMovedDuringRecovery()throws Exception{
  String id=instance(root);Files.createDirectories(mods(id));Files.writeString(mods(id).resolve("original.txt"),"original");var storage=new LauncherStorage(root);Path relative=Path.of("instances/"+id+"/.minecraft/mods");String before=ModDirectorySnapshot.capture(storage,relative,new CancellationToken()).fingerprint();
  String op=UUID.randomUUID().toString();Path transaction=root.resolve("instances/"+id+"/mod-transactions/"+op);Files.createDirectories(transaction);Files.move(mods(id),transaction.resolve("previous"));Files.createDirectories(mods(id));Files.writeString(mods(id).resolve("new.txt"),"new");String after=ModDirectorySnapshot.capture(storage,relative,new CancellationToken()).fingerprint();
  Path journal=root.resolve("instances/"+id+"/mods-transaction.json");Files.write(journal,LauncherJson.write(Map.of("schemaVersion",1,"instanceId",id,"operationId",op,"phase","PREPARED","hadOriginal",true,"beforeSha256",before,"afterSha256",after)));
  var store=new DownloadStore(root,(r,l,t,sink)->{throw new AssertionError();});try(var batch=new DownloadBatch(store,1)){
   var target=new InstanceModStore(root,new VanillaInstaller(root,new FileInstanceCatalog(root,CLOCK),store,batch,CLOCK),store,batch,CLOCK);
   Files.writeString(mods(id).resolve("new.txt"),"user change");assertEquals("MOD_RECOVERY_REQUIRED",assertThrows(TaskFailure.class,()->target.recoverPending(id,new CancellationToken())).code());assertEquals("user change",Files.readString(mods(id).resolve("new.txt")));assertEquals("original",Files.readString(transaction.resolve("previous/original.txt")));assertFalse(Files.exists(transaction.resolve("discarded")));
   Files.writeString(mods(id).resolve("new.txt"),"new");Files.writeString(transaction.resolve("previous/original.txt"),"backup changed");assertEquals("MOD_RECOVERY_REQUIRED",assertThrows(TaskFailure.class,()->target.recoverPending(id,new CancellationToken())).code());assertEquals("new",Files.readString(mods(id).resolve("new.txt")));assertEquals("backup changed",Files.readString(transaction.resolve("previous/original.txt")));assertFalse(Files.exists(transaction.resolve("discarded")));
  }
 }
 @Test void recoveryWithNoOriginalRestoresAbsenceAndCommittedJournalIsStable()throws Exception{
  String id=instance(root);var storage=new LauncherStorage(root);Path relative=Path.of("instances/"+id+"/.minecraft/mods");String before=ModDirectorySnapshot.capture(storage,relative,new CancellationToken()).fingerprint();Files.createDirectories(mods(id));Files.writeString(mods(id).resolve("new.txt"),"new");String after=ModDirectorySnapshot.capture(storage,relative,new CancellationToken()).fingerprint();String op=UUID.randomUUID().toString();Files.createDirectories(root.resolve("instances/"+id+"/mod-transactions/"+op));
  Path journal=root.resolve("instances/"+id+"/mods-transaction.json");var fields=new HashMap<String,Object>(Map.of("schemaVersion",1,"instanceId",id,"operationId",op,"phase","PREPARED","hadOriginal",false,"beforeSha256",before,"afterSha256",after));Files.write(journal,LauncherJson.write(fields));var store=new DownloadStore(root,(r,l,t,sink)->{throw new AssertionError();});try(var batch=new DownloadBatch(store,1)){
   var target=new InstanceModStore(root,new VanillaInstaller(root,new FileInstanceCatalog(root,CLOCK),store,batch,CLOCK),store,batch,CLOCK);target.recoverPending(id,new CancellationToken());assertFalse(Files.exists(mods(id)));target.recoverPending(id,new CancellationToken());assertFalse(Files.exists(mods(id)));
   Files.createDirectories(mods(id));Files.writeString(mods(id).resolve("committed.txt"),"keep");fields.put("phase","COMMITTED");Files.write(journal,LauncherJson.write(fields));target.recoverPending(id,new CancellationToken());assertEquals("keep",Files.readString(mods(id).resolve("committed.txt")));
  }
 }

}
