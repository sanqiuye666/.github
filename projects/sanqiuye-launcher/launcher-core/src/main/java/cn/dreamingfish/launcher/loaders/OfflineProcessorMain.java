package cn.dreamingfish.launcher.loaders;
import java.net.*;
import java.nio.file.Path;
import java.util.*;

/** 固定子进程入口；工具类与启动器类加载器隔离，不授予工具启动器代码域的权限。 */
public final class OfflineProcessorMain {
 private OfflineProcessorMain(){}
 public static void main(String[] args)throws Exception{
  int count=Integer.parseInt(args[1]);if(count<1||count>128||args.length<count+2)throw new IllegalArgumentException("处理器类路径无效");var urls=new URL[count];for(int i=0;i<count;i++)urls[i]=Path.of(args[i+2]).toUri().toURL();
  try(var loader=new URLClassLoader(urls,ClassLoader.getPlatformClassLoader())){Thread.currentThread().setContextClassLoader(loader);if(args[0].equals("sqy.mapping.Sanitize")){sanitize(loader,Arrays.copyOfRange(args,count+2,args.length));return;}var main=Class.forName(args[0],true,loader).getMethod("main",String[].class);try{main.invoke(null,(Object)Arrays.copyOfRange(args,count+2,args.length));}catch(java.lang.reflect.InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof Exception error)throw error;if(cause instanceof Error error)throw error;throw failure;}}
 }
 @SuppressWarnings({"unchecked","rawtypes"})
 private static void sanitize(ClassLoader loader,String[] args)throws Exception{
  if(args.length!=2)throw new IllegalArgumentException("映射转换参数无效");Path root=Path.of(System.getProperty("user.dir")).toRealPath();Path input=Path.of(args[0]).toRealPath(),output=Path.of(args[1]).toAbsolutePath().normalize();if(!input.startsWith(root)||!output.startsWith(root)||!output.getParent().toRealPath().startsWith(root)||java.nio.file.Files.isSymbolicLink(output))throw new IllegalArgumentException("映射转换路径越界");Class<?> type=Class.forName("net.minecraftforge.srgutils.IMappingFile",true,loader),format=Class.forName("net.minecraftforge.srgutils.IMappingFile$Format",true,loader);Object mapping;try(var in=java.nio.file.Files.newInputStream(input)){mapping=type.getMethod("load",java.io.InputStream.class).invoke(null,in);}Object tsrg=Enum.valueOf((Class)format,"TSRG");type.getMethod("write",Path.class,format,boolean.class).invoke(mapping,output,tsrg,false);
 }
}
