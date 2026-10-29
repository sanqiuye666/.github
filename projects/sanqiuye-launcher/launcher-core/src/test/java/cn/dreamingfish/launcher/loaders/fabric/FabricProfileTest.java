package cn.dreamingfish.launcher.loaders.fabric;

import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FabricProfileTest {
 static Map<String,Object> profile(){return new LinkedHashMap<>(Map.of("id","fabric-loader-0.16.14-1.20.6","inheritsFrom","1.20.6","mainClass","net.fabricmc.loader.impl.launch.knot.KnotClient","arguments",Map.of("game",List.of(),"jvm",List.of("-DFabricMcEmu= net.minecraft.client.main.Main ")),"libraries",List.of(library("net.fabricmc:intermediary:1.20.6"),library("net.fabricmc:fabric-loader:0.16.14"))));}
 static Map<String,Object> library(String name){return new LinkedHashMap<>(Map.of("name",name,"url","https://maven.fabricmc.net/"));}
 @Test void officialClientProfileHasOnlyDerivedMavenPathsAndExpectedMain()throws Exception{
  var value=FabricProfile.parse(LauncherJson.write(profile()),"1.20.6","0.16.14");assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient",value.mainClass());assertEquals("net/fabricmc/intermediary/1.20.6/intermediary-1.20.6.jar",value.libraries().getFirst().path());assertEquals("https://maven.fabricmc.net/net/fabricmc/intermediary/1.20.6/intermediary-1.20.6.jar",value.libraries().getFirst().uri().toString());assertEquals(1,value.jvmArguments().size());
 }
 @Test void mismatchedInheritanceMainOrUnknownExecutableArgumentsFailBeforeDownloads()throws Exception{
  for(var field:List.of(Map.entry("inheritsFrom","1.21.1"),Map.entry("mainClass","example.Untrusted"),Map.entry("id","fabric-loader-9-1.20.6"))){var n=profile();n.put(field.getKey(),field.getValue());assertThrows(java.io.IOException.class,()->FabricProfile.parse(LauncherJson.write(n),"1.20.6","0.16.14"));}
  var n=profile();n.put("arguments",Map.of("game",List.of(),"jvm",List.of("-javaagent:untrusted.jar")));assertThrows(java.io.IOException.class,()->FabricProfile.parse(LauncherJson.write(n),"1.20.6","0.16.14"));
 }
 @Test void domainsCoordinatesDuplicatesAndInvalidDigestsAreRejected()throws Exception{
  for(var bad:List.of(Map.of("name","../outside:loader:1","url","https://maven.fabricmc.net/"),Map.of("name","net.fabricmc:fabric-loader:0.16.14","url","https://example.test/"),Map.of("name","net.fabricmc:fabric-loader:0.16.14","url","https://maven.fabricmc.net/","sha256","bad","size",1))){var n=profile();n.put("libraries",List.of(library("net.fabricmc:intermediary:1.20.6"),bad));assertThrows(java.io.IOException.class,()->FabricProfile.parse(LauncherJson.write(n),"1.20.6","0.16.14"));}
  var n=profile();n.put("libraries",List.of(library("net.fabricmc:intermediary:1.20.6"),library("net.fabricmc:fabric-loader:0.16.14"),library("net.fabricmc:fabric-loader:0.16.14")));assertThrows(java.io.IOException.class,()->FabricProfile.parse(LauncherJson.write(n),"1.20.6","0.16.14"));
 }
}
