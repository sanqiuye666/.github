package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.mods.*;
import cn.dreamingfish.launcher.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LauncherIntegrityTasksTest {
 @TempDir Path root;
 static class Context implements TaskContext{final CancellationToken token=new CancellationToken();public UUID id(){return UUID.randomUUID();}public CancellationToken token(){return token;}public void progress(TaskState s,long b,Long t,long f,Long n){}public void publish(IoAction action)throws IOException{action.run();}}
 static TaskSnapshot terminal(TaskManager manager,UUID id)throws Exception{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);while(System.nanoTime()<end){var task=manager.get(id);if(task.state().terminal())return task;Thread.sleep(5);}throw new AssertionError("Task did not finish");}
 static void dto(TaskManager tasks,TaskSnapshot task,String code,String text)throws Exception{assertEquals(TaskState.FAILED,task.state());var value=LauncherRouteFixtures.body(new TaskRoutes(tasks).handle(LauncherRouteFixtures.get("/tasks/"+task.id(),Map.of())).orElseThrow()).path("task");assertEquals(code,value.path("errorCode").asText());assertTrue(value.path("message").asText().contains(text),value.toString());}
 @Test void modifiedAndMissingModContentReachTheTaskDtoAndPersistWithoutRepair()throws Exception{
  byte[] content="verified-mod".getBytes();String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));var digest=new FileDigest(FileDigest.Algorithm.SHA256,hash);var store=new DownloadStore(root,(r,l,t,s)->s.accept(ByteBuffer.wrap(content)));var library=new ModLibrary(root,store,Clock.systemUTC());
  var file=new ModFile(new ModFileRef(ModSource.MODRINTH,"project","version","a".repeat(64)),"fixture","fixture.jar",List.of("1.21.1"),List.of("fabric"),"release",Instant.now(),(long)content.length,digest,List.of(),true,null,new DownloadSpec(RemoteService.MODRINTH_CDN,URI.create("https://cdn.modrinth.com/data/project/file.jar"),content.length,1024,digest,"fixture"));
  var entry=library.publish(new ModProject(ModSource.MODRINTH,"project","fixture",null,"",null),file,new Context());Path path=root.resolve(entry.relativeFile());byte[] changed=content.clone();changed[0]^=1;Files.write(path,changed);UUID first;
  try(var tasks=new TaskManager(root,Clock.systemUTC(),Map.of(TaskKind.MOD_VERIFY,(r,c)->{library.verify(UUID.fromString(r.inputs().get("entryId")),c.token());c.publish(()->{});}))){
   first=tasks.submit(new TaskRequest(TaskKind.MOD_VERIFY,Map.of("entryId",entry.id().toString()))).id();dto(tasks,terminal(tasks,first),"FILE_CHANGED","摘要");assertArrayEquals(changed,Files.readAllBytes(path));Files.delete(path);
   var missing=tasks.submit(new TaskRequest(TaskKind.MOD_VERIFY,Map.of("entryId",entry.id().toString())));dto(tasks,terminal(tasks,missing.id()),"FILE_MISSING","缺失");assertFalse(Files.exists(path));
  }
  try(var reopened=new TaskManager(root,Clock.systemUTC(),Map.of())){dto(reopened,reopened.get(first),"FILE_CHANGED","摘要");}
 }
 @Test void modifiedInstallationAndBusyDirectoryHaveDistinctTaskDtos()throws Exception{
  try(var f=new LauncherInstallFixture(root)){
   var plan=f.planner.plan("fixture",new CancellationToken());var receipt=f.installer.install(f.id,plan,new Context());Path path=root.resolve("instances/"+f.id+"/installations/"+receipt.installationId()+"/versions/client.jar");byte[] changed=Files.readAllBytes(path);changed[0]^=1;Files.write(path,changed);
   try(var tasks=new TaskManager(root,f.clock,Map.of(TaskKind.INSTALLATION_VERIFY,(r,c)->{f.installer.verifyActive(f.id,c.token());c.publish(()->{});},TaskKind.VANILLA_INSTALL,(r,c)->f.installer.install(f.id,plan,c)))){
    var verification=tasks.submit(new TaskRequest(TaskKind.INSTALLATION_VERIFY,Map.of("instanceId",f.id)));dto(tasks,terminal(tasks,verification.id()),"FILE_CHANGED","摘要");assertArrayEquals(changed,Files.readAllBytes(path));
    try(var lease=new LauncherStorage(root).lock(Path.of("instances/"+f.id+"/installations/.install.lock"))){var install=tasks.submit(new TaskRequest(TaskKind.VANILLA_INSTALL,Map.of("instanceId",f.id,"versionId","fixture","metadataSha1",plan.metadataSha1())));dto(tasks,terminal(tasks,install.id()),"BUSY","目录");}
   }
  }
 }
 @Test void cacheTamperingIsDistinctFromAnArbitraryNetworkException()throws Exception{
  byte[] bytes="cached".getBytes();String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));var spec=new DownloadSpec(RemoteService.MOJANG_FILES,URI.create("https://piston-data.mojang.com/file"),bytes.length,1024,new FileDigest(FileDigest.Algorithm.SHA256,hash),"fixture");var store=new DownloadStore(root,(r,l,t,s)->s.accept(ByteBuffer.wrap(bytes)));var object=store.fetch(spec,new CancellationToken(),n->{});byte[] changed=bytes.clone();changed[0]^=1;Files.write(root.resolve(object.relativeObject()),changed);
  try(var tasks=new TaskManager(root,Clock.systemUTC(),Map.of(TaskKind.INSTALLATION_VERIFY,(r,c)->{store.fetch(spec,c.token(),n->{});c.publish(()->{});},TaskKind.MOD_VERIFY,(r,c)->{throw new IOException("SECRET upstream token must not reach the UI");}))){
   var cache=tasks.submit(new TaskRequest(TaskKind.INSTALLATION_VERIFY,Map.of("instanceId",UUID.randomUUID().toString())));dto(tasks,terminal(tasks,cache.id()),"CACHE_CHANGED","摘要");assertArrayEquals(changed,Files.readAllBytes(root.resolve(object.relativeObject())));
   var arbitrary=tasks.submit(new TaskRequest(TaskKind.MOD_VERIFY,Map.of("entryId",UUID.randomUUID().toString())));var failed=terminal(tasks,arbitrary.id());assertEquals("IO_ERROR",failed.errorCode());assertFalse(failed.message().contains("SECRET"));
  }
 }
}
