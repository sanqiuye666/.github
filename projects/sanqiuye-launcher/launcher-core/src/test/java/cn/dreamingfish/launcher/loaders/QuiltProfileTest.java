package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class QuiltProfileTest {
 static Map<String,Object> profile(){return new LinkedHashMap<>(Map.of("id","quilt-loader-0.28.1-1.20.6","inheritsFrom","1.20.6","mainClass","org.quiltmc.loader.impl.launch.knot.KnotClient","arguments",Map.of("game",List.of()),"libraries",List.of(lib("org.quiltmc:quilt-loader:0.28.1"),lib("org.quiltmc:hashed:1.20.6"),Map.of("name","net.fabricmc:intermediary:1.20.6","url","https://maven.fabricmc.net/"))));}
 static Map<String,Object> lib(String name){return Map.of("name",name,"url","https://maven.quiltmc.org/repository/release/");}
 @Test void officialProfileWithoutJvmListSupportsBothOfficialMavens()throws Exception{
  var p=QuiltProfile.parse(LauncherJson.write(profile()),"1.20.6","0.28.1");assertEquals("org.quiltmc.loader.impl.launch.knot.KnotClient",p.mainClass());assertEquals(3,p.libraries().size());assertTrue(p.libraries().getFirst().uri().toString().endsWith("org/quiltmc/quilt-loader/0.28.1/quilt-loader-0.28.1.jar"));
 }
 @Test void wrongGameVersionMainArgumentsAndMavenLocationsFail()throws Exception{
  for(var field:List.of(Map.entry("inheritsFrom","1.21.1"),Map.entry("mainClass","example.Main"),Map.entry("id","quilt-loader-other-1.20.6"))){var p=profile();p.put(field.getKey(),field.getValue());assertThrows(java.io.IOException.class,()->QuiltProfile.parse(LauncherJson.write(p),"1.20.6","0.28.1"));}
  var p=profile();p.put("arguments",Map.of("game",List.of(),"jvm",List.of("-javaagent:x")));assertThrows(java.io.IOException.class,()->QuiltProfile.parse(LauncherJson.write(p),"1.20.6","0.28.1"));
  for(String name:List.of("../bad:loader:1","org.quiltmc:hashed:../../x"))assertThrows(IllegalArgumentException.class,()->MavenLibrary.path(name));
  assertThrows(IllegalArgumentException.class,()->MavenLibrary.simple(LauncherJson.read(LauncherJson.write(Map.of("name","org.quiltmc:quilt-loader:0.28.1","url","https://example.test/")),65536)));
 }
}
