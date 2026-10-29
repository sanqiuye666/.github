package cn.dreamingfish.launcher.downloads;
import cn.dreamingfish.launcher.storage.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
public final class TaskRepository implements AutoCloseable {
 private static final Set<String> KEYS=Set.of("schemaVersion","id","kind","state","inputs","createdAt","updatedAt","downloadedBytes","totalBytes","completedFiles","totalFiles","errorCode","message","retryOf");
 private final LauncherStorage storage;private final LauncherStorage.Lease lease;private final Map<UUID,TaskSnapshot> records=new LinkedHashMap<>();private final List<Problem> problems=new ArrayList<>();
 public record Problem(String recordId,String reason){}
 public TaskRepository(Path root,Clock clock)throws IOException{
  storage=new LauncherStorage(root);lease=storage.lock(Path.of("tasks/.manager.lock"));
  try{for(var file:storage.children(Path.of("tasks"))){String name=file.getFileName().toString();if(name.equals(".manager.lock")||name.startsWith(".write-"))continue;
   try{if(!name.endsWith(".json"))throw new IOException("未知记录");var id=TaskRequest.uuid(name.substring(0,name.length()-5));var n=LauncherJson.read(storage.read(file,65536),65536);var keys=new HashSet<String>();n.fieldNames().forEachRemaining(keys::add);if(!keys.equals(KEYS)||!n.path("schemaVersion").isInt()||n.path("schemaVersion").intValue()!=1||!id.toString().equals(n.path("id").asText()))throw new IOException("记录结构无效");
    var inputs=new HashMap<String,String>();if(!n.path("inputs").isObject())throw new IOException("任务输入无效");n.path("inputs").fields().forEachRemaining(e->{if(!e.getValue().isTextual())throw new IllegalArgumentException("任务输入类型无效");inputs.put(e.getKey(),e.getValue().textValue());});
    var snapshot=new TaskSnapshot(id,TaskKind.valueOf(n.path("kind").asText()),TaskState.valueOf(n.path("state").asText()),inputs,Instant.parse(n.path("createdAt").asText()),Instant.parse(n.path("updatedAt").asText()),number(n,"downloadedBytes"),nullableNumber(n,"totalBytes"),number(n,"completedFiles"),nullableNumber(n,"totalFiles"),text(n,"errorCode"),text(n,"message"),n.path("retryOf").isNull()?null:TaskRequest.uuid(n.path("retryOf").asText()));
    if(!snapshot.state().terminal())snapshot=new TaskSnapshot(id,snapshot.kind(),TaskState.INTERRUPTED,inputs,snapshot.createdAt(),clock.instant(),snapshot.downloadedBytes(),snapshot.totalBytes(),snapshot.completedFiles(),snapshot.totalFiles(),"INTERRUPTED","上次进程结束，任务未自动重启",snapshot.retryOf());
    save(snapshot);
   }catch(IOException|RuntimeException error){problems.add(new Problem(name,"任务记录损坏或无法读取，原文件已保留"));}
  }}catch(IOException|RuntimeException error){lease.close();throw error;}
 }
 private static long number(JsonNode n,String key)throws IOException{if(!n.path(key).isIntegralNumber()||!n.path(key).canConvertToLong())throw new IOException("计数字段无效");return n.path(key).longValue();}
 private static Long nullableNumber(JsonNode n,String key)throws IOException{return n.path(key).isNull()?null:number(n,key);}
 private static String text(JsonNode n,String key)throws IOException{var v=n.path(key);if(v.isNull())return null;if(!v.isTextual()||v.textValue().length()>512||v.textValue().chars().anyMatch(Character::isISOControl))throw new IOException("文本字段无效");return v.textValue();}
 public synchronized void save(TaskSnapshot s)throws IOException{
  var map=new LinkedHashMap<String,Object>();map.put("schemaVersion",1);map.put("id",s.id().toString());map.put("kind",s.kind().name());map.put("state",s.state().name());map.put("inputs",s.inputs());map.put("createdAt",s.createdAt().toString());map.put("updatedAt",s.updatedAt().toString());map.put("downloadedBytes",s.downloadedBytes());map.put("totalBytes",s.totalBytes());map.put("completedFiles",s.completedFiles());map.put("totalFiles",s.totalFiles());map.put("errorCode",s.errorCode());map.put("message",s.message());map.put("retryOf",s.retryOf()==null?null:s.retryOf().toString());storage.writeAtomically(Path.of("tasks/"+s.id()+".json"),LauncherJson.write(map));records.put(s.id(),s);
 }
 public synchronized Map<UUID,TaskSnapshot> snapshots(){return Map.copyOf(records);}public List<Problem> problems(){return List.copyOf(problems);}
 @Override public void close()throws IOException{lease.close();}
}
