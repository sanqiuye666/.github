package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.*;
import java.io.*;
import java.util.*;
public final class ModService {
 private final Map<ModSource,ModProvider> providers;private final ModLibrary library;
 public ModService(Map<ModSource,ModProvider> providers,ModLibrary library){this.providers=Map.copyOf(providers);this.library=library;providers.forEach((source,provider)->{if(provider.source()!=source)throw new IllegalArgumentException("来源与适配器不匹配");});}
 private ModProvider provider(ModSource source)throws TaskFailure{var provider=providers.get(source);if(provider==null)throw new TaskFailure("SOURCE_UNCONFIGURED","模组来源尚未配置");return provider;}
 public List<SourceStatus> statuses(CancellationToken token)throws IOException{var result=new ArrayList<SourceStatus>();for(var source:ModSource.values()){token.check();try{result.add(provider(source).status(token));}catch(IOException error){token.check();result.add(new SourceStatus(source,"UNAVAILABLE","来源暂不可用",List.of(),null));}}return result;}
 public java.util.List<cn.dreamingfish.launcher.content.ContentCategory> categories(ModSource source,CancellationToken token)throws IOException{return provider(source).categories(token);}
 public ModPage search(ModSource source,ModQuery query,CancellationToken token)throws IOException{return provider(source).search(query,token);}
 public ModProject project(ModSource source,String projectId,CancellationToken token)throws IOException{return provider(source).project(projectId,token);}
 public ModFilesPage files(ModSource source,String projectId,ModFileQuery query,CancellationToken token)throws IOException{return provider(source).files(projectId,query,token);}
 public ModFile installFile(ModFileRef ref,String gameVersion,String loader,CancellationToken token)throws IOException{return resolve(ref,gameVersion,loader,token);}
 public ModFile dependency(ModSource source,ModDependency dependency,String gameVersion,String loader,CancellationToken token)throws IOException{
  var file=provider(source).dependency(dependency,new ModFileQuery(gameVersion,loader,0,50),token);
  if(file.ref().source()!=source||dependency.projectId()!=null&&!dependency.projectId().equals(file.ref().projectId())||dependency.versionId()!=null&&!dependency.versionId().equals(file.ref().versionId()))throw new TaskFailure("DEPENDENCY_CONFLICT","必需依赖的项目或版本与固定要求不同");
  return resolve(file.ref(),gameVersion,loader,token);
 }
 public TaskRequest prepareDownload(ModFileRef ref,String gameVersion,String loader,CancellationToken token)throws IOException{
  var query=new ModFileQuery(gameVersion,loader,0,20);var file=resolve(ref,query.gameVersion(),query.loader(),token);var inputs=new LinkedHashMap<String,String>();inputs.put("source",ref.source().name());inputs.put("projectId",ref.projectId());inputs.put("versionId",ref.versionId());inputs.put("fileKey",ref.fileKey());inputs.put("gameVersion",query.gameVersion());inputs.put("loader",query.loader());inputs.put("digestAlgorithm",file.sourceDigest().algorithm().name());inputs.put("digest",file.sourceDigest().hex());inputs.put("size",Long.toString(file.size()));return new TaskRequest(TaskKind.MOD_DOWNLOAD,inputs);
 }
 private ModFile resolve(ModFileRef ref,String gameVersion,String loader,CancellationToken token)throws IOException{
  if(gameVersion==null||gameVersion.isBlank()||loader==null||loader.isBlank())throw new TaskFailure("INCOMPATIBLE","请先明确选择游戏版本和加载器");var provider=provider(ref.source());var status=provider.status(token);if(!status.state().equals("AVAILABLE"))throw new TaskFailure("SOURCE_UNCONFIGURED",status.reason()==null?"模组来源尚不可用":status.reason());if(!status.loaders().contains(loader))throw new TaskFailure("INCOMPATIBLE","所选来源没有此加载器标签");var project=provider.project(ref.projectId(),token);if(project.source()!=ref.source()||!project.projectId().equals(ref.projectId()))throw new TaskFailure("FILE_CHANGED","项目来源或 ID 已变更");var file=provider.resolve(ref,token);if(!file.ref().equals(ref))throw new TaskFailure("FILE_CHANGED","平台文件已变更，请重新选择");
  if(!file.downloadable()||file.sourceDigest()==null||file.size()==null||file.downloadSpec()==null||!ModProtocol.compatible(file,gameVersion,loader))throw new TaskFailure("INCOMPATIBLE",file.blockedReason()==null?"文件与所选游戏版本或加载器不兼容":file.blockedReason());var spec=file.downloadSpec();RemoteService expected=ref.source()==ModSource.MODRINTH?RemoteService.MODRINTH_CDN:RemoteService.CURSEFORGE_CDN;if(spec.service()!=expected||!spec.digest().equals(file.sourceDigest())||spec.expectedSize()!=file.size())throw new TaskFailure("FILE_CHANGED","来源文件描述不一致");return file;
 }
 public void runDownload(TaskRequest request,TaskContext context)throws IOException{
  if(request.kind()!=TaskKind.MOD_DOWNLOAD)throw new IllegalArgumentException("不是模组下载任务");var input=request.inputs();var ref=new ModFileRef(ModSource.valueOf(input.get("source")),input.get("projectId"),input.get("versionId"),input.get("fileKey"));var file=resolve(ref,input.get("gameVersion"),input.get("loader"),context.token());var pinned=new FileDigest(FileDigest.Algorithm.valueOf(input.get("digestAlgorithm")),input.get("digest"));if(!pinned.equals(file.sourceDigest())||Long.parseLong(input.get("size"))!=file.size())throw new TaskFailure("FILE_CHANGED","排队期间文件摘要或长度已变化，请重新选择");library.publish(provider(ref.source()).project(ref.projectId(),context.token()),file,context);
 }
}
