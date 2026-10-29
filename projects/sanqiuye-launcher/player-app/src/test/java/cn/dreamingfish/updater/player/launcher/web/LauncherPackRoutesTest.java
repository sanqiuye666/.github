package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.packs.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LauncherPackRoutesTest {
 @TempDir java.nio.file.Path temporary;
 final AtomicInteger calls=new AtomicInteger();
 final PackProject project=new PackProject(PackSource.MODRINTH,"TestPack","测试整合包","<script>只是文本</script>",List.of("1.21.1"),List.of("fabric"));
 PackQuery seen;
 PackProvider provider=new PackProvider(){
  public java.util.List<cn.dreamingfish.launcher.content.ContentCategory> categories(CancellationToken token){return List.of(new cn.dreamingfish.launcher.content.ContentCategory("technology","technology","categories"));}
  public Page search(PackQuery q,CancellationToken t)throws IOException{seen=q;calls.incrementAndGet();t.check();return new Page(List.of(project),q.offset(),q.limit(),1,"NETWORK",Instant.EPOCH,null);}
  public Details details(String id,CancellationToken t)throws IOException{calls.incrementAndGet();t.check();return new Details(project,List.of(new PackVersion("v1","版本1",List.of("1.21.1"),List.of("fabric"),Instant.EPOCH)),"CACHE",Instant.EPOCH,null);}
 };
 @Test void readOnlyRoutesExposeBoundedDataAndExplicitUnimplementedSources()throws Exception{
  try(var routes=new PackRoutes(new PackCatalog(Map.of(PackSource.MODRINTH,provider)))){
   var sources=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.get("/packs/sources",Map.of())).orElseThrow());
   assertEquals(6,sources.path("sources").size());assertTrue(sources.toString().contains("尚未接入"));assertEquals(0,calls.get());
   var result=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.get("/packs/search",Map.of("source","MODRINTH","q","搜索","offset","0","limit","2","category","technology","gameVersion","1.20.6","loader","fabric","sort","DOWNLOADS"))).orElseThrow());assertEquals("TestPack",result.path("items").get(0).path("id").asText());
   assertEquals("technology",seen.category());assertEquals("1.20.6",seen.gameVersion());assertEquals("fabric",seen.loader());assertEquals("DOWNLOADS",seen.sort());
   var categories=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.get("/packs/categories",Map.of())).orElseThrow());assertEquals("technology",categories.path("categories").get(0).path("id").asText());assertTrue(result.path("items").get(0).path("presentation").isObject());
   var detail=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.get("/packs/MODRINTH/TestPack",Map.of())).orElseThrow());assertEquals("CACHE",detail.path("provenance").asText());assertEquals(1,detail.path("versions").size());assertFalse(detail.toString().contains("downloadUrl"));
   for(var query:List.of(Map.of("url","https://example.test"),Map.of("limit","21"),Map.of("offset","-1"),Map.of("q","bad\nheader"),Map.of("source","UNKNOWN")))assertEquals(400,assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.get("/packs/search",query))).status());
   assertEquals(405,assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.post("/packs/search",Map.of()))).status());
   assertEquals(400,assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.get("/packs/MODRINTH/%2e%2e",Map.of()))).status());
   assertEquals("SOURCE_NOT_IMPLEMENTED",assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.get("/packs/search",Map.of("source","TECHNIC")))).code());
   assertEquals(2,calls.get(),"非法输入和未接入源不触发平台请求");
  }
 }
 @Test void rateLimitPreservesRetryInformation()throws Exception{
  var limited=new PackProvider(){public Page search(PackQuery q,CancellationToken t)throws IOException{throw new RemoteHttpException(429,"RATE_LIMITED",9);}public Details details(String id,CancellationToken t){throw new AssertionError();}};
  try(var routes=new PackRoutes(new PackCatalog(Map.of(PackSource.MODRINTH,limited)))){
   var result=routes.handle(LauncherRouteFixtures.get("/packs/search",Map.of())).orElseThrow();assertEquals(429,result.status());assertEquals(9,LauncherRouteFixtures.body(result).path("error").path("retryAfterSeconds").asInt());
  }
 }
 @Test void explicitCancelStopsActiveWorkAndCancelBeforeReadDoesNotStartProvider()throws Exception{
  var started=new CountDownLatch(1);var stopped=new CountDownLatch(1);var count=new AtomicInteger();
  var blocking=new PackProvider(){public Page search(PackQuery q,CancellationToken t)throws IOException{count.incrementAndGet();started.countDown();try(var registration=t.onCancel(stopped::countDown)){try{assertTrue(stopped.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();}t.check();throw new AssertionError();}}public Details details(String id,CancellationToken t){throw new AssertionError();}};
  try(var routes=new PackRoutes(new PackCatalog(Map.of(PackSource.MODRINTH,blocking)));var workers=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   String id=UUID.randomUUID().toString();
   var pending=workers.submit(()->assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.get("/packs/search",Map.of("requestId",id)))));
   assertTrue(started.await(2,TimeUnit.SECONDS));assertEquals(200,routes.handle(LauncherRouteFixtures.post("/packs/cancel",Map.of("requestId",id))).orElseThrow().status());assertEquals("QUERY_CANCELLED",pending.get(2,TimeUnit.SECONDS).code());assertEquals(0,stopped.getCount());
   String early=UUID.randomUUID().toString();routes.handle(LauncherRouteFixtures.post("/packs/cancel",Map.of("requestId",early)));
   assertEquals("QUERY_CANCELLED",assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.get("/packs/search",Map.of("requestId",early)))).code());assertEquals(1,count.get());
   assertEquals(400,assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.post("/packs/cancel",Map.of("requestId",id,"url","https://example.test")))).status());
  }
 }
 @Test void totalBudgetCancelsTheWholeMultiRequestOperation()throws Exception{
  var cancelled=new CountDownLatch(1);
  var blocking=new PackProvider(){public Page search(PackQuery q,CancellationToken t)throws IOException{try(var registration=t.onCancel(cancelled::countDown)){try{assertTrue(cancelled.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();}t.check();throw new AssertionError();}}public Details details(String id,CancellationToken t){throw new AssertionError();}};
  try(var routes=new PackRoutes(new PackCatalog(Map.of(PackSource.MODRINTH,blocking)),Duration.ofMillis(100))){
   assertEquals("SOURCE_TIMEOUT",assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.get("/packs/search",Map.of()))).code());assertEquals(0,cancelled.getCount());
  }
 }
 @Test void realHttpGateRejectsMissingSessionAndGetBodiesBeforeCallingProvider()throws Exception{
  var directory=temporary.resolve("cn/dreamingfish/updater/player/launcher/web/ui");java.nio.file.Files.createDirectories(directory);java.nio.file.Files.writeString(directory.resolve("index.html"),"<main>fixture</main>");java.nio.file.Files.writeString(directory.resolve("build-info.json"),"{\"outputs\":{\"index.html\":\"fixture\"}}");
  try(var resources=new java.net.URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()},null);var routes=new PackRoutes(new PackCatalog(Map.of(PackSource.MODRINTH,provider)));var server=LauncherWebServer.start(0,new LauncherApi(List.of(routes)),resources);var http=java.net.http.HttpClient.newHttpClient()){
   var uri=server.baseUri().resolve("api/launcher/v1/packs/search");
   assertEquals(403,http.send(java.net.http.HttpRequest.newBuilder(uri).header("X-SQY-Client","launcher").GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());assertEquals(0,calls.get());
   var session=http.send(java.net.http.HttpRequest.newBuilder(server.baseUri().resolve("api/launcher/v1/session")).header("X-SQY-Client","launcher").GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
   String token=cn.dreamingfish.launcher.storage.LauncherJson.read(session.body(),16384).path("sessionToken").asText();
   var body=java.net.http.HttpRequest.newBuilder(uri).header("X-SQY-Client","launcher").header("X-SQY-Session",token).method("GET",java.net.http.HttpRequest.BodyPublishers.ofString("{}"));
   assertEquals(400,http.send(body.build(),java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());assertEquals(0,calls.get());
   var good=java.net.http.HttpRequest.newBuilder(uri).header("X-SQY-Client","launcher").header("X-SQY-Session",token).GET();
   assertEquals(200,http.send(good.build(),java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());assertEquals(1,calls.get());
  }
 }
}
