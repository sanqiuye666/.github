package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.GameInstance.LoaderKind;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.loaders.*;
import cn.dreamingfish.launcher.vanilla.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LauncherLoaderRoutesTest{
 @TempDir Path root;
 @Test void previewAndOneUseConfirmationPinActualKindAndDigests()throws Exception{
  try(var f=new LauncherInstallFixture(root);var tasks=new TaskManager(root,f.clock,Map.of(TaskKind.LOADER_INSTALL,(r,c)->c.publish(()->{})))){
   LoaderProvider provider=new LoaderProvider(){public LoaderKind kind(){return LoaderKind.NEOFORGE;}public List<LoaderVersions.Version> versions(String game,CancellationToken token){return List.of(new LoaderVersions.Version("20.6.139",true));}public LoaderPlan pinned(String g,String m,String l,String s,String p,CancellationToken t){throw new AssertionError();}public LoaderPlan plan(String game,String version,CancellationToken token)throws java.io.IOException{var base=f.planner.plan(game,token);var files=new ArrayList<>(base.files());files.add(new InstallFile(Path.of("versions/installer.jar"),new DownloadSpec(RemoteService.NEOFORGE_MAVEN,URI.create("https://maven.neoforged.net/releases/example.jar"),1,1024,new FileDigest(FileDigest.Algorithm.SHA256,"a".repeat(64)),"fixture")));return LoaderPlan.fixed(new VanillaPlan(game,base.metadataSha1(),base.platform(),21,files,0,null,new LoaderIdentity(kind(),version)),"b".repeat(64));}};
   try(var routes=new InstallationRoutes(f.planner,f.installer,tasks,f.instances,f.clock,null,Map.of(LoaderKind.NEOFORGE,provider))){var list=LauncherRouteFixtures.body(routes.handle(new ApiRequest("GET","/installations/loader/versions",Map.of("instanceId",f.id,"loader","NEOFORGE"),null)).orElseThrow());assertEquals("20.6.139",list.path("versions").get(0).path("version").asText());var plan=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.post("/installations/plan",Map.of("instanceId",f.id,"loader","NEOFORGE","loaderVersion","20.6.139"))).orElseThrow());assertEquals("NEOFORGE",plan.path("loader").asText());assertEquals(0,tasks.listing(0,50).total());var request=LauncherRouteFixtures.post("/installations",Map.of("planId",plan.path("planId").asText()));var task=LauncherRouteFixtures.body(routes.handle(request).orElseThrow()).path("task");assertEquals("LOADER_INSTALL",task.path("kind").asText());assertEquals("NEOFORGE",task.path("inputs").path("loader").asText());assertEquals(64,task.path("inputs").path("sourceSha256").asText().length());assertTrue(task.path("inputs").has("planSha256"));assertThrows(LauncherApiException.class,()->routes.handle(request));}
  }
 }
}
