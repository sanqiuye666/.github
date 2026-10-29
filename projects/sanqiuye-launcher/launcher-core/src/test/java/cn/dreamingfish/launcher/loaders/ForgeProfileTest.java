package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.GameInstance.LoaderKind;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class ForgeProfileTest {
 @TempDir Path root;
 static Map<String,Object> version(){
  var n=new LinkedHashMap<String,Object>();n.put("id","neoforge-20.6.139");n.put("inheritsFrom","1.20.6");n.put("mainClass","cpw.mods.bootstraplauncher.BootstrapLauncher");n.put("libraries",List.of());n.put("arguments",Map.of("jvm",List.of("-Djava.net.preferIPv6Addresses=system"),"game",List.of("--launchTarget","forgeclient","--fml.neoForgeVersion","20.6.139","--fml.mcVersion","1.20.6")));return n;
 }
 static Map<String,Object> install(){return new LinkedHashMap<>(Map.of("spec",1,"version","neoforge-20.6.139","minecraft","1.20.6","json","/version.json","libraries",List.of(),"data",Map.of("BINPATCH",Map.of("client","/data/client.lzma")),"processors",List.of(Map.of("jar","net.neoforged.installertools:binarypatcher:2.1.2","classpath",List.of(),"args",List.of("--clean","{MINECRAFT_JAR}","--apply","{BINPATCH}","--output","[net.neoforged:neoforge:20.6.139:client]")))));}
 private Path zip(Map<String,Object> profile,Map<String,Object> version,String extra)throws Exception{Path file=root.resolve(UUID.randomUUID()+".jar");try(var z=new ZipOutputStream(Files.newOutputStream(file))){for(var item:Map.of("install_profile.json",LauncherJson.write(profile),"version.json",LauncherJson.write(version),"data/client.lzma",new byte[]{1}).entrySet()){z.putNextEntry(new ZipEntry(item.getKey()));z.write(item.getValue());z.closeEntry();}if(extra!=null){z.putNextEntry(new ZipEntry(extra));z.write(1);z.closeEntry();}}return file;}
 @Test void archiveOnlyAcceptsPinnedGameCoreAndKnownOfflineClientSteps()throws Exception{
  var p=ForgeInstallerProfile.read(zip(install(),version(),null),new LoaderIdentity(LoaderKind.NEOFORGE,"20.6.139"),"1.20.6");assertEquals(1,p.processors().size());assertEquals("cpw.mods.bootstraplauncher.BootstrapLauncher",p.client().mainClass());assertTrue(p.embedded().containsKey("data/client.lzma"));
  assertThrows(java.io.IOException.class,()->ForgeInstallerProfile.read(zip(install(),version(),"../outside"),p.identity(),"1.20.6"));var bad=install();bad.put("minecraft","1.21.1");assertThrows(java.io.IOException.class,()->ForgeInstallerProfile.read(zip(bad,version(),null),p.identity(),"1.20.6"));
  var unknown=install();unknown.put("processors",List.of(Map.of("jar","evil:runner:1","classpath",List.of(),"args",List.of())));assertThrows(java.io.IOException.class,()->ForgeInstallerProfile.read(zip(unknown,version(),null),p.identity(),"1.20.6"));
 }
 @Test void injectedJvmAndGamePathsAreRejectedBeforeAnyProcessor()throws Exception{
  var n=version();n.put("arguments",Map.of("jvm",List.of("-javaagent:outside"),"game",List.of("--launchTarget","forgeclient")));assertThrows(java.io.IOException.class,()->ForgeClientProfile.parse(LauncherJson.write(n),new LoaderIdentity(LoaderKind.NEOFORGE,"20.6.139"),"1.20.6"));
  var bad=version();bad.put("arguments",Map.of("jvm",List.of(),"game",List.of("--gameDir","outside")));assertThrows(java.io.IOException.class,()->ForgeClientProfile.parse(LauncherJson.write(bad),new LoaderIdentity(LoaderKind.NEOFORGE,"20.6.139"),"1.20.6"));assertEquals("de/oceanlabs/mcp/mcp_config/1/mcp_config-1-mappings.tsrg",MavenLibrary.path("de.oceanlabs.mcp:mcp_config:1:mappings@tsrg"));
 }
}
