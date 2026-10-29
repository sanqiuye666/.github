package cn.dreamingfish.launcher.loaders.fabric;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/** 只认识Fabric客户端profile，坐标只映射到官方Maven；不执行安装器。 */
public record FabricProfile(String mainClass,List<String> jvmArguments,List<Library> libraries) {
 public record Library(String path,URI uri,FileDigest digest,Long size){}
 public static final String PATH="versions/fabric.json";
 public FabricProfile {jvmArguments=List.copyOf(jvmArguments);libraries=List.copyOf(libraries);}
 public static void version(String value){if(value==null||!value.matches("[A-Za-z0-9][A-Za-z0-9_.+ -]{0,127}")||value.contains(".."))throw new IllegalArgumentException("Fabric版本标识无效");}
 public static FabricProfile parse(byte[] bytes,String game,String loader)throws IOException{
  try{
   version(game);version(loader);var n=LauncherJson.read(bytes,2*1024*1024);
   if(!("fabric-loader-"+loader+"-"+game).equals(LauncherJson.text(n,"id",256))||!game.equals(LauncherJson.text(n,"inheritsFrom",128)))throw new IllegalArgumentException("Fabric继承版本不匹配");
   String main=LauncherJson.text(n,"mainClass",256);if(!Set.of("net.fabricmc.loader.impl.launch.knot.KnotClient","net.fabricmc.loader.launch.knot.KnotClient").contains(main))throw new IllegalArgumentException("不支持的Fabric客户端入口");
   var args=n.path("arguments");if(!args.isObject()||args.size()!=2||!args.path("game").isArray()||!args.path("game").isEmpty()||!args.path("jvm").isArray()||args.path("jvm").size()>1)throw new IllegalArgumentException("不支持的Fabric参数结构");
   var jvm=new ArrayList<String>();for(var value:args.path("jvm")){if(!value.isTextual()||!value.textValue().equals("-DFabricMcEmu= net.minecraft.client.main.Main "))throw new IllegalArgumentException("不支持的Fabric JVM参数");jvm.add(value.textValue());}
   if(!n.path("libraries").isArray()||n.path("libraries").size()<2||n.path("libraries").size()>64)throw new IllegalArgumentException("Fabric依赖数量无效");
   var libraries=new ArrayList<Library>();var paths=new HashSet<String>();boolean hasLoader=false,hasMappings=false;
   for(JsonNode library:n.path("libraries")){
    if(!"https://maven.fabricmc.net/".equals(LauncherJson.text(library,"url",128)))throw new IllegalArgumentException("Fabric依赖不是指定官方来源");
    String name=LauncherJson.text(library,"name",256);String[] parts=name.split(":",-1);if(parts.length!=3)throw new IllegalArgumentException("不支持的Maven坐标");for(String part:parts)if(!part.matches("[A-Za-z0-9_][A-Za-z0-9_.+\\-]{0,127}")||part.contains(".."))throw new IllegalArgumentException("Maven坐标含路径或未知分类");
    String path=parts[0].replace('.','/')+"/"+parts[1]+"/"+parts[2]+"/"+parts[1]+"-"+parts[2]+".jar";if(!paths.add(path))throw new IllegalArgumentException("Fabric依赖重复");
    FileDigest digest=null;for(var algorithm:List.of(FileDigest.Algorithm.SHA1,FileDigest.Algorithm.SHA512,FileDigest.Algorithm.SHA256)){String key=algorithm.name().toLowerCase(Locale.ROOT);if(library.has(key)){var d=new FileDigest(algorithm,LauncherJson.text(library,key,128));digest=d;}}
    Long size=null;if(library.has("size")){if(!library.path("size").isIntegralNumber()||!library.path("size").canConvertToLong()||library.path("size").longValue()<0||library.path("size").longValue()>64L*1024*1024)throw new IllegalArgumentException("Fabric依赖长度无效");size=library.path("size").longValue();}
    if(name.startsWith("net.fabricmc:fabric-loader:")){if(!parts[2].equals(loader))throw new IllegalArgumentException("Fabric Loader版本不一致");hasLoader=true;}
    if(name.startsWith("net.fabricmc:intermediary:")){if(!parts[2].equals(game))throw new IllegalArgumentException("Fabric映射版本不一致");hasMappings=true;}
    libraries.add(new Library(path,URI.create("https://maven.fabricmc.net/"+path),digest,size));
   }
   if(!hasLoader||!hasMappings)throw new IllegalArgumentException("Fabric缺少固定Loader或映射依赖");return new FabricProfile(main,jvm,libraries);
  }catch(IllegalArgumentException invalid){throw new IOException("Fabric客户端元数据无效："+invalid.getMessage(),invalid);}
 }
}
