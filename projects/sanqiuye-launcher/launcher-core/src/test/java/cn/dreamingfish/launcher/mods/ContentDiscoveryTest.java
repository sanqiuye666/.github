package cn.dreamingfish.launcher.mods;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.packs.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContentDiscoveryTest {
 @Test void malformedOptionalPresentationNeverBlocksValidFileQueries()throws Exception{
  for(String field:List.of("icon_url","body","gallery","categories","game_versions")){
   var project=new HashMap<String,Object>(Map.of("id","project","project_type","mod","title","有效项目","description","合法摘要"));
   project.put(field,switch(field){case "icon_url"->"https://cdn.modrinth.com/"+"a".repeat(5000);case "body"->"a".repeat(65537);case "gallery"->Collections.nCopies(257,Map.of("url","https://cdn.modrinth.com/image.png"));case "categories"->List.of("bad category",42);default->List.of(Map.of("invalid",true));});
   var provider=new ModrinthProvider(ModrinthProviderTest.transport(new ArrayList<>(),r->r.uri().getPath().endsWith("/version")?List.of(ModrinthProviderTest.version()):project),Clock.systemUTC());
   if(field.equals("body"))assertTrue(provider.project("project",new CancellationToken()).presentation().description().isEmpty(),"超出展示预算的正文应降级");
   var files=provider.files("project",new ModFileQuery("","",0,20),new CancellationToken());assertTrue(files.items().stream().anyMatch(ModFile::downloadable),field+"不应影响合法版本文件");
  }
  var oversized=new ModrinthProvider(ModrinthProviderTest.transport(new ArrayList<>(),r->Map.of("id","project","project_type","mod","title","项目","description","摘要","body","a".repeat(262145))),Clock.systemUTC());
  assertThrows(java.io.IOException.class,()->oversized.project("project",new CancellationToken()),"通用JSON字符串上限继续严格拒绝，不为展示放宽协议");
 }
 @Test void typedCategoriesBecomeSourceFacetsAndKeepImagesAndIntroduction()throws Exception{
  var calls=new ArrayList<RemoteRequest>();
  var project=new HashMap<String,Object>();project.putAll(Map.of("id","project","project_id","project","project_type","mod","title","地图工具","description","简短介绍","body","## 功能\n\n真实项目介绍","icon_url","https://cdn.modrinth.com/data/project/icon.png","categories",List.of("utility","fabric"),"game_versions",List.of("1.21.1"),"loaders",List.of("fabric")));
  project.put("gallery",List.of(Map.of("url","https://cdn.modrinth.com/data/project/cover.png")));project.put("updated","2026-10-20T00:00:00Z");
  RemoteClient remote=(r,l,t,s)->{calls.add(r);Object value=r.uri().getPath().endsWith("/category")?List.of(Map.of("name","utility","project_type","mod","header","categories"),Map.of("name","magic","project_type","modpack","header","categories")):r.uri().getPath().endsWith("/search")?Map.of("hits",List.of(project),"total_hits",1):project;s.accept(ByteBuffer.wrap(LauncherJson.write(value)));};
  var provider=new ModrinthProvider(remote,Clock.systemUTC());
  assertEquals(List.of("utility"),provider.categories(new CancellationToken()).stream().map(c->c.id()).toList());
  var query=new ModQuery("地图","1.21.1","",ModQuery.Sort.DOWNLOADS,20,20,"utility");
  var page=provider.search(query,new CancellationToken());
  assertTrue(ModrinthProviderTest.query(calls.getLast().uri()).get("facets").contains("categories:utility"));
  assertEquals("https://cdn.modrinth.com/data/project/icon.png",page.items().getFirst().presentation().iconUrl());
  assertEquals(List.of("utility"),page.items().getFirst().presentation().categories().stream().map(c->c.id()).toList());
  assertEquals("## 功能\n\n真实项目介绍",provider.project("project",new CancellationToken()).presentation().description());
  assertEquals(1,provider.project("project",new CancellationToken()).presentation().gallery().size());
  var bad=assertThrows(TaskFailure.class,()->provider.search(new ModQuery("","","",ModQuery.Sort.RELEVANCE,0,20,"magic"),new CancellationToken()));assertEquals("UNKNOWN_CATEGORY",bad.code());
 }
 @Test void packCategoryUsesModpackTagsAndUnsupportedSourcesRejectFilters()throws Exception{
  var calls=new ArrayList<RemoteRequest>();
  RemoteClient remote=(r,l,t,s)->{calls.add(r);Object value=r.uri().getPath().endsWith("/category")?List.of(Map.of("name","technology","project_type","modpack","header","categories"),Map.of("name","utility","project_type","mod","header","categories")):Map.of("hits",List.of(),"total_hits",0);s.accept(ByteBuffer.wrap(LauncherJson.write(value)));};
  var provider=new ModrinthPackProvider(remote,Clock.systemUTC());assertEquals("technology",provider.categories(new CancellationToken()).getFirst().id());
  provider.search(new PackQuery("",0,20,"technology","1.20.6","fabric","DOWNLOADS"),new CancellationToken());var params=ModrinthProviderTest.query(calls.getLast().uri());assertEquals("downloads",params.get("index"));assertTrue(params.get("facets").contains("categories:technology"));assertTrue(params.get("facets").contains("versions:1.20.6"));
  assertThrows(IllegalArgumentException.class,()->new AtLauncherPackProvider(remote,Clock.systemUTC()).search(new PackQuery("",0,20,"technology","","","RELEVANCE"),new CancellationToken()));
  assertThrows(IllegalArgumentException.class,()->new FtbPackProvider(remote,Clock.systemUTC()).search(new PackQuery("",0,20,"technology","","","RELEVANCE"),new CancellationToken()));
 }
 @Test void curseforgeCategoriesUseNumericIdAndRealMetadata()throws Exception{
  var calls=new ArrayList<RemoteRequest>();var data=new HashMap<>(CurseForgeProviderTest.project());data.put("logo",Map.of("url","https://media.forgecdn.net/avatars/1/icon.png"));data.put("categories",List.of(Map.of("id",423,"name","Map and Information")));
  var provider=new CurseForgeProvider(ModrinthProviderTest.transport(calls,r->r.uri().getPath().endsWith("/categories")?Map.of("data",List.of(Map.of("id",423,"name","Map and Information","classId",6,"parentCategoryId",6))):Map.of("data",List.of(data),"pagination",Map.of("totalCount",1))),()->"test-only",Clock.systemUTC());
  assertEquals("423",provider.categories(new CancellationToken()).getFirst().id());var result=provider.search(new ModQuery("","","",ModQuery.Sort.RELEVANCE,0,20,"423"),new CancellationToken());assertEquals("423",ModrinthProviderTest.query(calls.getLast().uri()).get("categoryId"));assertNotNull(result.items().getFirst().presentation().iconUrl());
 }
 @Test void optionalMaliciousImagesAreOmittedAndMetadataIsBounded(){
  assertNull(cn.dreamingfish.launcher.content.ProjectPresentation.image("http://127.0.0.1/a.png"));
  assertNull(cn.dreamingfish.launcher.content.ProjectPresentation.image("https://cdn.modrinth.com.evil.invalid/a.png"));
  assertNull(cn.dreamingfish.launcher.content.ProjectPresentation.image("https://user:secret@cdn.modrinth.com/a.png"));
  assertNull(cn.dreamingfish.launcher.content.ProjectPresentation.image("file:///C:/secret"));
  assertThrows(IllegalArgumentException.class,()->new ModQuery("","","",ModQuery.Sort.RELEVANCE,0,20,"utility\"],[\"project_type:modpack"));
 }
 @Test void ftbUsesItsActualArtAndTagsAndAtLauncherDoesNotGuessAnImage()throws Exception{
  RemoteClient remote=(r,l,t,s)->{boolean ftb=r.service()==RemoteService.FTB_API;Object value=ftb?Map.ofEntries(Map.entry("status","success"),Map.entry("id",125),Map.entry("name","FTB包"),Map.entry("synopsis","简短说明"),Map.entry("description","完整介绍"),Map.entry("private",false),Map.entry("versions",List.of()),Map.entry("art",List.of(Map.of("type","square","url","https://cdn.feed-the-beast.com/blob/icon.png"))),Map.entry("tags",List.of(Map.of("id",12,"name","Magic")))):Map.of("error",false,"code",200,"data",Map.of("safeName","TestPack","name","ATL包","type","public","description","来源介绍","versions",List.of()));s.accept(ByteBuffer.wrap(LauncherJson.write(value)));};
  var ftb=new FtbPackProvider(remote,Clock.systemUTC()).details("125",new CancellationToken()).project();assertEquals("完整介绍",ftb.presentation().description());assertEquals("https://cdn.feed-the-beast.com/blob/icon.png",ftb.presentation().iconUrl());assertEquals("Magic",ftb.presentation().categories().getFirst().name());
  var atl=new AtLauncherPackProvider(remote,Clock.systemUTC()).details("TestPack",new CancellationToken()).project();assertNull(atl.presentation().iconUrl());assertEquals("来源介绍",atl.presentation().description());
 }
 @Test void categoryFailuresDoNotPoisonCacheOrHideNewOfficialTags()throws Exception{
  var count=new java.util.concurrent.atomic.AtomicInteger();RemoteClient remote=(r,l,t,s)->{if(count.getAndIncrement()==0)throw new java.io.IOException("暂时断网");s.accept(ByteBuffer.wrap(LauncherJson.write(List.of(Map.of("name","future-category","project_type","mod","header","categories")))));};
  var provider=new ModrinthProvider(remote,Clock.systemUTC());assertThrows(java.io.IOException.class,()->provider.categories(new CancellationToken()));assertEquals("future-category",provider.categories(new CancellationToken()).getFirst().id());assertEquals(1,provider.categories(new CancellationToken()).size());assertEquals(2,count.get());
 }
}
