package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;

public record MavenLibrary(String path,URI uri,RemoteService service,FileDigest digest,Long size) {
 public static String path(String coordinate){
  if(coordinate==null||coordinate.length()>512)throw new IllegalArgumentException("Maven坐标无效");String[] extension=coordinate.split("@",-1);if(extension.length>2)throw new IllegalArgumentException("Maven扩展名无效");String ext=extension.length==2?extension[1]:"jar";if(!Set.of("jar","zip","txt","json","tsrg").contains(ext))throw new IllegalArgumentException("Maven扩展名尚未支持");
  String[] parts=extension[0].split(":",-1);if(parts.length<3||parts.length>4)throw new IllegalArgumentException("Maven坐标段数无效");for(String part:parts)if(!part.matches("[A-Za-z0-9_][A-Za-z0-9_.+\\-]{0,127}")||part.contains(".."))throw new IllegalArgumentException("Maven坐标含非法路径");
  return parts[0].replace('.','/')+"/"+parts[1]+"/"+parts[2]+"/"+parts[1]+"-"+parts[2]+(parts.length==4?"-"+parts[3]:"")+"."+ext;
 }
 public static MavenLibrary simple(JsonNode n){
  String root=LauncherJson.text(n,"url",160);RemoteService service=switch(root){case "https://maven.fabricmc.net/"->RemoteService.FABRIC_LIBRARIES;case "https://maven.quiltmc.org/repository/release/"->RemoteService.QUILT_LIBRARIES;default->throw new IllegalArgumentException("未认可的核心Maven来源");};String path=path(LauncherJson.text(n,"name",512));
  FileDigest digest=null;for(var algorithm:List.of(FileDigest.Algorithm.SHA1,FileDigest.Algorithm.SHA512,FileDigest.Algorithm.SHA256)){String key=algorithm.name().toLowerCase(Locale.ROOT);if(n.has(key))digest=new FileDigest(algorithm,LauncherJson.text(n,key,128));}
  Long size=null;if(n.has("size")){if(!n.path("size").isIntegralNumber()||!n.path("size").canConvertToLong()||n.path("size").longValue()<0||n.path("size").longValue()>128L*1024*1024)throw new IllegalArgumentException("核心依赖长度无效");size=n.path("size").longValue();}
  return new MavenLibrary(path,URI.create(root+path),service,digest,size);
 }
 public static MavenLibrary described(JsonNode n,cn.dreamingfish.launcher.GameInstance.LoaderKind kind){
  String path=path(LauncherJson.text(n,"name",512));var a=n.path("downloads").path("artifact");if(!a.isObject()||!path.equals(LauncherJson.text(a,"path",512))||!a.path("size").canConvertToLong()||!a.path("size").isIntegralNumber())throw new IllegalArgumentException("核心依赖描述不完整");long size=a.path("size").longValue();if(size<0||size>128L*1024*1024)throw new IllegalArgumentException("核心依赖长度无效");var digest=new FileDigest(FileDigest.Algorithm.SHA1,LauncherJson.text(a,"sha1",40));var url=a.path("url");if(!url.isTextual()||url.textValue().length()>2048)throw new IllegalArgumentException("核心依赖来源无效");if(url.textValue().isEmpty())return new MavenLibrary(path,null,ForgeCatalog.service(kind),digest,size);
  URI uri=URI.create(url.textValue());RemoteService service=switch(Objects.toString(uri.getHost(),"")){case "maven.minecraftforge.net"->RemoteService.FORGE_MAVEN;case "maven.neoforged.net"->RemoteService.NEOFORGE_MAVEN;case "libraries.minecraft.net"->RemoteService.MOJANG_LIBRARIES;default->throw new IllegalArgumentException("未认可的核心依赖来源");};RemotePolicy.validateUri(service,uri);if(uri.getQuery()!=null||!uri.getPath().endsWith("/"+path))throw new IllegalArgumentException("核心依赖来源与坐标不匹配");return new MavenLibrary(path,uri,service,digest,size);
 }
}
