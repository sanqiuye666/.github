package cn.dreamingfish.launcher.downloads;
import java.io.IOException;
import java.util.UUID;
public interface TaskContext {
 UUID id();CancellationToken token();
 void progress(TaskState state,long downloadedBytes,Long totalBytes,long completedFiles,Long totalFiles)throws IOException;
 void publish(IoAction action)throws IOException;
 @FunctionalInterface interface IoAction {void run()throws IOException;}
}
