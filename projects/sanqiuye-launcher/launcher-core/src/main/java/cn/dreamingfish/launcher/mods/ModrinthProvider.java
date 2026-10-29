package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.URI;
import java.time.*;
import java.util.*;

/** Independently implemented Modrinth v2 GET adapter. No account or mutation endpoints. */
public final class ModrinthProvider implements ModProvider {
 private static final String BASE="https://api.modrinth.com/v2";
 private final RemoteClient remote;private final Clock clock;private List<String> loaders;private Instant tagsAt;private final cn.dreamingfish.launcher.content.ModrinthCategories categories;
 public ModrinthProvider(RemoteClient remote,Clock clock){this.remote=remote;this.clock=clock;this.categories=new cn.dreamingfish.launcher.content.ModrinthCategories(remote,clock,"mod");}
 @Override public java.util.List<cn.dreamingfish.launcher.content.ContentCategory> categories(CancellationToken token)throws IOException{return categories.list(token);}
 @Override public ModSource source(){return ModSource.MODRINTH;}
 private JsonNode get(String path,Map<String,String> query,int limit,CancellationToken token)throws IOException{
  return LauncherJson.readValue(remote.bytes(new RemoteRequest(RemoteService.MODRINTH_API,ModProtocol.uri(BASE+path,query),Map.of("Accept","application/json")),limit,token),limit);
 }
 @Override public synchronized SourceStatus status(CancellationToken token)throws IOException{
  token.check();String warning=null;
  if(tagsAt==null||!clock.instant().isBefore(tagsAt.plusSeconds(86400)))try{
   var tags=get("/tag/loader",Map.of(),1024*1024,token);if(!tags.isArray()||tags.size()>1000)throw new IOException("加载器标签格式无效");var selected=new TreeSet<String>();
   for(var tag:tags){if(ModProtocol.strings(tag.path("supported_project_types"),100).contains("mod")){String name=LauncherJson.text(tag,"name",32);if(!name.matches("[a-z0-9_-]{1,32}"))throw new IOException("加载器标签无效");selected.add(name);}}
   loaders=List.copyOf(selected);tagsAt=clock.instant();
  }catch(IOException|RuntimeException unavailable){token.check();warning=loaders==null?"加载器标签暂不可用，可查询全部加载器":"加载器标签刷新失败，使用已有标签";}
  return new SourceStatus(source(),"AVAILABLE",null,loaders==null?List.of():loaders,warning);
 }
 private void loader(String value,CancellationToken token)throws IOException{if(!value.isEmpty()&&!status(token).loaders().contains(value))throw new TaskFailure("UNKNOWN_LOADER","请选择此来源提供的加载器标签");}
 @Override public ModPage search(ModQuery query,CancellationToken token)throws IOException{
  categories.require(query.category(),token);loader(query.loader(),token);var facets=new ArrayList<List<String>>();facets.add(List.of("project_type:mod"));if(!query.category().isEmpty())facets.add(List.of("categories:"+query.category()));if(!query.gameVersion().isEmpty())facets.add(List.of("versions:"+query.gameVersion()));if(!query.loader().isEmpty())facets.add(List.of("categories:"+query.loader()));
  var parameters=new LinkedHashMap<String,String>();parameters.put("query",query.query());parameters.put("facets",ModProtocol.json(facets));parameters.put("index",query.sort().name().toLowerCase(Locale.ROOT));parameters.put("offset",Integer.toString(query.offset()));parameters.put("limit",Integer.toString(query.limit()));
  var n=get("/search",parameters,4*1024*1024,token);
  try{if(!n.path("hits").isArray()||n.path("hits").size()>query.limit())throw new IllegalArgumentException("搜索结果数量无效");var items=new ArrayList<ModProject>();for(var hit:n.path("hits")){if(!"mod".equals(hit.path("project_type").asText()))throw new IllegalArgumentException("搜索结果不是模组");items.add(new ModProject(source(),ModProtocol.id(LauncherJson.text(hit,"project_id",128)),ModProtocol.text(hit,"title",512),ModProtocol.text(hit,"author",256),ModProtocol.text(hit,"description",8192),ModProtocol.number(hit,"downloads"),cn.dreamingfish.launcher.content.ProjectPresentation.modrinth(hit)));}long total=ModProtocol.requiredNumber(n,"total_hits");return new ModPage(items,query.offset(),query.limit(),total,total,false);}catch(RuntimeException invalid){throw ModProtocol.invalid(invalid);}
 }
 @Override public ModProject project(String projectId,CancellationToken token)throws IOException{
  ModProtocol.id(projectId);var n=get("/project/"+projectId,Map.of(),4*1024*1024,token);
  try{if(!"mod".equals(n.path("project_type").asText()))throw new IllegalArgumentException("项目不是模组");return new ModProject(source(),ModProtocol.id(LauncherJson.text(n,"id",128)),ModProtocol.text(n,"title",512),ModProtocol.text(n,"author",256),ModProtocol.text(n,"description",8192),ModProtocol.number(n,"downloads"),cn.dreamingfish.launcher.content.ProjectPresentation.modrinth(n));}catch(RuntimeException invalid){throw ModProtocol.invalid(invalid);}
 }
 @Override public ModFilesPage files(String projectId,ModFileQuery query,CancellationToken token)throws IOException{
  var project=project(projectId,token);loader(query.loader(),token);var parameters=new LinkedHashMap<String,String>();parameters.put("include_changelog","false");if(!query.gameVersion().isEmpty())parameters.put("game_versions",ModProtocol.json(List.of(query.gameVersion())));if(!query.loader().isEmpty())parameters.put("loaders",ModProtocol.json(List.of(query.loader())));
  var n=get("/project/"+project.projectId()+"/version",parameters,16*1024*1024,token);
  try{if(!n.isArray()||n.size()>20000)throw new IllegalArgumentException("版本数组超限");var all=new ArrayList<ModFile>();for(var version:n){for(var file:version(version,project.projectId()))if(ModProtocol.compatible(file,query.gameVersion(),query.loader()))all.add(file);if(all.size()>100000)throw new IllegalArgumentException("文件数组超限");}all.sort(Comparator.comparing(ModFile::publishedAt,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(f->f.ref().versionId()).thenComparing(f->f.downloadable()?0:1).thenComparing(ModFile::fileName));return new ModFilesPage(all.stream().skip(query.offset()).limit(query.limit()).toList(),query.offset(),query.limit(),all.size(),all.size(),false);}catch(RuntimeException invalid){throw ModProtocol.invalid(invalid);}
 }
 @Override public ModFile resolve(ModFileRef ref,CancellationToken token)throws IOException{
  if(ref.source()!=source())throw new TaskFailure("FILE_CHANGED","模组来源与引用不匹配");var n=get("/version/"+ref.versionId(),Map.of(),4*1024*1024,token);
  try{if(!ref.versionId().equals(n.path("id").asText()))throw new IllegalArgumentException("版本 ID 不匹配");return version(n,ref.projectId()).stream().filter(f->f.ref().equals(ref)).findFirst().orElseThrow(()->new TaskFailure("FILE_CHANGED","平台文件已变化，请重新选择"));}catch(RuntimeException invalid){throw ModProtocol.invalid(invalid);}
 }
 @Override public ModFile dependency(ModDependency dependency,ModFileQuery query,CancellationToken token)throws IOException{
  if(dependency.versionId()==null)return ModProvider.super.dependency(dependency,query,token);
  var n=get("/version/"+ModProtocol.id(dependency.versionId()),Map.of(),4*1024*1024,token);
  try{
   if(!dependency.versionId().equals(LauncherJson.text(n,"id",128)))throw new IOException("依赖版本ID与请求不匹配");
   String project=ModProtocol.id(LauncherJson.text(n,"project_id",128));
   if(dependency.projectId()!=null&&!dependency.projectId().equals(project))throw new IOException("依赖项目与固定版本不匹配");
   return version(n,project).stream().filter(ModFile::downloadable).findFirst().orElseThrow(()->new TaskFailure("DEPENDENCY_UNRESOLVED","固定依赖版本没有可下载的主模组文件"));
  }catch(RuntimeException invalid){throw ModProtocol.invalid(invalid);}
 }
 private List<ModFile> version(JsonNode n,String projectId)throws IOException{
  if(!projectId.equals(LauncherJson.text(n,"project_id",128)))throw new IOException("项目与版本引用不匹配");String versionId=ModProtocol.id(LauncherJson.text(n,"id",128));var versions=ModProtocol.strings(n.path("game_versions"),10000);var tags=ModProtocol.strings(n.path("loaders"),128);for(String tag:tags)if(!tag.matches("[a-z0-9_-]{1,32}"))throw new IllegalArgumentException("版本加载器标签无效");
  var published=ModProtocol.instant(n,"date_published");String state=ModProtocol.text(n,"status",32);boolean released=(state==null||Set.of("listed","archived","unlisted").contains(state))&&(published==null||!published.isAfter(clock.instant()));
  var dependencies=new ArrayList<ModDependency>();if(n.has("dependencies")){var array=n.path("dependencies");if(!array.isArray()||array.size()>10000)throw new IllegalArgumentException("依赖数组无效");for(var d:array){ModDependency.Kind kind=switch(d.path("dependency_type").asText()){case "required"->ModDependency.Kind.REQUIRED;case "optional"->ModDependency.Kind.OPTIONAL;case "incompatible"->ModDependency.Kind.INCOMPATIBLE;case "embedded"->ModDependency.Kind.EMBEDDED;default->ModDependency.Kind.UNKNOWN;};dependencies.add(new ModDependency(kind,ModProtocol.text(d,"project_id",128),ModProtocol.text(d,"version_id",128),ModProtocol.text(d,"file_name",256)));}}
  var files=n.path("files");if(!files.isArray()||files.size()>1000)throw new IllegalArgumentException("版本文件数组无效");JsonNode selected=null;for(var f:files){String name=LauncherJson.text(f,"filename",256);if(ModProtocol.ordinary(name)&&(!f.hasNonNull("file_type")||"mod".equals(f.path("file_type").asText()))){if(selected==null||f.path("primary").asBoolean(false))selected=f;if(f.path("primary").asBoolean(false))break;}}
  var result=new ArrayList<ModFile>();for(var f:files){String name=LauncherJson.text(f,"filename",256),type=ModProtocol.text(f,"file_type",64);var hashes=f.path("hashes");FileDigest digest=null;if(hashes.hasNonNull("sha512"))digest=new FileDigest(FileDigest.Algorithm.SHA512,LauncherJson.text(hashes,"sha512",128));else if(hashes.hasNonNull("sha1"))digest=new FileDigest(FileDigest.Algorithm.SHA1,LauncherJson.text(hashes,"sha1",40));Long size=ModProtocol.number(f,"size");String url=ModProtocol.text(f,"url",4096),reason=null;DownloadSpec spec=null;
   if(f!=selected)reason="附件或非普通主模组文件";else if(!released)reason="该版本尚未公开发布";else if(versions.isEmpty()||tags.isEmpty())reason="缺少明确兼容标签";else if(digest==null||size==null||url==null)reason="缺少来源摘要、长度或官方下载地址";else if(size>2L*1024*1024*1024)reason="文件超过 2 GiB 上限";else try{spec=new DownloadSpec(RemoteService.MODRINTH_CDN,URI.create(url),size,2L*1024*1024*1024,digest,name);}catch(IllegalArgumentException invalid){reason="官方下载地址不符合来源策略";}
   result.add(new ModFile(new ModFileRef(source(),projectId,versionId,ModProtocol.fileKey(name,digest,type==null?"mod":type)),ModProtocol.text(n,"name",512),name,versions,tags,ModProtocol.text(n,"version_type",64),published,size,digest,dependencies,reason==null,reason,spec));
  }return result;
 }
}
