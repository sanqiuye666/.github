package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.*;
import java.io.*;import java.util.*;
/** 队列只保存引用及计划摘要，执行前重新解析并核对同一计划。 */
public final class InstanceModService {
 private final ModService sources;private final ModDependencyResolver resolver;private final InstanceModStore store;
 public InstanceModService(ModService sources,InstanceModStore store){this.sources=sources;this.resolver=new ModDependencyResolver(sources);this.store=store;}
 public InstanceModStore.Plan plan(String instance,ModFileRef ref,CancellationToken token)throws IOException{
  var target=store.listing(instance,token);return store.prepare(instance,resolver.plan(ref,target.gameVersion(),target.loader(),token),token);
 }
 public InstanceModStore.Plan fabricApi(String instance,CancellationToken token)throws IOException{
  var target=store.listing(instance,token);if(!target.loader().equals("fabric"))throw new TaskFailure("INCOMPATIBLE","Fabric API快捷入口仅用于Fabric实例");
  var file=sources.dependency(ModSource.MODRINTH,new ModDependency(ModDependency.Kind.REQUIRED,"P7dR8mSH",null,null),target.gameVersion(),target.loader(),token);return plan(instance,file.ref(),token);
 }
 public InstanceModStore.Plan removal(String instance,CancellationToken token)throws IOException{return store.removal(instance,token);}
 public InstanceModStore.Listing listing(String instance,CancellationToken token)throws IOException{return store.listing(instance,token);}
 public TaskRequest request(InstanceModStore.Plan plan)throws IOException{
  var input=new HashMap<String,String>(Map.of("instanceId",plan.instanceId(),"planSha256",plan.fingerprint()));if(plan.removal())return new TaskRequest(TaskKind.MOD_REMOVE,input);
  var graph=plan.graph();var root=graph.files().getFirst().ref();input.putAll(Map.of("source",root.source().name(),"projectId",root.projectId(),"versionId",root.versionId(),"fileKey",root.fileKey(),"gameVersion",graph.gameVersion(),"loader",graph.loader()));return new TaskRequest(TaskKind.MOD_INSTALL,input);
 }
 public void run(TaskRequest request,TaskContext context)throws IOException{
  var input=request.inputs();InstanceModStore.Plan current;
  if(request.kind()==TaskKind.MOD_REMOVE)current=removal(input.get("instanceId"),context.token());
  else if(request.kind()==TaskKind.MOD_INSTALL)current=plan(input.get("instanceId"),new ModFileRef(ModSource.valueOf(input.get("source")),input.get("projectId"),input.get("versionId"),input.get("fileKey")),context.token());
  else throw new IllegalArgumentException("不是实例模组任务");
  if(!current.fingerprint().equals(input.get("planSha256")))throw new TaskFailure("PLAN_CHANGED","排队期间实例或官方依赖已变化，请重新预览");
  if(current.removal())store.remove(current,context);else store.install(current,context);
 }
}
