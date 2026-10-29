package cn.dreamingfish.launcher.mods;

import cn.dreamingfish.launcher.downloads.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModDependencyPlanTest {
 @TempDir Path root;
 static final String GAME="1.20.6";
 static ModDependency required(String project,String version){return new ModDependency(ModDependency.Kind.REQUIRED,project,version,null);}
 static ModFile file(String project,String version,ModDependency... dependencies){
  var digest=new FileDigest(FileDigest.Algorithm.SHA256,"a".repeat(64));String name=project+"-"+version+".jar";
  return new ModFile(new ModFileRef(ModSource.MODRINTH,project,version,ModProtocol.fileKey(name,digest,"mod")),project,name,List.of(GAME),List.of("fabric"),"release",Instant.parse("2026-01-01T00:00:00Z"),10L,digest,List.of(dependencies),true,null,new DownloadSpec(RemoteService.MODRINTH_CDN,URI.create("https://cdn.modrinth.com/data/"+project+"/"+version+".jar"),10,100,digest,name));
 }
 static class Provider implements ModProvider {
  final Map<String,ModFile> versions=new LinkedHashMap<>();final List<String> resolved=new ArrayList<>();
  Provider(ModFile... files){for(var f:files)versions.put(f.ref().versionId(),f);}
  public ModSource source(){return ModSource.MODRINTH;}
  public SourceStatus status(CancellationToken token){return new SourceStatus(source(),"AVAILABLE",null,List.of("fabric"),null);}
  public ModProject project(String id,CancellationToken token){return new ModProject(source(),id,id,null,null,null);}
  public ModPage search(ModQuery q,CancellationToken token){throw new UnsupportedOperationException();}
  public ModFilesPage files(String project,ModFileQuery query,CancellationToken token){var list=versions.values().stream().filter(f->f.ref().projectId().equals(project)).toList();return new ModFilesPage(list,0,50,list.size(),list.size(),false);}
  public ModFile resolve(ModFileRef ref,CancellationToken token)throws IOException{token.check();resolved.add(ref.versionId());var f=versions.get(ref.versionId());if(f==null||!f.ref().equals(ref))throw new IOException("测试引用不存在");return f;}
  public ModFile dependency(ModDependency dep,ModFileQuery query,CancellationToken token)throws IOException{
   if(dep.versionId()!=null){var f=versions.get(dep.versionId());if(f!=null&&(dep.projectId()==null||dep.projectId().equals(f.ref().projectId())))return f;}
   else if(dep.projectId()!=null){var f=versions.values().stream().filter(v->v.ref().projectId().equals(dep.projectId())).findFirst();if(f.isPresent())return f.get();}
   throw new TaskFailure("DEPENDENCY_UNRESOLVED","测试依赖无法解析");
  }
 }
 ModDependencyResolver resolver(Provider provider){var store=new DownloadStore(root,(r,l,t,s)->{throw new AssertionError("计划不能下载文件");});return new ModDependencyResolver(new ModService(Map.of(ModSource.MODRINTH,provider),new ModLibrary(root,store,Clock.systemUTC())));}
 @Test void fixesRequiredGraphAndSkipsOptionalEmbeddedWithoutDownloading()throws Exception{
  var api=file("api","api1");var leaf=file("leaf","leaf1",required("api",null));
  var main=file("main","main1",required("leaf",null),required("api","api1"),new ModDependency(ModDependency.Kind.OPTIONAL,"optional",null,null),new ModDependency(ModDependency.Kind.EMBEDDED,"embedded",null,null));
  var provider=new Provider(main,leaf,api);var first=resolver(provider).plan(main.ref(),GAME,"fabric",new CancellationToken());
  assertEquals(Set.of("main1","leaf1","api1"),new HashSet<>(first.files().stream().map(f->f.ref().versionId()).toList()));
  assertEquals(first.fingerprint(),resolver(provider).plan(main.ref(),GAME,"fabric",new CancellationToken()).fingerprint());assertEquals(30,first.totalBytes());assertTrue(first.warnings().stream().anyMatch(s->s.contains("可选")));
 }
 @Test void resolvesVersionOnlyAndRejectsConflictingPins()throws Exception{
  var one=file("dep","one");var two=file("dep","two");var main=file("main","main",required(null,"one"));
  assertEquals(2,resolver(new Provider(main,one)).plan(main.ref(),GAME,"fabric",new CancellationToken()).files().size());
  var conflict=file("main","main",required("dep","one"),required("dep","two"));assertEquals("DEPENDENCY_CONFLICT",assertThrows(TaskFailure.class,()->resolver(new Provider(conflict,one,two)).plan(conflict.ref(),GAME,"fabric",new CancellationToken())).code());
 }
 @Test void rejectsCyclesUnresolvedAndKnownIncompatibility()throws Exception{
  var a=file("a","a",required("b",null));var b=file("b","b",required("a",null));assertEquals("DEPENDENCY_CYCLE",assertThrows(TaskFailure.class,()->resolver(new Provider(a,b)).plan(a.ref(),GAME,"fabric",new CancellationToken())).code());
  var missing=file("a","a",required(null,null));assertEquals("DEPENDENCY_UNRESOLVED",assertThrows(TaskFailure.class,()->resolver(new Provider(missing)).plan(missing.ref(),GAME,"fabric",new CancellationToken())).code());
  var conflict=file("a","a",required("b",null),new ModDependency(ModDependency.Kind.INCOMPATIBLE,"b",null,null));assertEquals("DEPENDENCY_CONFLICT",assertThrows(TaskFailure.class,()->resolver(new Provider(conflict,file("b","b"))).plan(conflict.ref(),GAME,"fabric",new CancellationToken())).code());
 }
 @Test void neverRelaxesGameOrLoaderAndPinsContentNames()throws Exception{
  var f=file("a","a");assertThrows(TaskFailure.class,()->resolver(new Provider(f)).plan(f.ref(),"1.21.1","fabric",new CancellationToken()));assertThrows(TaskFailure.class,()->resolver(new Provider(f)).plan(f.ref(),GAME,"quilt",new CancellationToken()));
  var bad=new ModFile(f.ref(),f.displayName(),"../escape.jar",f.gameVersions(),f.loaders(),f.releaseChannel(),f.publishedAt(),f.size(),f.sourceDigest(),f.dependencies(),true,null,f.downloadSpec());assertThrows(IOException.class,()->resolver(new Provider(bad)).plan(bad.ref(),GAME,"fabric",new CancellationToken()));
 }
 @Test void boundsDepthAndCancellation()throws Exception{
  var files=new ArrayList<ModFile>();for(int i=0;i<18;i++)files.add(file("p"+i,"v"+i,i==17?new ModDependency[]{}:new ModDependency[]{required("p"+(i+1),null)}));var provider=new Provider(files.toArray(ModFile[]::new));assertEquals("DEPENDENCY_LIMIT",assertThrows(TaskFailure.class,()->resolver(provider).plan(files.getFirst().ref(),GAME,"fabric",new CancellationToken())).code());var token=new CancellationToken();token.cancel();assertThrows(InterruptedIOException.class,()->resolver(provider).plan(files.getFirst().ref(),GAME,"fabric",token));
 }
 @Test void boundsWideGraphAndCaseInsensitiveFileCollisions()throws Exception{
  var all=new ArrayList<ModFile>();var deps=new ArrayList<ModDependency>();for(int i=0;i<64;i++){all.add(file("p"+i,"v"+i));deps.add(required("p"+i,null));}var main=file("main","main",deps.toArray(ModDependency[]::new));all.add(main);assertEquals("DEPENDENCY_LIMIT",assertThrows(TaskFailure.class,()->resolver(new Provider(all.toArray(ModFile[]::new))).plan(main.ref(),GAME,"fabric",new CancellationToken())).code());
  var a=file("A","x");var b=file("a","X");var rootFile=file("main","main",required("A",null),required("a",null));assertEquals("MOD_NAME_CONFLICT",assertThrows(TaskFailure.class,()->resolver(new Provider(rootFile,a,b)).plan(rootFile.ref(),GAME,"fabric",new CancellationToken())).code());
 }
}
