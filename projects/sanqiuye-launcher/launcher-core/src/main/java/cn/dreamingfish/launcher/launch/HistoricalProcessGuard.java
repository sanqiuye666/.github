package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.downloads.TaskRequest;
import cn.dreamingfish.launcher.storage.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

/** 核心退出后系统锁会释放；安装和启动都不能绕过仍存活的历史游戏。 */
public final class HistoricalProcessGuard {
 private HistoricalProcessGuard(){}
 public static void ensureIdle(LauncherStorage storage,String instanceId)throws IOException{
  TaskRequest.uuid(instanceId);
  for(Path folder:storage.children(Path.of("instances",instanceId,"launches"))){
   String id=folder.getFileName().toString();try{TaskRequest.uuid(id);}catch(IllegalArgumentException ignored){continue;}
   storage.requireDirectory(folder);if(!storage.exists(folder.resolve("launch.json")))continue;
   Long pid;Instant expected;
   try{
    var n=LauncherJson.read(storage.read(folder.resolve("launch.json"),1024*1024),1024*1024);
    if(n.path("schemaVersion").asInt()!=1||!id.equals(n.path("id").asText())||!instanceId.equals(n.path("instanceId").asText()))throw new IllegalArgumentException();
    var state=LaunchSnapshot.State.valueOf(LauncherJson.text(n,"state",32));if(Set.of(LaunchSnapshot.State.EXITED,LaunchSnapshot.State.FAILED,LaunchSnapshot.State.CANCELLED).contains(state))continue;
    if(!n.has("pid")||!n.has("processStartedAt"))throw new IllegalArgumentException();
    if(n.path("pid").isNull())pid=null;else{if(!n.path("pid").isIntegralNumber()||!n.path("pid").canConvertToLong()||n.path("pid").longValue()<1)throw new IllegalArgumentException();pid=n.path("pid").longValue();}
    expected=n.path("processStartedAt").isNull()?null:Instant.parse(LauncherJson.text(n,"processStartedAt",64));
   }catch(IOException|RuntimeException invalid){throw new LaunchFailure("RECORD_INVALID","启动历史记录损坏，未改动实例，请检查后重试");}
   if(pid==null)throw new LaunchFailure("INSTANCE_BUSY","存在状态待确认的启动记录，请先检查游戏窗口");
   var process=ProcessHandle.of(pid);if(process.isPresent()&&process.get().isAlive()){var started=process.get().info().startInstant();if(expected==null||started.isEmpty()||started.get().equals(expected))throw new LaunchFailure("INSTANCE_BUSY","上次游戏进程可能仍在运行，请先在游戏窗口退出");}
  }
 }
}
