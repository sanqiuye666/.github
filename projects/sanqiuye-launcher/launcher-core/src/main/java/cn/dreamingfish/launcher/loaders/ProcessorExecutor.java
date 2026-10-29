package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.downloads.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Java21离线文件处理：只写暂存目录、不授予网络/执行权限、有限时间和日志。 */
public final class ProcessorExecutor {
 private static String quote(String value){return value.replace("\\","\\\\").replace("\"","\\\"");}
 public void run(Path staging,List<Path> jars,String main,List<String> arguments,CancellationToken token,Duration timeout)throws IOException{
  token.check();if(Runtime.version().feature()!=21)throw new TaskFailure("INSTALLER_JAVA_UNSUPPORTED","核心处理器目前要求启动器随包Java21");if(main==null||!main.matches("[A-Za-z_$][A-Za-z0-9_$.]{0,255}")||jars.isEmpty()||jars.size()>128||arguments.size()>256||arguments.stream().anyMatch(v->v==null||v.length()>32768||v.chars().anyMatch(Character::isISOControl)))throw new IOException("处理器参数无效");
  Path stage=staging.toRealPath();if(Files.isSymbolicLink(staging))throw new IOException("处理器暂存目录是链接");var classpath=new ArrayList<String>();for(Path jar:jars){Path real=jar.toRealPath();if(!real.startsWith(stage)||Files.isSymbolicLink(jar)||!Files.isRegularFile(real))throw new IOException("处理器依赖超出暂存目录");classpath.add(real.toString());}
  Path core;try{core=Path.of(OfflineProcessorMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());}catch(Exception e){throw new IOException("无法确定本机处理器入口",e);}if(core.toString().contains(File.pathSeparator))throw new IOException("启动器路径含类路径分隔符");
  Path tools=stage.resolve("versions/processor-work");Files.createDirectories(tools);Path policy=tools.resolve("offline.policy"),log=stage.resolve("versions/processor.log");String stagePath=quote(stage.toString().replace('\\','/'));String javaPath=quote(Path.of(System.getProperty("java.home")).toString().replace('\\','/'));String code=quote(core.toUri().toASCIIString());
  String policyText="grant codeBase \""+code+"\" { permission java.security.AllPermission; };\n"+
   "grant { permission java.io.FilePermission \""+stagePath+"\", \"read,write,delete\"; permission java.io.FilePermission \""+stagePath+"/"+"-\", \"read,write,delete\"; permission java.io.FilePermission \""+javaPath+"/"+"-\", \"read\"; permission java.util.PropertyPermission \"*\", \"read\"; permission java.util.PropertyPermission \"user.timezone\", \"write\"; permission java.lang.RuntimePermission \"exitVM.*\"; permission java.lang.RuntimePermission \"accessDeclaredMembers\"; permission java.lang.RuntimePermission \"getClassLoader\"; permission java.lang.RuntimePermission \"getProtectionDomain\"; permission java.lang.RuntimePermission \"setContextClassLoader\"; permission java.lang.RuntimePermission \"modifyThread\"; permission java.lang.RuntimePermission \"modifyThreadGroup\"; permission java.lang.RuntimePermission \"accessClassInPackage.*\"; permission java.lang.reflect.ReflectPermission \"suppressAccessChecks\"; };\n";
  Files.writeString(policy,policyText);var command=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java").toString(),"-Xmx1024M","-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Djava.security.manager=default","-Djava.security.policy=="+policy.toUri().toASCIIString(),"-Djava.io.tmpdir="+tools,"-cp",core.toString(),OfflineProcessorMain.class.getName(),main,Integer.toString(classpath.size())));command.addAll(classpath);command.addAll(arguments);
  var builder=new ProcessBuilder(command).directory(stage.toFile()).redirectErrorStream(true);builder.environment().clear();String systemRoot=System.getenv("SystemRoot");if(systemRoot!=null)builder.environment().put("SystemRoot",systemRoot);builder.environment().put("TEMP",tools.toString());builder.environment().put("TMP",tools.toString());builder.environment().put("USERPROFILE",tools.toString());
  long started=System.nanoTime();Process process=builder.start();var outputFailure=new AtomicReference<IOException>();long existing=Files.exists(log)?Files.size(log):0;
  Thread reader=Thread.ofPlatform().daemon(true).name("sqy-core-processor-log").start(()->{try(var in=process.getInputStream();var out=Files.newOutputStream(log,StandardOpenOption.CREATE,StandardOpenOption.APPEND)){byte[] block=new byte[4096];int n;long total=existing;while((n=in.read(block))!=-1){total+=n;if(total>4L*1024*1024)throw new TaskFailure("PROCESSOR_OUTPUT_LIMIT","核心处理器日志超过4MiB");out.write(block,0,n);}}catch(IOException error){outputFailure.set(error);process.destroyForcibly();}});
  try(var registration=token.onCancel(process::destroyForcibly)){
   while(!process.waitFor(100,TimeUnit.MILLISECONDS)){token.check();if(System.nanoTime()-started>timeout.toNanos())throw new TaskFailure("PROCESSOR_TIMEOUT","核心处理器超时，未激活安装");}token.check();reader.join(5000);if(reader.isAlive())throw new TaskFailure("PROCESSOR_OUTPUT_TIMEOUT","核心处理器输出未关闭");if(outputFailure.get()!=null)throw outputFailure.get();if(process.exitValue()!=0)throw new TaskFailure("PROCESSOR_FAILED","核心处理器失败（退出码"+process.exitValue()+"），请检查本实例安装日志");
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new InterruptedIOException("核心处理已取消");}
  finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();try{process.waitFor(3,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}}
 }
}
