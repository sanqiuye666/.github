package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.instances.FileInstanceCatalog;
import cn.dreamingfish.launcher.vanilla.VanillaInstaller;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;
import java.nio.*;import java.nio.file.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InstanceModQueueTest{
 @TempDir Path root;
 @Test void fixedQueueRechecksPlanAndInstallsAndRemovesWithoutTouchingManual()throws Exception{
  String id=InstanceModStoreTest.instance(root);byte[] bytes=InstanceModStoreTest.jar("demo");var file=InstanceModStoreTest.file("demo","v1",bytes);var provider=new ModDependencyPlanTest.Provider(file);var clock=InstanceModStoreTest.CLOCK;
  var downloads=new DownloadStore(root,(r,l,t,s)->s.accept(ByteBuffer.wrap(bytes)));try(var batch=new DownloadBatch(downloads,1)){
   var store=new InstanceModStore(root,new VanillaInstaller(root,new FileInstanceCatalog(root,clock),downloads,batch,clock),downloads,batch,clock);var service=new InstanceModService(new ModService(Map.of(ModSource.MODRINTH,provider),new ModLibrary(root,downloads,clock)),store);
   var plan=service.plan(id,file.ref(),new CancellationToken());var request=service.request(plan);assertEquals(TaskKind.MOD_INSTALL,request.kind());assertFalse(request.inputs().toString().contains("http"));
   Files.createDirectories(root.resolve("instances/"+id+"/.minecraft/mods"));Files.writeString(root.resolve("instances/"+id+"/.minecraft/mods/manual.txt"),"keep");assertEquals("PLAN_CHANGED",assertThrows(TaskFailure.class,()->service.run(request,new InstanceModStoreTest.Context())).code());
   service.run(service.request(service.plan(id,file.ref(),new CancellationToken())),new InstanceModStoreTest.Context());assertEquals(1,store.listing(id,new CancellationToken()).entries().size());
   service.run(service.request(service.removal(id,new CancellationToken())),new InstanceModStoreTest.Context());assertTrue(store.listing(id,new CancellationToken()).entries().isEmpty());assertEquals("keep",Files.readString(root.resolve("instances/"+id+"/.minecraft/mods/manual.txt")));
   var extra=new HashMap<>(request.inputs());extra.put("destination","other");assertThrows(IllegalArgumentException.class,()->new TaskRequest(TaskKind.MOD_INSTALL,extra));extra.remove("destination");extra.put("planSha256","bad");assertThrows(IllegalArgumentException.class,()->new TaskRequest(TaskKind.MOD_INSTALL,extra));
  }
 }
}
