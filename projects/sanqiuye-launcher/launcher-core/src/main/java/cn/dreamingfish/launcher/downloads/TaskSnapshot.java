package cn.dreamingfish.launcher.downloads;
import java.time.Instant;
import java.util.*;
public record TaskSnapshot(UUID id,TaskKind kind,TaskState state,Map<String,String> inputs,Instant createdAt,Instant updatedAt,long downloadedBytes,Long totalBytes,long completedFiles,Long totalFiles,String errorCode,String message,UUID retryOf) {
 public TaskSnapshot {Objects.requireNonNull(id);Objects.requireNonNull(kind);Objects.requireNonNull(state);Objects.requireNonNull(createdAt);Objects.requireNonNull(updatedAt);inputs=new TaskRequest(kind,inputs).inputs();if(downloadedBytes<0||completedFiles<0||totalBytes!=null&&(totalBytes<downloadedBytes)||totalFiles!=null&&totalFiles<completedFiles)throw new IllegalArgumentException("任务计数无效");}
}
