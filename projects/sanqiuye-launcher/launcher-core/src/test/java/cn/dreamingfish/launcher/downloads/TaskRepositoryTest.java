package cn.dreamingfish.launcher.downloads;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class TaskRepositoryTest {
 @TempDir Path root;
 @Test void recoverySeparatesCorruptRecordsAndRejectsSecrets()throws Exception {
  var id=UUID.randomUUID();var now=TaskManagerTest.CLOCK.instant();var request=TaskManagerTest.request();
  try(var repo=new TaskRepository(root,TaskManagerTest.CLOCK)){repo.save(new TaskSnapshot(id,request.kind(),TaskState.DOWNLOADING,request.inputs(),now,now,1,null,0,null,null,null,null));assertThrows(IOException.class,()->new TaskRepository(root,TaskManagerTest.CLOCK));}
  Path bad=root.resolve("tasks/"+UUID.randomUUID()+".json");Files.writeString(bad,"{broken");
  try(var repo=new TaskRepository(root,TaskManagerTest.CLOCK)){assertEquals(TaskState.INTERRUPTED,repo.snapshots().get(id).state());assertEquals(1,repo.problems().size());assertEquals("{broken",Files.readString(bad));}
  assertThrows(IllegalArgumentException.class,()->new TaskRequest(TaskKind.INSTALLATION_VERIFY,Map.of("instanceId",UUID.randomUUID().toString(),"url","https://example.com")));
  assertThrows(IllegalArgumentException.class,()->new TaskRequest(TaskKind.INSTALLATION_VERIFY,Map.of("instanceId","secret/path")));
 }
 @Test void processLockIsReal()throws Exception {
  String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();var builder=new ProcessBuilder(executable,"-cp",System.getProperty("java.class.path"),LockProbe.class.getName(),root.toString());builder.environment().remove("JAVA_TOOL_OPTIONS");var child=builder.start();
  try{assertEquals("LOCKED",new BufferedReader(new InputStreamReader(child.getInputStream())).readLine());assertThrows(IOException.class,()->new TaskRepository(root,TaskManagerTest.CLOCK));}finally{child.getOutputStream().close();if(!child.waitFor(5,TimeUnit.SECONDS))child.destroyForcibly();}assertEquals(0,child.exitValue());
 }
 public static class LockProbe {public static void main(String[] args)throws Exception{try(var repo=new TaskRepository(Path.of(args[0]),TaskManagerTest.CLOCK)){System.out.println("LOCKED");System.out.flush();System.in.read();}}}
}
