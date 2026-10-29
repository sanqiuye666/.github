package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class LauncherInstallationRoutesTest {
 @TempDir Path root;
 @Test void planningDoesNotSubmitTaskAndConfirmationIsSingleUse()throws Exception{
  try(var f=new LauncherInstallFixture(root);var tasks=new TaskManager(root,f.clock,Map.of(TaskKind.VANILLA_INSTALL,(r,c)->c.publish(()->{})))){
   var routes=new InstallationRoutes(f.planner,f.installer,tasks,f.instances,f.clock);
   var plan=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.post("/installations/plan",Map.of("instanceId",f.id))).orElseThrow());
   assertEquals(0,tasks.listing(0,50).total());assertTrue(plan.path("fileCount").asInt()>=3);String planId=plan.path("planId").asText();
   assertEquals(202,routes.handle(LauncherRouteFixtures.post("/installations",Map.of("planId",planId))).orElseThrow().status());
   assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.post("/installations",Map.of("planId",planId))));assertEquals(1,tasks.listing(0,50).total());
   assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.post("/installations/plan",Map.of("instanceId",UUID.randomUUID().toString()))));
  }
 }
 @Test void longVerificationDoesNotBlockHttpAndCancellationReachesWorker()throws Exception {
  var began=new CountDownLatch(1);var cancelled=new CountDownLatch(1);
  try(var f=new LauncherInstallFixture(root);var tasks=new TaskManager(root,f.clock,Map.of(TaskKind.INSTALLATION_VERIFY,(r,c)->{began.countDown();try{while(true){c.token().check();try{Thread.sleep(5);}catch(InterruptedException e){Thread.currentThread().interrupt();c.token().check();}}}finally{cancelled.countDown();}}))){
   var routes=new InstallationRoutes(f.planner,f.installer,tasks,f.instances,f.clock);
   try(var server=LauncherWebServer.start(0,new LauncherApi(List.of(routes,new TaskRoutes(tasks))),getClass().getClassLoader());var http=HttpClient.newHttpClient()){
    var session=HttpRequest.newBuilder(server.baseUri().resolve("api/launcher/v1/session")).timeout(Duration.ofSeconds(2)).header("X-SQY-Client","launcher").build();
    String token=LauncherJson.read(http.send(session,HttpResponse.BodyHandlers.ofByteArray()).body(),16384).path("sessionToken").asText();
    var request=HttpRequest.newBuilder(server.baseUri().resolve("api/launcher/v1/installations/verify")).timeout(Duration.ofSeconds(2)).header("X-SQY-Client","launcher").header("X-SQY-Session",token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(LauncherJson.write(Map.of("instanceId",f.id)))).build();
    long start=System.nanoTime();var response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());assertEquals(202,response.statusCode());assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1000);assertTrue(began.await(2,TimeUnit.SECONDS));assertEquals(200,http.send(session,HttpResponse.BodyHandlers.discarding()).statusCode());
    var id=UUID.fromString(LauncherJson.read(response.body(),16384).path("task").path("id").asText());assertEquals(TaskState.CANCELLED,tasks.cancel(id).state());assertTrue(cancelled.await(2,TimeUnit.SECONDS));
   }
  }
 }
 @Test void expiredPlansAndChangedMetadataAreRejected()throws Exception {
  class MovingClock extends Clock {Instant now=Instant.now();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}}
  var clock=new MovingClock();
  try(var f=new LauncherInstallFixture(root);var tasks=new TaskManager(root,clock,Map.of(TaskKind.VANILLA_INSTALL,(r,c)->c.publish(()->{})))){
   var routes=new InstallationRoutes(f.planner,f.installer,tasks,f.instances,clock);var plan=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.post("/installations/plan",Map.of("instanceId",f.id))).orElseThrow());clock.now=clock.now.plusSeconds(601);
   assertEquals(410,assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.post("/installations",Map.of("planId",plan.path("planId").asText())))).status());assertEquals(0,tasks.listing(0,50).total());assertEquals("PLAN_CHANGED",assertThrows(TaskFailure.class,()->f.planner.planPinned("fixture","a".repeat(40),new CancellationToken())).code());
  }
 }
}
