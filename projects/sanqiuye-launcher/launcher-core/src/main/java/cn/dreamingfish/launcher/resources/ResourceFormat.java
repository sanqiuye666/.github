package cn.dreamingfish.launcher.resources;
import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.vanilla.SafeInstallPath;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
/** 只检查有界容器和必要标记，不解压、不执行，也不宣称游戏兼容。 */
final class ResourceFormat {
 static void validate(Path file,ResourceKind kind)throws IOException{
  if(kind==ResourceKind.SCHEMATICS){try(var raw=Files.newInputStream(file);var buffered=new BufferedInputStream(raw)){
   buffered.mark(2);int a=buffered.read(),b=buffered.read();buffered.reset();InputStream input=a==31&&b==139?new GZIPInputStream(buffered):buffered;
   var in=new DataInputStream(input);if(in.readUnsignedByte()!=10)throw new IOException("投影需要NBT复合根容器");int length=in.readUnsignedShort();if(length>4096||in.readNBytes(length).length!=length)throw new IOException("NBT根名称无效");
  }return;}
  try(var zip=new ZipFile(file.toFile())){
   int count=0;long expanded=0;boolean shader=false;var names=new HashSet<String>();
   for(var entries=zip.entries();entries.hasMoreElements();){var entry=entries.nextElement();if(++count>10000)throw new IOException("压缩包文件过多");String name=entry.getName();String normalized=entry.isDirectory()&&name.endsWith("/")?name.substring(0,name.length()-1):name;
    try{SafeInstallPath.parse(normalized);}catch(IllegalArgumentException invalid){throw new IOException("压缩包含不安全路径");}
    if(!names.add(normalized)||entry.getSize()<0||entry.getSize()>1024L*1024*1024||(expanded+=entry.getSize())>1024L*1024*1024)throw new IOException("压缩包大小或重复条目无效");
    if(!entry.isDirectory()&&name.startsWith("shaders/")&&(name.endsWith(".fsh")||name.endsWith(".vsh")||name.endsWith(".glsl")||name.endsWith(".csh")))shader=true;
   }
   if(kind==ResourceKind.SHADERS){if(!shader)throw new IOException("未发现shaders目录内的光影程序");}
   else {String marker=kind==ResourceKind.RECORDINGS?"metaData.json":"pack.mcmeta";var entry=zip.getEntry(marker);if(entry==null||entry.isDirectory()||entry.getSize()>65536)throw new IOException("缺少资源容器元数据");
    try(var in=zip.getInputStream(entry)){var n=LauncherJson.read(in.readNBytes(65537),65536);if(!n.isObject()||kind==ResourceKind.RESOURCEPACKS&&!n.path("pack").isObject())throw new IOException("元数据格式无效");}
    if(kind==ResourceKind.RECORDINGS&&(zip.getEntry("recording.tmcpr")==null||zip.getEntry("recording.tmcpr").isDirectory()))throw new IOException("录像缺少数据流");
   }
  }
 }
}
