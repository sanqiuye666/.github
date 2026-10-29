package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class CurseForgeProviderTest {
 static Map<String,Object> project(){var p=new HashMap<String,Object>();p.put("id",123);p.put("gameId",432);p.put("classId",6);p.put("name","title");p.put("summary","summary");p.put("isAvailable",true);p.put("allowModDistribution",null);p.put("authors",List.of());return p;}
 static Map<String,Object> file(){return new HashMap<>(Map.ofEntries(Map.entry("id",456),Map.entry("modId",123),Map.entry("gameId",432),Map.entry("fileName","mod.jar"),Map.entry("isAvailable",true),Map.entry("fileStatus",4),Map.entry("releaseType",1),Map.entry("fileDate","2026-01-01T00:00:00Z"),Map.entry("fileLength",3),Map.entry("downloadUrl","https://edge.forgecdn.net/files/0/mod.jar"),Map.entry("hashes",List.of(Map.of("algo",1,"value","a".repeat(40)))),Map.entry("gameVersions",List.of("1.21.1","Fabric")),Map.entry("dependencies",java.util.stream.IntStream.rangeClosed(1,6).mapToObj(i->Map.of("modId",1000+i,"relationType",i)).toList())));}
 @Test void missingKeyDoesNotMakeRequests()throws Exception {
  var calls=new AtomicInteger();var provider=new CurseForgeProvider((r,l,t,s)->{calls.incrementAndGet();throw new AssertionError("No unauthenticated request");},()->null,Clock.systemUTC());
  assertEquals("UNCONFIGURED",provider.status(new CancellationToken()).state());assertEquals("SOURCE_UNCONFIGURED",assertThrows(TaskFailure.class,()->provider.search(new ModQuery("","","",ModQuery.Sort.RELEVANCE,0,20),new CancellationToken())).code());assertEquals(0,calls.get());assertFalse(provider.toString().contains("null"));
 }
 @Test void correctReadOnlyQueriesLoaderMappingPaginationAndSecretBoundary()throws Exception{
  var requests=new ArrayList<RemoteRequest>();var provider=new CurseForgeProvider(ModrinthProviderTest.transport(requests,r->Map.of("data",List.of(project()),"pagination",Map.of("index",0,"pageSize",20,"resultCount",1,"totalCount",20000))),()->"fake-test-key",Clock.systemUTC());
  String[] loaders={"forge","cauldron","liteloader","fabric","quilt","neoforge"};
  for(int i=0;i<loaders.length;i++){var page=provider.search(new ModQuery("a & 中文","1.21.1",loaders[i],ModQuery.Sort.DOWNLOADS,0,20),new CancellationToken());var request=requests.getLast();var q=ModrinthProviderTest.query(request.uri());assertEquals("432",q.get("gameId"));assertEquals("6",q.get("classId"));assertEquals(Integer.toString(i+1),q.get("modLoaderType"));assertEquals("6",q.get("sortField"));assertEquals("a & 中文",q.get("searchFilter"));assertTrue(page.platformLimited());assertEquals(10000,page.accessibleTotal());assertEquals("fake-test-key",request.headers().get("x-api-key"));assertEquals(RemoteService.CURSEFORGE_API,request.service());assertFalse(request.toString().contains("fake-test-key"));}
  assertThrows(IllegalArgumentException.class,()->provider.search(new ModQuery("","","fabric",ModQuery.Sort.RELEVANCE,0,20),new CancellationToken()));assertThrows(IllegalArgumentException.class,()->provider.search(new ModQuery("","","",ModQuery.Sort.RELEVANCE,9990,20),new CancellationToken()));
 }
 @Test void distributionAvailabilityHashesAndEarlyAccessAreNotBypassed()throws Exception{
  var current=new AtomicReference<>(file());var p=project();var requests=new ArrayList<RemoteRequest>();var provider=new CurseForgeProvider(ModrinthProviderTest.transport(requests,r->Map.of("data",r.uri().getPath().equals("/v1/mods/123")?p:r.uri().getPath().endsWith("/files")?List.of(current.get()):current.get(),"pagination",Map.of("totalCount",1))),()->"fake-test-key",Clock.systemUTC());
  var query=new ModFileQuery("1.21.1","fabric",0,20);var valid=provider.files("123",query,new CancellationToken()).items().getFirst();assertTrue(valid.downloadable());assertEquals(6,valid.dependencies().size());assertEquals(FileDigest.Algorithm.SHA1,valid.sourceDigest().algorithm());
  for(var change:List.<java.util.function.Consumer<Map<String,Object>>>of(f->f.put("downloadUrl",null),f->f.put("isAvailable",false),f->f.put("hashes",List.of(Map.of("algo",2,"value","a".repeat(32)))),f->{f.put("isEarlyAccessContent",true);f.put("earlyAccessEndDate","2099-01-01T00:00:00Z");},f->f.put("gameVersions",List.of()))){var bad=file();change.accept(bad);current.set(bad);var files=provider.files("123",new ModFileQuery("","",0,20),new CancellationToken());assertFalse(files.items().getFirst().downloadable());assertNull(files.items().getFirst().downloadSpec());}
  current.set(file());p.put("allowModDistribution",false);assertFalse(provider.files("123",query,new CancellationToken()).items().getFirst().downloadable());assertTrue(requests.stream().allMatch(r->r.uri().getHost().equals("api.curseforge.com")));assertFalse(requests.stream().anyMatch(r->r.uri().getPath().contains("download-url")));
 }
}
