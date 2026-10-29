package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class ModrinthProviderTest {
 @Test void exactDependencyCanUseVersionOnlyAndNeverCrossesProject()throws Exception{
  var requests=new ArrayList<RemoteRequest>();var provider=new ModrinthProvider(transport(requests,r->version()),Clock.systemUTC());
  var result=provider.dependency(new ModDependency(ModDependency.Kind.REQUIRED,null,"version",null),new ModFileQuery("1.21.1","fabric",0,50),new CancellationToken());
  assertEquals("project",result.ref().projectId());assertEquals("version",result.ref().versionId());assertEquals("mod.jar",result.fileName());assertEquals("/v2/version/version",requests.getFirst().uri().getPath());
  assertThrows(IOException.class,()->provider.dependency(new ModDependency(ModDependency.Kind.REQUIRED,"wrong","version",null),new ModFileQuery("1.21.1","fabric",0,50),new CancellationToken()));
 }
 static Map<String,String> query(URI uri){var result=new HashMap<String,String>();for(String part:uri.getRawQuery().split("&")){var pair=part.split("=",2);result.put(URLDecoder.decode(pair[0],java.nio.charset.StandardCharsets.UTF_8),URLDecoder.decode(pair[1],java.nio.charset.StandardCharsets.UTF_8));}return result;}
 static Map<String,Object> file(String name,boolean primary){return Map.of("filename",name,"primary",primary,"url","https://cdn.modrinth.com/data/project/versions/version/"+name,"size",3,"hashes",Map.of("sha512","a".repeat(128)));}
 static Map<String,Object> version(){return Map.of("id","version","project_id","project","name","普通版本","game_versions",List.of("1.21.1"),"loaders",List.of("fabric"),"version_type","release","date_published","2026-01-01T00:00:00Z","files",List.of(file("mod-sources.jar",true),file("mod.jar",false)),"dependencies",List.of(Map.of("dependency_type","required","project_id","required"),Map.of("dependency_type","optional","project_id","optional"),Map.of("dependency_type","incompatible","project_id","incompatible"),Map.of("dependency_type","embedded","project_id","embedded")));}
 static RemoteClient transport(List<RemoteRequest> requests,java.util.function.Function<RemoteRequest,Object> response){return (r,l,t,s)->{requests.add(r);s.accept(ByteBuffer.wrap(LauncherJson.write(response.apply(r))));};}
 @Test void structuredSearchEscapesQueryAndUsesActualTags()throws Exception{
  var requests=new ArrayList<RemoteRequest>();var provider=new ModrinthProvider(transport(requests,r->r.uri().getPath().endsWith("/loader")?List.of(Map.of("name","fabric","supported_project_types",List.of("mod")),Map.of("name","paper","supported_project_types",List.of("plugin"))):Map.of("hits",List.of(Map.of("project_id","project","project_type","mod","title","名称","description","summary","author","author","downloads",10)),"total_hits",1)),Clock.systemUTC());
  for(var sort:ModQuery.Sort.values()){var page=provider.search(new ModQuery("a & 中文","1.21.1","fabric",sort,20,20),new CancellationToken());assertEquals(1,page.items().size());var query=query(requests.getLast().uri());assertEquals("a & 中文",query.get("query"));assertEquals(sort.name().toLowerCase(Locale.ROOT),query.get("index"));assertEquals("20",query.get("offset"));assertEquals(List.of(List.of("project_type:mod"),List.of("versions:1.21.1"),List.of("categories:fabric")),new com.fasterxml.jackson.databind.ObjectMapper().readValue(query.get("facets"),List.class));}
  assertEquals(List.of("fabric"),provider.status(new CancellationToken()).loaders());assertEquals(1,requests.stream().filter(r->r.uri().getPath().endsWith("/loader")).count());assertTrue(requests.stream().allMatch(r->r.service()==RemoteService.MODRINTH_API));
 }
 @Test void versionFilesPreserveCompatibilityAndOrdinarySelection()throws Exception{
  var requests=new ArrayList<RemoteRequest>();var provider=new ModrinthProvider(transport(requests,r->r.uri().getPath().endsWith("/loader")?List.of(Map.of("name","fabric","supported_project_types",List.of("mod"))):r.uri().getPath().startsWith("/v2/version/")?version():r.uri().getPath().endsWith("/version")?List.of(version()):Map.of("id","project","project_type","mod","title","title","description","summary")),Clock.systemUTC());
  var files=provider.files("project",new ModFileQuery("1.21.1","fabric",0,20),new CancellationToken());var query=query(requests.stream().filter(r->r.uri().getPath().endsWith("/version")).findFirst().orElseThrow().uri());assertEquals("[\"fabric\"]",query.get("loaders"));assertEquals("[\"1.21.1\"]",query.get("game_versions"));assertEquals("false",query.get("include_changelog"));
  var ordinary=files.items().stream().filter(ModFile::downloadable).toList();assertEquals(1,ordinary.size());assertEquals("mod.jar",ordinary.getFirst().fileName());assertEquals(4,ordinary.getFirst().dependencies().size());assertEquals(FileDigest.Algorithm.SHA512,ordinary.getFirst().sourceDigest().algorithm());assertFalse(files.items().stream().filter(f->f.fileName().contains("sources")).findFirst().orElseThrow().downloadable());
  assertEquals(ordinary.getFirst().ref(),provider.resolve(ordinary.getFirst().ref(),new CancellationToken()).ref());assertThrows(IOException.class,()->provider.resolve(new ModFileRef(ModSource.MODRINTH,"other","version",ordinary.getFirst().ref().fileKey()),new CancellationToken()));
 }
 @Test void malformedHashesAndOversizedArraysNeverBecomeDownloads()throws Exception{
  var version=new HashMap<>(version());var bad=new HashMap<>(file("bad.jar",true));bad.put("hashes",Map.of("sha512","invalid"));version.put("files",List.of(bad));var provider=new ModrinthProvider(transport(new ArrayList<>(),r->r.uri().getPath().endsWith("/loader")?List.of():r.uri().getPath().endsWith("/version")?List.of(version):Map.of("id","project","project_type","mod","title","title","description","text")),Clock.systemUTC());
  assertThrows(IOException.class,()->provider.files("project",new ModFileQuery("","",0,20),new CancellationToken()));
  var huge=new ModrinthProvider((r,l,t,s)->s.accept(ByteBuffer.wrap(new byte[8*1024*1024+1])),Clock.systemUTC());assertThrows(IOException.class,()->huge.search(new ModQuery("","","",ModQuery.Sort.RELEVANCE,0,20),new CancellationToken()));
 }
}
