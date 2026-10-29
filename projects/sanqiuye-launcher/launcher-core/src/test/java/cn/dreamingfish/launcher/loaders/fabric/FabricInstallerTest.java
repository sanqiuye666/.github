package cn.dreamingfish.launcher.loaders.fabric;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.instances.*;
import cn.dreamingfish.launcher.storage.*;
import cn.dreamingfish.launcher.vanilla.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FabricInstallerTest {
 @TempDir Path root;
 @Test void historicalLiveProcessBlocksInstallAfterCoreLockIsReleased()throws Exception{
  var f=VanillaFixtures.setup(root,false,false,false);var instances=new FileInstanceCatalog(root,VanillaFixtures.CLOCK);String id=instances.create("historical","26.3",f.catalog().load(false).manifest()).instance().id();var plan=new VanillaPlanner(f.catalog(),f.store(),VanillaFixtures.WINDOWS).plan("26.3",new CancellationToken());
  String launch=UUID.randomUUID().toString();Path record=root.resolve("instances/"+id+"/launches/"+launch+"/launch.json");Files.createDirectories(record.getParent());var values=new LinkedHashMap<String,Object>();values.put("schemaVersion",1);values.put("id",launch);values.put("instanceId",id);values.put("installationId",UUID.randomUUID().toString());values.put("state","RUNNING");values.put("createdAt",java.time.Instant.now().toString());for(String key:List.of("startedAt","endedAt","exitCode","errorCode"))values.put(key,null);values.put("message","test");values.put("pid",ProcessHandle.current().pid());values.put("processStartedAt",ProcessHandle.current().info().startInstant().orElseThrow().toString());Files.write(record,LauncherJson.write(values));
  try(var batch=new DownloadBatch(f.store(),4)){
   var installer=new VanillaInstaller(root,instances,f.store(),batch,VanillaFixtures.CLOCK);assertEquals("INSTANCE_BUSY",assertThrows(cn.dreamingfish.launcher.launch.LaunchFailure.class,()->installer.install(id,plan,new Context())).code());assertFalse(Files.exists(root.resolve("instances/"+id+"/active-installation.json")));
   values.put("processStartedAt",java.time.Instant.EPOCH.toString());Files.write(record,LauncherJson.write(values));assertNotNull(installer.install(id,plan,new Context()));
  }
 }
 static class Context implements TaskContext {final UUID id=UUID.randomUUID();final CancellationToken token=new CancellationToken();public UUID id(){return id;}public CancellationToken token(){return token;}public void progress(TaskState s,long b,Long total,long f,Long n){}public void publish(IoAction action)throws java.io.IOException{token.check();action.run();}}
 @Test void fabricUsesOneFixedSnapshotAndPreservesOriginalGameAndOtherInstance()throws Exception{
  var f=VanillaFixtures.setup(root,"1.20.6",false,false,false);var instances=new FileInstanceCatalog(root,VanillaFixtures.CLOCK);String id=instances.create("target","1.20.6",f.catalog().load(false).manifest()).instance().id();String other=instances.create("other","1.20.6",f.catalog().load(false).manifest()).instance().id();Path game=root.resolve("instances/"+id+"/.minecraft/options.txt"),keep=root.resolve("instances/"+other+"/.minecraft/options.txt");Files.writeString(game,"original settings");Files.writeString(keep,"other settings");
  try(var batch=new DownloadBatch(f.store(),4)){
   var vanilla=new VanillaPlanner(f.catalog(),f.store(),VanillaFixtures.WINDOWS);var installer=new VanillaInstaller(root,instances,f.store(),batch,VanillaFixtures.CLOCK);installer.install(id,vanilla.plan("1.20.6",new CancellationToken()),new Context());
   String endpoint="https://meta.fabricmc.net/v2/versions/loader/1.20.6";f.responses().put(endpoint,LauncherJson.write(List.of(Map.of("loader",Map.of("version","0.16.14","stable",true),"intermediary",Map.of("version","1.20.6")))));f.responses().put(endpoint+"/0.16.14/profile/json",LauncherJson.write(FabricProfileTest.profile()));
   for(String artifact:List.of("intermediary","fabric-loader")){String v=artifact.equals("intermediary")?"1.20.6":"0.16.14",url="https://maven.fabricmc.net/net/fabricmc/"+artifact+"/"+v+"/"+artifact+"-"+v+".jar";byte[] bytes=artifact.getBytes();f.responses().put(url,bytes);f.responses().put(url+".sha1",VanillaFixtures.sha1(bytes).getBytes());}
   RemoteClient remote=(r,n,t,s)->s.accept(ByteBuffer.wrap(f.responses().get(r.uri().toString())));var fabric=new FabricPlanner(vanilla,f.store(),remote).plan("1.20.6","0.16.14",new CancellationToken());var installed=installer.install(id,fabric.installation(),new Context());assertEquals("0.16.14",installer.verifyActive(id,new CancellationToken()).fabricVersion());assertEquals(2,LauncherJson.read(installed.bytes(),65536).path("schemaVersion").intValue());assertEquals("original settings",Files.readString(game));assertEquals("other settings",Files.readString(keep));
   byte[] active=Files.readAllBytes(root.resolve("instances/"+id+"/active-installation.json"));var cancelled=new Context();cancelled.token.cancel();assertThrows(java.io.IOException.class,()->installer.install(id,fabric.installation(),cancelled));assertArrayEquals(active,Files.readAllBytes(root.resolve("instances/"+id+"/active-installation.json")));
  }
 }
 @Test void activeGameLockRejectsInstallBeforeAnyActiveReferenceChanges()throws Exception{
  var f=VanillaFixtures.setup(root,false,false,false);var instances=new FileInstanceCatalog(root,VanillaFixtures.CLOCK);String id=instances.create("busy","26.3",f.catalog().load(false).manifest()).instance().id();var plan=new VanillaPlanner(f.catalog(),f.store(),VanillaFixtures.WINDOWS).plan("26.3",new CancellationToken());try(var batch=new DownloadBatch(f.store(),4);var running=new LauncherStorage(root).lock(Path.of("instances",id,".running.lock"))){var installer=new VanillaInstaller(root,instances,f.store(),batch,VanillaFixtures.CLOCK);assertThrows(LauncherBusyException.class,()->installer.install(id,plan,new Context()));assertFalse(Files.exists(root.resolve("instances/"+id+"/active-installation.json")));}
 }
}
