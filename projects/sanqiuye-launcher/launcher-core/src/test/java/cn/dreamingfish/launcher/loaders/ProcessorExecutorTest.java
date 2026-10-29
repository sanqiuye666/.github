package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.downloads.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;
class ProcessorExecutorTest {
 @TempDir(cleanup=org.junit.jupiter.api.io.CleanupMode.NEVER) Path root;
 private Path tool(Path stage)throws Exception{
  Files.createDirectories(stage);Path source=root.resolve("FixtureProcessor.java"),classes=root.resolve("classes");Files.createDirectories(classes);
  Files.writeString(source,"public class FixtureProcessor {public static void main(String[] a)throws Exception{switch(a[0]){case \"write\":java.nio.file.Files.writeString(java.nio.file.Path.of(a[1]),\"generated\");break;case \"outside\":try{java.nio.file.Files.writeString(java.nio.file.Path.of(a[1]),\"changed\");System.exit(9);}catch(SecurityException expected){}break;case \"network\":try{new java.net.Socket().connect(new java.net.InetSocketAddress(\"127.0.0.1\",1),100);System.exit(9);}catch(SecurityException expected){}break;case \"sleep\":Thread.sleep(10000);break;case \"fail\":System.exit(7);}}}");
  assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"-d",classes.toString(),source.toString()));Path jar=stage.resolve("tool.jar");try(var out=new JarOutputStream(Files.newOutputStream(jar))){out.putNextEntry(new JarEntry("FixtureProcessor.class"));out.write(Files.readAllBytes(classes.resolve("FixtureProcessor.class")));out.closeEntry();}return jar;
 }
 @Test void processorCanWriteOnlyStagingAndCannotOpenNetwork()throws Exception{
  Path stage=root.resolve("stage"),jar=tool(stage),keep=root.resolve("keep.txt");Files.writeString(keep,"keep");var runner=new ProcessorExecutor();runner.run(stage,List.of(jar),"FixtureProcessor",List.of("write",stage.resolve("out.txt").toString()),new CancellationToken(),Duration.ofSeconds(10));assertEquals("generated",Files.readString(stage.resolve("out.txt")));
  runner.run(stage,List.of(jar),"FixtureProcessor",List.of("outside",keep.toString()),new CancellationToken(),Duration.ofSeconds(10));assertEquals("keep",Files.readString(keep));runner.run(stage,List.of(jar),"FixtureProcessor",List.of("network"),new CancellationToken(),Duration.ofSeconds(10));
 }
 @Test void timeoutFailureAndCancellationNeverLeaveProcessorRunning()throws Exception{
  Path stage=root.resolve("stage"),jar=tool(stage);var runner=new ProcessorExecutor();assertEquals("PROCESSOR_TIMEOUT",assertThrows(TaskFailure.class,()->runner.run(stage,List.of(jar),"FixtureProcessor",List.of("sleep"),new CancellationToken(),Duration.ofMillis(400))).code());assertEquals("PROCESSOR_FAILED",assertThrows(TaskFailure.class,()->runner.run(stage,List.of(jar),"FixtureProcessor",List.of("fail"),new CancellationToken(),Duration.ofSeconds(10))).code());var token=new CancellationToken();token.cancel();assertThrows(java.io.InterruptedIOException.class,()->runner.run(stage,List.of(jar),"FixtureProcessor",List.of("write","unused"),token,Duration.ofSeconds(10)));
 }
}
