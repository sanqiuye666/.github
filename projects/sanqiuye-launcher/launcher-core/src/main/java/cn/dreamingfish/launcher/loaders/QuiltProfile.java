package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.storage.LauncherJson;
import java.io.IOException;
import java.util.*;

public record QuiltProfile(String mainClass,List<MavenLibrary> libraries) {
 public QuiltProfile{libraries=List.copyOf(libraries);}
 public static QuiltProfile parse(byte[] bytes,String game,String version)throws IOException{
  try{
   LoaderIdentity.validateVersion(game);LoaderIdentity.validateVersion(version);var n=LauncherJson.read(bytes,2*1024*1024);
   if(!("quilt-loader-"+version+"-"+game).equals(LauncherJson.text(n,"id",256))||!game.equals(LauncherJson.text(n,"inheritsFrom",128))||!"org.quiltmc.loader.impl.launch.knot.KnotClient".equals(LauncherJson.text(n,"mainClass",256)))throw new IllegalArgumentException("Quilt身份或客户端入口不匹配");
   var args=n.path("arguments");var names=new HashSet<String>();args.fieldNames().forEachRemaining(names::add);if(!args.isObject()||!Set.of("game","jvm").containsAll(names)||!args.path("game").isArray()||!args.path("game").isEmpty()||args.has("jvm")&&(!args.path("jvm").isArray()||!args.path("jvm").isEmpty()))throw new IllegalArgumentException("Quilt参数尚未支持");
   if(!n.path("libraries").isArray()||n.path("libraries").size()<3||n.path("libraries").size()>64)throw new IllegalArgumentException("Quilt依赖数量无效");var libs=new ArrayList<MavenLibrary>();var paths=new HashSet<String>();var required=new HashSet<>(Set.of("org.quiltmc:quilt-loader:"+version,"org.quiltmc:hashed:"+game,"net.fabricmc:intermediary:"+game));
   for(var library:n.path("libraries")){var lib=MavenLibrary.simple(library);if(!paths.add(lib.path())||library.has("rules")||library.has("natives"))throw new IllegalArgumentException("Quilt依赖重复或含未知规则");required.remove(library.path("name").asText());libs.add(lib);}
   if(!required.isEmpty())throw new IllegalArgumentException("Quilt缺少选定核心或映射");return new QuiltProfile("org.quiltmc.loader.impl.launch.knot.KnotClient",libs);
  }catch(IllegalArgumentException invalid){throw new IOException("Quilt客户端描述无效："+invalid.getMessage(),invalid);}
 }
}
