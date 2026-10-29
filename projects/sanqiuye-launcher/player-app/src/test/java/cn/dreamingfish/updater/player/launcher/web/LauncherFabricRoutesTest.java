package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.loaders.fabric.FabricPlanner;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LauncherFabricRoutesTest {
 @TempDir Path root;
 @Test void cancelBeforeQueryArrivalNeverStartsOfficialRequest()throws Exception{
  try(var f=new LauncherInstallFixture(root);var tasks=new TaskManager(root,f.clock,Map.of())){
   var calls=new java.util.concurrent.atomic.AtomicInteger();RemoteClient remote=(r,n,t,s)->{calls.incrementAndGet();throw new java.io.IOException("unexpected");};
   try(var routes=new InstallationRoutes(f.planner,f.installer,tasks,f.instances,f.clock,new FabricPlanner(f.planner,f.store,remote))){
    String id=UUID.randomUUID().toString();routes.handle(LauncherRouteFixtures.post("/installations/cancel-query",Map.of("requestId",id)));
    assertEquals("QUERY_CANCELLED",assertThrows(LauncherApiException.class,()->routes.handle(new ApiRequest("GET","/installations/fabric/versions",Map.of("instanceId",f.id,"requestId",id),null))).code());assertEquals(0,calls.get());
   }
  }
 }
 @Test void slowMetadataIsCancelledByWholeQueryBudgetAndReleasesPermit()throws Exception{
  try(var f=new LauncherInstallFixture(root);var tasks=new TaskManager(root,f.clock,Map.of())){
   var cancelled=new java.util.concurrent.atomic.AtomicBoolean();RemoteClient remote=(r,n,t,s)->{try{while(true){t.check();Thread.sleep(2);}}catch(InterruptedException e){throw new java.io.InterruptedIOException();}finally{cancelled.set(t.isCancelled());}};
   try(var routes=new InstallationRoutes(f.planner,f.installer,tasks,f.instances,f.clock,new FabricPlanner(f.planner,f.store,remote),java.time.Duration.ofMillis(30))){
    for(int i=0;i<4;i++){var error=assertThrows(LauncherApiException.class,()->routes.handle(new ApiRequest("GET","/installations/fabric/versions",Map.of("instanceId",f.id),null)));assertEquals("SOURCE_TIMEOUT",error.code());assertTrue(cancelled.get());}
   }
  }
 }
 @Test void queriesAndPreviewsDoNotInstallAndConfirmationPinsAllInputsOnce()throws Exception{
  try(var f=new LauncherInstallFixture(root);var tasks=new TaskManager(root,f.clock,Map.of(TaskKind.FABRIC_INSTALL,(r,c)->c.publish(()->{})))){
   var libs=new ArrayList<Map<String,Object>>();for(String artifact:List.of("intermediary","fabric-loader"))libs.add(Map.of("name","net.fabricmc:"+artifact+":"+(artifact.equals("intermediary")?"fixture":"0.16.14"),"url","https://maven.fabricmc.net/","sha256","a".repeat(64),"size",4));
   byte[] profile=LauncherJson.write(Map.of("id","fabric-loader-0.16.14-fixture","inheritsFrom","fixture","mainClass","net.fabricmc.loader.impl.launch.knot.KnotClient","libraries",libs,"arguments",Map.of("jvm",List.of(),"game",List.of())));
   RemoteClient remote=(r,max,t,s)->{t.check();s.accept(ByteBuffer.wrap(r.uri().toString().endsWith("profile/json")?profile:LauncherJson.write(List.of(Map.of("loader",Map.of("version","0.16.14","stable",true),"intermediary",Map.of("version","fixture"))))));};
   var fabric=new FabricPlanner(f.planner,f.store,remote);var routes=new InstallationRoutes(f.planner,f.installer,tasks,f.instances,f.clock,fabric);
   var versions=LauncherRouteFixtures.body(routes.handle(new ApiRequest("GET","/installations/fabric/versions",Map.of("instanceId",f.id),null)).orElseThrow());assertEquals("0.16.14",versions.path("versions").get(0).path("version").asText());
   var plan=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.post("/installations/plan",Map.of("instanceId",f.id,"loader","FABRIC","loaderVersion","0.16.14"))).orElseThrow());assertEquals("0.16.14",plan.path("fabricVersion").asText());assertEquals(0,tasks.listing(0,50).total());
   var body=LauncherRouteFixtures.post("/installations",Map.of("planId",plan.path("planId").asText()));var result=LauncherRouteFixtures.body(routes.handle(body).orElseThrow()).path("task");assertEquals("FABRIC_INSTALL",result.path("kind").asText());assertTrue(result.path("inputs").has("profileSha256"));assertTrue(result.path("inputs").has("planSha256"));assertThrows(LauncherApiException.class,()->routes.handle(body));assertEquals(1,tasks.listing(0,50).total());
   assertThrows(IllegalArgumentException.class,()->routes.handle(LauncherRouteFixtures.post("/installations/plan",Map.of("instanceId",f.id,"loader","FORGE","loaderVersion","x"))));
  }
 }
}
