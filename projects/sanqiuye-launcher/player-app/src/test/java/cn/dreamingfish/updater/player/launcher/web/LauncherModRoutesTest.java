package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.mods.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class LauncherModRoutesTest {
 @TempDir Path root;
 @Test void onlyDefinedReferencesReachTheQueueAndSourceLimitsAreExplicit()throws Exception{
  var cfCalls=new AtomicInteger();var limited=new AtomicBoolean();byte[] content="mod".getBytes();String digest=HexFormat.of().formatHex(new FileDigest(FileDigest.Algorithm.SHA512,"0".repeat(128)).newDigest().digest(content));
  RemoteClient remote=(request,limit,token,sink)->{if(request.service()==RemoteService.CURSEFORGE_API){cfCalls.incrementAndGet();throw new AssertionError();}if(request.service()==RemoteService.MODRINTH_CDN){sink.accept(ByteBuffer.wrap(content));return;}String path=request.uri().getPath();Object result;
   if(path.endsWith("/category"))result=List.of(Map.of("name","utility","project_type","mod","header","categories"));
   else if(path.endsWith("/loader"))result=List.of(Map.of("name","fabric","supported_project_types",List.of("mod")));
   else if(path.endsWith("/search")){if(limited.get())throw new RemoteHttpException(429,"RATE_LIMITED",60);result=Map.of("hits",List.of(),"total_hits",0);}
   else if(path.equals("/v2/project/project/version")||path.equals("/v2/version/version")){var version=Map.of("id","version","project_id","project","name","version","game_versions",List.of("1.21.1"),"loaders",List.of("fabric"),"date_published","2026-01-01T00:00:00Z","files",List.of(Map.of("filename","mod.jar","primary",true,"hashes",Map.of("sha512",digest),"size",content.length,"url","https://cdn.modrinth.com/data/project/mod.jar")));result=path.equals("/v2/project/project/version")?List.of(version):version;}
   else result=Map.of("id","project","project_type","mod","title","fixture","description","text","icon_url","https://cdn.modrinth.com/icon.png","body","完整介绍");sink.accept(ByteBuffer.wrap(LauncherJson.write(result)));};
  var clock=Clock.systemUTC();var library=new ModLibrary(root,new DownloadStore(root,remote),clock);var service=new ModService(Map.of(ModSource.MODRINTH,new ModrinthProvider(remote,clock),ModSource.CURSEFORGE,new CurseForgeProvider(remote,()->null,clock)),library);
  try(var tasks=new TaskManager(root,clock,Map.of(TaskKind.MOD_DOWNLOAD,service::runDownload,TaskKind.MOD_VERIFY,(r,c)->{library.verify(UUID.fromString(r.inputs().get("entryId")),c.token());c.publish(()->{});}))) {
   var routes=new ModRoutes(service,library,tasks);var sources=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.get("/mods/sources",Map.of())).orElseThrow());assertTrue(sources.toString().contains("UNCONFIGURED"));
   assertEquals("SOURCE_UNCONFIGURED",assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.get("/mods/search",Map.of("source","CURSEFORGE")))).code());assertEquals(0,cfCalls.get());
   var categories=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.get("/mods/categories",Map.of())).orElseThrow());assertEquals("utility",categories.path("categories").get(0).path("id").asText());
   assertEquals(200,routes.handle(LauncherRouteFixtures.get("/mods/search",Map.of("category","utility"))).orElseThrow().status());
   assertEquals("UNKNOWN_CATEGORY",assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.get("/mods/search",Map.of("category","magic")))).code());
   var metadata=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.get("/mods/project",Map.of("projectId","project"))).orElseThrow()).path("project").path("presentation");assertEquals("完整介绍",metadata.path("description").asText());assertEquals("https://cdn.modrinth.com/icon.png",metadata.path("iconUrl").asText());
   for(var extra:List.of("url","destination","apiKey"))assertEquals(400,assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.post("/mods/downloads",Map.of(extra,"not-allowed")))).status());
   var files=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.get("/mods/files",Map.of("source","MODRINTH","projectId","project","gameVersion","1.21.1","loader","fabric"))).orElseThrow());assertFalse(files.toString().contains("downloadSpec"));assertFalse(files.toString().contains("cdn.modrinth.com"));
   var file=files.path("items").get(0);var ref=new HashMap<String,Object>();file.path("ref").fields().forEachRemaining(e->ref.put(e.getKey(),e.getValue().asText()));assertEquals(202,routes.handle(LauncherRouteFixtures.post("/mods/downloads",Map.of("ref",ref,"gameVersion","1.21.1","loader","fabric"))).orElseThrow().status());ref.put("url","https://example.invalid");assertEquals(400,assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.post("/mods/downloads",Map.of("ref",ref,"gameVersion","1.21.1","loader","fabric")))).status());
   limited.set(true);var error=routes.handle(LauncherRouteFixtures.get("/mods/search",Map.of("source","MODRINTH"))).orElseThrow();assertEquals(429,error.status());assertEquals(60,LauncherRouteFixtures.body(error).path("error").path("retryAfterSeconds").asInt());
  }
 }
}
