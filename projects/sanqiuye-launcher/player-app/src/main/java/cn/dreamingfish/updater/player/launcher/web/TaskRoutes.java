package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.downloads.*;
import java.io.*;
import java.util.*;
public final class TaskRoutes implements LauncherRoutes {
 private final TaskManager tasks;public TaskRoutes(TaskManager tasks){this.tasks=tasks;}
 @Override public Optional<ApiResponse> handle(ApiRequest request)throws IOException{
  if(!request.path().equals("/tasks")&&!request.path().startsWith("/tasks/"))return Optional.empty();
  if(request.path().equals("/tasks")){request.requireMethod("GET");request.onlyQuery("offset","limit");int offset=CatalogRoutes.number(request.query(),"offset",0,0,Integer.MAX_VALUE),limit=CatalogRoutes.number(request.query(),"limit",50,1,100);var page=tasks.listing(offset,limit);return Optional.of(new ApiResponse(200,Map.of("tasks",page.tasks().stream().map(TaskRoutes::view).toList(),"problems",page.problems(),"total",page.total(),"offset",offset,"limit",limit)));}
  request.onlyQuery();String[] parts=request.path().substring(7).split("/",-1);UUID id;try{id=TaskRequest.uuid(parts[0]);}catch(IllegalArgumentException invalid){throw ApiRequest.invalid("任务 ID 无效");}
  try{TaskSnapshot task;if(parts.length==1){request.requireMethod("GET");task=tasks.get(id);}else if(parts.length==2&&Set.of("cancel","retry").contains(parts[1])){request.requireMethod("POST");request.onlyFields(Set.of());task=parts[1].equals("cancel")?tasks.cancel(id):tasks.retry(id);}else throw new LauncherApiException(404,"NOT_FOUND","任务操作不存在");return Optional.of(new ApiResponse(200,Map.of("task",view(task))));}
  catch(FileNotFoundException missing){throw new LauncherApiException(404,"NOT_FOUND","任务不存在");}catch(TaskFailure error){throw failure(error);}
 }
 static LauncherApiException failure(TaskFailure error){return new LauncherApiException(Set.of("QUEUE_FULL","BUSY").contains(error.code())?503:409,error.code(),error.getMessage());}
 public static Map<String,Object> view(TaskSnapshot s){var m=new LinkedHashMap<String,Object>();m.put("id",s.id().toString());m.put("kind",s.kind().name());m.put("state",s.state().name());m.put("inputs",s.inputs());m.put("createdAt",s.createdAt().toString());m.put("updatedAt",s.updatedAt().toString());m.put("downloadedBytes",s.downloadedBytes());m.put("totalBytes",s.totalBytes());m.put("completedFiles",s.completedFiles());m.put("totalFiles",s.totalFiles());m.put("errorCode",s.errorCode());m.put("message",s.message());m.put("retryOf",s.retryOf()==null?null:s.retryOf().toString());return m;}
}
