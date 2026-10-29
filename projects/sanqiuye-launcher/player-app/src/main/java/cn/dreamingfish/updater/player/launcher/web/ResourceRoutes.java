package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.resources.*;import cn.dreamingfish.launcher.downloads.TaskRequest;import cn.dreamingfish.launcher.storage.LauncherBusyException;
import java.io.*;import java.nio.file.Path;import java.time.Duration;import java.util.*;
public final class ResourceRoutes implements LauncherRoutes,AutoCloseable {
 private final ResourceStore store;private final InstallationQueries queries=new InstallationQueries(Duration.ofSeconds(30));
 public ResourceRoutes(ResourceStore store){this.store=store;}
 @Override public Optional<ApiResponse> handle(ApiRequest r)throws IOException{
  if(!Set.of("/resources","/resources/import","/resources/change","/resources/restore","/resources/cancel").contains(r.path()))return Optional.empty();
  try{
   if(r.path().equals("/resources/cancel")){r.requireMethod("POST");r.onlyQuery();r.onlyFields(Set.of("requestId"));queries.cancel(InstallationQueries.id(r.text("requestId",36)));return Optional.of(new ApiResponse(200,Map.of("cancelRequested",true)));}
   if(r.path().equals("/resources")){r.requireMethod("GET");r.onlyQuery("instanceId","kind");String id=r.query().get("instanceId");TaskRequest.uuid(id);ResourceKind kind=ResourceKind.valueOf(r.query().get("kind"));return Optional.of(new ApiResponse(200,store.listing(id,kind)));}
   r.requireMethod("POST");r.onlyQuery("requestId");
   Set<String> fields=switch(r.path()){case "/resources/import"->Set.of("instanceId","kind","source");case "/resources/restore"->Set.of("instanceId","kind","backupId");default->Set.of("instanceId","kind","name","revision","operation","target");};r.onlyFields(fields);
   String id=r.text("instanceId",36);TaskRequest.uuid(id);ResourceKind kind=ResourceKind.valueOf(r.text("kind",32));
   return queries.run(InstallationQueries.id(r.query().get("requestId")),token->{var value=switch(r.path()){case "/resources/import"->store.importFile(id,kind,Path.of(r.text("source",4096)),token);case "/resources/restore"->store.restore(id,kind,r.text("backupId",36),token);default->store.change(id,kind,r.text("name",128),r.text("revision",64),r.text("operation",16),r.body().path("target").isNull()?null:r.text("target",128),token);};return Optional.of(new ApiResponse(200,value));});
  }catch(LauncherBusyException busy){throw new LauncherApiException(409,"INSTANCE_BUSY","实例正在安装、检查或运行，请稍后重试");}
 }
 @Override public void close(){queries.close();}
}
