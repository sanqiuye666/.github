package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.downloads.*;import cn.dreamingfish.launcher.mods.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InstanceModRoutesTest{
 @TempDir Path root;
 @Test void readPreviewAndSingleConfirmationUseFixedQueueWithoutURLs()throws Exception{
  try(var f=new LauncherInstallFixture(root)){
   f.installer.install(f.id,f.planner.plan("fixture",new CancellationToken()),new TaskContext(){public UUID id(){return UUID.randomUUID();}public CancellationToken token(){return new CancellationToken();}public void progress(TaskState s,long b,Long t,long n,Long c){}public void publish(IoAction a)throws java.io.IOException{a.run();}});
   var service=new InstanceModService(new ModService(Map.of(),new ModLibrary(root,f.store,f.clock)),new InstanceModStore(root,f.installer,f.store,f.batch,f.clock));
   try(var tasks=new TaskManager(root,f.clock,Map.of(TaskKind.MOD_REMOVE,service::run));var routes=new InstanceModRoutes(service,tasks,f.clock)){
    var list=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.get("/mods/instance",Map.of("instanceId",f.id))).orElseThrow());assertTrue(list.path("entries").isEmpty());
    for(String extra:List.of("url","destination","key"))assertEquals(400,assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.post("/mods/instance/plan",Map.of("instanceId",f.id,extra,"blocked")))).status());
    var plan=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.post("/mods/instance/plan",Map.of("instanceId",f.id,"operation","REMOVE"))).orElseThrow());assertFalse(plan.toString().contains("downloadSpec"));assertEquals(0,tasks.listing(0,50).total());
    var request=LauncherRouteFixtures.post("/mods/instance/apply",Map.of("planId",plan.path("planId").asText()));var task=LauncherRouteFixtures.body(routes.handle(request).orElseThrow()).path("task");assertEquals("MOD_REMOVE",task.path("kind").asText());assertTrue(task.path("inputs").has("planSha256"));assertEquals(410,assertThrows(LauncherApiException.class,()->routes.handle(request)).status());
   }
  }
 }
}
