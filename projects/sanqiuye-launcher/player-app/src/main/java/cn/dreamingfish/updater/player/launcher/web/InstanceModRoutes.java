package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.downloads.*;import cn.dreamingfish.launcher.mods.*;import cn.dreamingfish.launcher.storage.*;
import java.io.*;import java.time.*;import java.util.*;
/** 预览不下载；确认只接收一次性计划ID，耗时查询共用有界取消器。 */
public final class InstanceModRoutes implements LauncherRoutes,AutoCloseable {
 private final InstanceModService service;private final TaskManager tasks;private final Clock clock;private final InstallationQueries queries=new InstallationQueries(Duration.ofSeconds(30));
 private record Preview(InstanceModStore.Plan plan,Instant expires){}
 private final Map<UUID,Preview> previews=new LinkedHashMap<>();
 public InstanceModRoutes(InstanceModService service,TaskManager tasks,Clock clock){this.service=service;this.tasks=tasks;this.clock=clock;}
 @Override public Optional<ApiResponse> handle(ApiRequest r)throws IOException{
  if(!Set.of("/mods/instance","/mods/instance/plan","/mods/instance/apply","/mods/instance/cancel-query").contains(r.path()))return Optional.empty();
  try{
   if(r.path().equals("/mods/instance/cancel-query")){r.requireMethod("POST");r.onlyQuery();r.onlyFields(Set.of("requestId"));queries.cancel(InstallationQueries.id(r.text("requestId",36)));return Optional.of(new ApiResponse(200,Map.of("cancelRequested",true)));}
   if(r.path().equals("/mods/instance")){r.requireMethod("GET");r.onlyQuery("instanceId","requestId");String id=r.query().get("instanceId");TaskRequest.uuid(id);return queries.run(InstallationQueries.id(r.query().get("requestId")),t->Optional.of(new ApiResponse(200,service.listing(id,t))));}
   r.requireMethod("POST");
   if(r.path().equals("/mods/instance/plan")){
    r.onlyQuery("requestId");r.onlyFields(Set.of("instanceId","operation","ref"));String id=r.text("instanceId",36);TaskRequest.uuid(id);String operation=r.body().has("operation")?r.text("operation",16):"INSTALL";
    if(!Set.of("INSTALL","REMOVE","FABRIC_API").contains(operation)||!operation.equals("INSTALL")&&r.body().has("ref"))throw ApiRequest.invalid("模组操作或引用无效");
    return queries.run(InstallationQueries.id(r.query().get("requestId")),token->{var plan=switch(operation){case "REMOVE"->service.removal(id,token);case "FABRIC_API"->service.fabricApi(id,token);default->service.plan(id,reference(r),token);};
     UUID key=UUID.randomUUID();Instant expires=clock.instant().plusSeconds(600);synchronized(previews){previews.entrySet().removeIf(e->!e.getValue().expires().isAfter(clock.instant()));if(previews.size()>=16)throw new LauncherApiException(503,"BUSY","模组计划已满，请先确认或稍后重试");previews.put(key,new Preview(plan,expires));}
     var existing=plan.removal()?service.listing(id,token):null;if(existing!=null&&!existing.fingerprint().equals(plan.beforeSha256()))throw new TaskFailure("PLAN_CHANGED","模组目录变化，请重新预览");
     var files=plan.removal()?existing.entries().stream().map(f->Map.of("source",f.source().name(),"projectId",f.projectId(),"versionId",f.versionId(),"fileName",f.fileName(),"size",f.size(),"digest","SHA256:"+f.sha256())).toList():plan.graph().files().stream().map(f->Map.of("source",f.ref().source().name(),"projectId",f.ref().projectId(),"versionId",f.ref().versionId(),"fileName",f.fileName(),"size",f.size(),"digest",f.sourceDigest().algorithm()+":"+f.sourceDigest().hex())).toList();
     return Optional.of(new ApiResponse(200,Map.of("planId",key.toString(),"instanceId",id,"gameVersion",plan.installation().versionId(),"loader",plan.installation().loaderKind().name().toLowerCase(Locale.ROOT),"removal",plan.removal(),"files",files,"totalBytes",plan.removal()?0:plan.graph().totalBytes(),"warnings",plan.removal()?List.of("仅移除三秋叶收据内的受管模组；手动文件保留，原目录备份保留。"):plan.graph().warnings(),"expiresAt",expires.toString())));
    });
   }
   r.onlyQuery();r.onlyFields(Set.of("planId"));UUID id=TaskRequest.uuid(r.text("planId",36));synchronized(previews){var preview=previews.get(id);if(preview==null||!preview.expires().isAfter(clock.instant())){previews.remove(id);throw new LauncherApiException(410,"PLAN_EXPIRED","模组计划已使用或过期，请重新预览");}var task=tasks.submit(service.request(preview.plan()));previews.remove(id);return Optional.of(new ApiResponse(202,Map.of("task",TaskRoutes.view(task))));}
  }catch(TaskFailure failure){throw TaskRoutes.failure(failure);}catch(LauncherBusyException busy){throw new LauncherApiException(409,"INSTANCE_BUSY","实例正在安装、检查或运行，请稍后重试");}
  catch(RemoteHttpException remote){if(remote.status()==429)return Optional.of(new ApiResponse(429,Map.of("error",Map.of("code","RATE_LIMITED","message","来源请求受限，请稍后重试","retryAfterSeconds",remote.retryAfterSeconds()==null?60:remote.retryAfterSeconds()))));throw new LauncherApiException(502,"SOURCE_UNAVAILABLE","官方模组来源暂不可用");}
  catch(IllegalArgumentException invalid){throw ApiRequest.invalid("模组实例、引用或参数无效");}
 }
 private static ModFileRef reference(ApiRequest r)throws IOException{var n=r.body().path("ref");var keys=new HashSet<String>();n.fieldNames().forEachRemaining(keys::add);if(!n.isObject()||!keys.equals(Set.of("source","projectId","versionId","fileKey")))throw ApiRequest.invalid("文件引用字段无效");return new ModFileRef(ModSource.valueOf(LauncherJson.text(n,"source",16)),LauncherJson.text(n,"projectId",128),LauncherJson.text(n,"versionId",128),LauncherJson.text(n,"fileKey",64));}
 @Override public void close(){queries.close();}
}
