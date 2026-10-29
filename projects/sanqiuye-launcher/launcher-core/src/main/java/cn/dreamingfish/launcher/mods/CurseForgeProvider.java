package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Official CurseForge GET endpoints; distribution restrictions never trigger guessed URLs. */
public final class CurseForgeProvider implements ModProvider {
 private static final String BASE="https://api.curseforge.com/v1";
 private static final Map<String,Integer> LOADERS=Map.of("forge",1,"cauldron",2,"liteloader",3,"fabric",4,"quilt",5,"neoforge",6);
 private final RemoteClient remote;private final Supplier<String> apiKey;private final Clock clock;
 private List<cn.dreamingfish.launcher.content.ContentCategory> cachedCategories;private Instant categoriesAt;
 @Override public synchronized List<cn.dreamingfish.launcher.content.ContentCategory> categories(CancellationToken token)throws IOException{
  key();token.check();if(categoriesAt!=null&&clock.instant().isBefore(categoriesAt.plusSeconds(86400)))return cachedCategories;
  var root=get("/categories",Map.of("gameId","432","classId","6"),token).path("data");
  try{if(!root.isArray()||root.size()>2000)throw new IllegalArgumentException();var list=new ArrayList<cn.dreamingfish.launcher.content.ContentCategory>();for(var item:root){if(item.hasNonNull("classId")&&item.path("classId").asInt()!=6)continue;list.add(new cn.dreamingfish.launcher.content.ContentCategory(numericId(item,"id"),LauncherJson.text(item,"name",256),item.path("parentCategoryId").asText("6")));}cachedCategories=List.copyOf(list);categoriesAt=clock.instant();return cachedCategories;}catch(RuntimeException invalid){throw ModProtocol.invalid(invalid);}
 }
 private record ProjectData(ModProject project,Boolean distribution,boolean available){}
 public CurseForgeProvider(RemoteClient remote,Supplier<String> apiKey,Clock clock){this.remote=remote;this.apiKey=apiKey;this.clock=clock;}
 @Override public ModSource source(){return ModSource.CURSEFORGE;}
 @Override public String toString(){return "CurseForgeProvider";}
 private String key()throws TaskFailure{String value=apiKey.get();if(value==null||value.isBlank())throw new TaskFailure("SOURCE_UNCONFIGURED","需要配置合法的 CurseForge 官方应用密钥");return value;}
 @Override public SourceStatus status(CancellationToken token)throws IOException{token.check();String value=apiKey.get();return value==null||value.isBlank()?new SourceStatus(source(),"UNCONFIGURED","需要配置官方应用密钥",List.of(),null):new SourceStatus(source(),"AVAILABLE",null,LOADERS.keySet().stream().sorted().toList(),"已配置应用密钥，实际授权在查询时校验");}
 private JsonNode get(String path,Map<String,String> query,CancellationToken token)throws IOException{
  String key=key();byte[] bytes;
  try{bytes=remote.bytes(new RemoteRequest(RemoteService.CURSEFORGE_API,ModProtocol.uri(BASE+path,query),Map.of("Accept","application/json","x-api-key",key)),4*1024*1024,token);}
  catch(RemoteHttpException error){if(error.status()==401||error.status()==403)throw new TaskFailure("AUTH_REQUIRED","CurseForge 应用密钥未获授权");throw error;}
  catch(IOException error){token.check();throw new TaskFailure("NETWORK_ERROR","CurseForge 官方查询未完成，请稍后重试");}
  return LauncherJson.read(bytes,4*1024*1024);
 }
 private static String id(String value){if(value==null||!value.matches("[1-9][0-9]{0,9}")||Long.parseLong(value)>Integer.MAX_VALUE)throw new IllegalArgumentException("CurseForge ID 无效");return value;}
 private static String numericId(JsonNode n,String key){return id(Long.toString(ModProtocol.requiredNumber(n,key)));}
 private static void filter(String version,String loader,int offset,int limit){if(!loader.isEmpty()&&(!LOADERS.containsKey(loader)||version.isEmpty()))throw new IllegalArgumentException("CurseForge 加载器筛选需要明确游戏版本");if((long)offset+limit>10000)throw new IllegalArgumentException("CurseForge 仅可访问前 10000 项结果");}
 private static Map<String,String> parameters(String version,String loader,int offset,int limit){filter(version,loader,offset,limit);var q=new LinkedHashMap<String,String>();q.put("index",Integer.toString(offset));q.put("pageSize",Integer.toString(limit));if(!version.isEmpty())q.put("gameVersion",version);if(!loader.isEmpty())q.put("modLoaderType",Integer.toString(LOADERS.get(loader)));return q;}
 @Override public ModPage search(ModQuery query,CancellationToken token)throws IOException{
  key();var q=parameters(query.gameVersion(),query.loader(),query.offset(),query.limit());q.put("gameId","432");q.put("classId","6");if(!query.category().isEmpty()){id(query.category());if(categories(token).stream().noneMatch(c->c.id().equals(query.category())))throw new TaskFailure("UNKNOWN_CATEGORY","请选择此来源提供的模组分类");q.put("categoryId",query.category());}q.put("searchFilter",query.query());if(query.sort()!=ModQuery.Sort.RELEVANCE){q.put("sortField",query.sort()==ModQuery.Sort.DOWNLOADS?"6":"3");q.put("sortOrder","desc");}
  var n=get("/mods/search",q,token);try{var data=n.path("data");if(!data.isArray()||data.size()>query.limit())throw new IllegalArgumentException("搜索数组超限");var projects=new ArrayList<ModProject>();for(var item:data)projects.add(projectData(item).project());long total=ModProtocol.requiredNumber(n.path("pagination"),"totalCount");return new ModPage(projects,query.offset(),query.limit(),total,Math.min(total,10000),total>10000);}catch(RuntimeException invalid){throw ModProtocol.invalid(invalid);}
 }
 private ProjectData loadProject(String projectId,CancellationToken token)throws IOException{
  id(projectId);var n=get("/mods/"+projectId,Map.of(),token).path("data");try{var result=projectData(n);if(!result.project().projectId().equals(projectId))throw new IllegalArgumentException("项目 ID 不匹配");return result;}catch(RuntimeException invalid){throw ModProtocol.invalid(invalid);}
 }
 @Override public ModProject project(String projectId,CancellationToken token)throws IOException{key();return loadProject(projectId,token).project();}
 private ProjectData projectData(JsonNode n){
  if(ModProtocol.requiredNumber(n,"gameId")!=432||ModProtocol.requiredNumber(n,"classId")!=6)throw new IllegalArgumentException("项目不属于 Minecraft 模组");String id=numericId(n,"id");var authors=new ArrayList<String>();if(n.hasNonNull("authors")){if(!n.path("authors").isArray()||n.path("authors").size()>1000)throw new IllegalArgumentException("作者数组无效");for(var author:n.path("authors")){String name=ModProtocol.text(author,"name",256);if(name!=null&&!name.isBlank())authors.add(name);}}
  Boolean distribution=null;if(n.hasNonNull("allowModDistribution")){if(!n.path("allowModDistribution").isBoolean())throw new IllegalArgumentException("分发状态无效");distribution=n.path("allowModDistribution").booleanValue();}
  return new ProjectData(new ModProject(source(),id,ModProtocol.text(n,"name",512),authors.isEmpty()?null:String.join(", ",authors),ModProtocol.text(n,"summary",8192),ModProtocol.number(n,"downloadCount"),cn.dreamingfish.launcher.content.ProjectPresentation.curseforge(n)),distribution,n.path("isAvailable").isBoolean()&&n.path("isAvailable").booleanValue());
 }
 @Override public ModFilesPage files(String projectId,ModFileQuery query,CancellationToken token)throws IOException{
  key();var q=parameters(query.gameVersion(),query.loader(),query.offset(),query.limit());var project=loadProject(projectId,token);var n=get("/mods/"+project.project().projectId()+"/files",q,token);
  try{var data=n.path("data");if(!data.isArray()||data.size()>query.limit())throw new IllegalArgumentException("文件数组超限");var files=new ArrayList<ModFile>();for(var item:data){var file=file(item,project);if(ModProtocol.compatible(file,query.gameVersion(),query.loader()))files.add(file);}long total=ModProtocol.requiredNumber(n.path("pagination"),"totalCount");return new ModFilesPage(files,query.offset(),query.limit(),total,Math.min(total,10000),total>10000);}catch(RuntimeException invalid){throw ModProtocol.invalid(invalid);}
 }
 @Override public ModFile resolve(ModFileRef ref,CancellationToken token)throws IOException{
  key();if(ref.source()!=source())throw new TaskFailure("FILE_CHANGED","平台来源不匹配");id(ref.versionId());var project=loadProject(ref.projectId(),token);var n=get("/mods/"+ref.projectId()+"/files/"+ref.versionId(),Map.of(),token).path("data");try{var file=file(n,project);if(!file.ref().equals(ref))throw new TaskFailure("FILE_CHANGED","CurseForge 文件已变化，请重新选择");return file;}catch(RuntimeException invalid){throw ModProtocol.invalid(invalid);}
 }
 private ModFile file(JsonNode n,ProjectData project){
  String projectId=project.project().projectId(),versionId=numericId(n,"id");if(ModProtocol.requiredNumber(n,"gameId")!=432||!projectId.equals(numericId(n,"modId")))throw new IllegalArgumentException("文件不属于此项目");String name=LauncherJson.text(n,"fileName",256);Long size=ModProtocol.number(n,"fileLength");var published=ModProtocol.instant(n,"fileDate");String url=ModProtocol.text(n,"downloadUrl",4096);FileDigest hash=null;
  if(n.hasNonNull("hashes")){if(!n.path("hashes").isArray()||n.path("hashes").size()>100)throw new IllegalArgumentException("摘要数组无效");for(var value:n.path("hashes"))if(value.path("algo").isInt()&&value.path("algo").intValue()==1){var parsed=new FileDigest(FileDigest.Algorithm.SHA1,LauncherJson.text(value,"value",40));if(hash!=null&&!hash.equals(parsed))throw new IllegalArgumentException("来源摘要冲突");hash=parsed;}}
  var versions=new ArrayList<String>();var loaders=new TreeSet<String>();if(n.hasNonNull("gameVersions"))for(String tag:ModProtocol.strings(n.path("gameVersions"),10000)){String normalized=tag.toLowerCase(Locale.ROOT);if(LOADERS.containsKey(normalized))loaders.add(normalized);else versions.add(tag);}
  var dependencies=new ArrayList<ModDependency>();if(n.hasNonNull("dependencies")){if(!n.path("dependencies").isArray()||n.path("dependencies").size()>10000)throw new IllegalArgumentException("依赖数组无效");for(var d:n.path("dependencies")){ModDependency.Kind kind=switch(d.path("relationType").asInt(-1)){case 1->ModDependency.Kind.EMBEDDED;case 2->ModDependency.Kind.OPTIONAL;case 3->ModDependency.Kind.REQUIRED;case 4->ModDependency.Kind.TOOL;case 5->ModDependency.Kind.INCOMPATIBLE;case 6->ModDependency.Kind.INCLUDE;default->ModDependency.Kind.UNKNOWN;};Long id=ModProtocol.number(d,"modId");dependencies.add(new ModDependency(kind,id==null||id==0?null:Long.toString(id),null,null));}}
  String reason=null;DownloadSpec download=null;int status=n.path("fileStatus").isInt()?n.path("fileStatus").intValue():-1;String channel=switch(n.path("releaseType").asInt(-1)){case 1->"release";case 2->"beta";case 3->"alpha";default->null;};Instant early=ModProtocol.instant(n,"earlyAccessEndDate");
  if(!project.available()||!n.path("isAvailable").isBoolean()||!n.path("isAvailable").booleanValue())reason="平台标记文件或项目不可用";
  else if(Boolean.FALSE.equals(project.distribution()))reason="作者未允许第三方分发";
  else if(!Set.of(4,8,10,12).contains(status)||channel==null||published!=null&&published.isAfter(clock.instant()))reason="文件尚未公开发布或状态不可下载";
  else if(n.path("isEarlyAccessContent").asBoolean(false)&&(early==null||early.isAfter(clock.instant())))reason="文件仍处于提前访问阶段";
  else if(!ModProtocol.ordinary(name)||n.path("isServerPack").asBoolean(false))reason="附件或非普通模组文件";
  else if(versions.isEmpty()||loaders.isEmpty())reason="缺少明确兼容标签";
  else if(hash==null||size==null||url==null||url.isBlank())reason="缺少 SHA-1、长度或官方下载地址";
  else if(size>2L*1024*1024*1024)reason="文件超过 2 GiB 上限";
  else try{download=new DownloadSpec(RemoteService.CURSEFORGE_CDN,URI.create(url),size,2L*1024*1024*1024,hash,name);}catch(IllegalArgumentException invalid){reason="官方下载地址不符合来源策略";}
  return new ModFile(new ModFileRef(source(),projectId,versionId,ModProtocol.fileKey(name,hash,"mod")),ModProtocol.text(n,"displayName",512),name,versions,new ArrayList<>(loaders),channel,published,size,hash,dependencies,reason==null,reason,download);
 }
}
