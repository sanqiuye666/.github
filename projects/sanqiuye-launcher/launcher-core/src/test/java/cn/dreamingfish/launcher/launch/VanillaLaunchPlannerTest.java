package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.*;
import cn.dreamingfish.launcher.downloads.CancellationToken;
import cn.dreamingfish.launcher.identity.OfflineIdentitySession;
import cn.dreamingfish.launcher.runtime.JavaRuntime;
import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.vanilla.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VanillaLaunchPlannerTest {
    @TempDir Path temporary;
    static String hash(String algorithm,byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));}
    static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    static final String LEGACY="${auth_player_name} ${auth_session} --gameDir \"${game_directory}\" --assetsDir ${game_assets} --tweakClass net.minecraft.launchwrapper.AlphaVanillaTweaker";
    class Installed {
        final Path root,directory;
        final String id=UUID.randomUUID().toString(),installId=UUID.randomUUID().toString();
        final GameInstance instance;final ArrayList<InstallationReceipt.FileEntry> files=new ArrayList<>();
        final Platform platform=Platform.current();final int major;final Map<String,Object> metadata=new LinkedHashMap<>();
        InstallationReceipt receipt;
        Installed(String version,int major,String legacy)throws Exception {this(temporary.resolve("测试 实例 "+UUID.randomUUID()),version,major,legacy);}
        Installed(Path dataRoot,String version,int major,String legacy)throws Exception {
            this.root=dataRoot;this.major=major;instance=new GameInstance(id,"隔离实例",version,GameInstance.LoaderKind.VANILLA,root.resolve("instances/"+id+"/.minecraft"));
            directory=root.resolve("instances/"+id+"/installations/"+installId);Files.createDirectories(instance.directory());
            metadata.put("id",version);metadata.put("type","release");metadata.put("mainClass",legacy==null||version.equals("1.12.2")?"net.minecraft.client.main.Main":"net.minecraft.launchwrapper.Launch");metadata.put("javaVersion",Map.of("majorVersion",major));
            byte[] client=bytes("client"),library=bytes("regular"),nativeZip=bytes("native");
            put("versions/client.jar",client);put("libraries/g/regular.jar",library);put("libraries/g/native.jar",nativeZip);
            var artifact=Map.of("path","g/regular.jar","size",library.length,"sha1",hash("SHA-1",library));
            var nativeArtifact=Map.of("path","g/native.jar","size",nativeZip.length,"sha1",hash("SHA-1",nativeZip));
            metadata.put("downloads",Map.of("client",Map.of("size",client.length,"sha1",hash("SHA-1",client))));
            metadata.put("libraries",List.of(Map.of("name","g:regular:1","downloads",Map.of("artifact",artifact,"classifiers",Map.of("native-test",nativeArtifact)),"natives",Map.of(platform.osName(),"native-test"),"extract",Map.of("exclude",List.of("META-INF/"))),
                    Map.of("name","g:other:1","rules",List.of(Map.of("action","allow","os",Map.of("name",platform.osName().equals("windows")?"osx":"windows")))),Map.of("downloads",Map.of("artifact",artifact))));
            byte[] index=bytes("{\"objects\":{},\"virtual\":true}");put("assets/indexes/game.json",index);
            metadata.put("assetIndex",Map.of("id","test-assets","sha1",hash("SHA-1",index),"size",index.length));
            byte[] logging=bytes("logging");put("logging/client.xml",logging);
            metadata.put("logging",Map.of("client",Map.of("argument","-Dlog4j.configurationFile=${path}","file",Map.of("size",logging.length,"sha1",hash("SHA-1",logging)))));
            if(legacy==null)metadata.put("arguments",Map.of("jvm",List.of("-Djava.library.path=${natives_directory}","-cp","${classpath}"),"game",List.of("--username","${auth_player_name}","--gameDir","${game_directory}","--assetsDir","${assets_root}"),"default-user-jvm",List.of(Map.of("value",List.of("-Xms2G","-Xmx4G")))));
            else metadata.put("minecraftArguments",legacy);
            finish();
        }
        void put(String path,byte[] value)throws Exception {Path file=directory.resolve(path);Files.createDirectories(file.getParent());Files.write(file,value);files.removeIf(entry->entry.path().equals(path));files.add(new InstallationReceipt.FileEntry(path,value.length,hash("SHA-256",value)));}
        void finish()throws Exception {byte[] data=LauncherJson.write(metadata);put("versions/game.json",data);receipt=new InstallationReceipt(id,installId,instance.gameVersion(),hash("SHA-1",data),Instant.parse("2026-10-21T00:00:00Z"),platform,major,List.copyOf(files));}
        JavaRuntime java(int actual){return new JavaRuntime("a".repeat(64),Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java"),actual,platform.bits(),"测试快照");}
        LaunchPlan plan(int actual,boolean accept)throws Exception {return new VanillaLaunchPlanner(root).plan(instance,receipt,java(actual),new OfflineIdentitySession().create("Alice"),new LaunchSettingsStore.VersionedSettings(UUID.randomUUID(),new LaunchSettings(null,256,2048,960,540,List.of("-XX:+UseG1GC"),accept)),new CancellationToken());}
    }
    @Test void plansModernArgumentsWithRulesAndPinnedFiles()throws Exception {
        var fixture=new Installed("26.3",25,null);var plan=fixture.plan(25,false);
        String classpath=fixture.directory.resolve("libraries/g/regular.jar")+java.io.File.pathSeparator+fixture.directory.resolve("versions/client.jar");
        assertEquals(List.of(fixture.directory.resolve("libraries/g/regular.jar"),fixture.directory.resolve("versions/client.jar")),plan.classpath());
        int cp=plan.jvmArguments().indexOf("-cp");assertEquals(classpath,plan.jvmArguments().get(cp+1));
        assertEquals(1,plan.jvmArguments().stream().filter(arg->arg.startsWith("-Xmx")).count());assertTrue(plan.jvmArguments().contains("-Xmx2048M"));assertFalse(plan.jvmArguments().contains("-Xmx4G"));
        assertTrue(plan.jvmArguments().contains("-Dlog4j.configurationFile="+fixture.directory.resolve("logging/client.xml")));
        assertEquals("test-assets",plan.assetIndexId());assertTrue(plan.virtualAssets());assertEquals(1,plan.natives().size());
        assertEquals("Alice",plan.gameArguments().get(1));assertTrue(plan.gameArguments().contains(fixture.instance.directory().toString()));
        Path library=fixture.directory.resolve("libraries/g/regular.jar");Files.writeString(library,"changed");
        assertEquals("FILE_CHANGED",assertThrows(LaunchFailure.class,()->fixture.plan(25,false)).code());
    }
    @Test void legacyArgumentsKeepChineseSpacePathsInOneToken()throws Exception {
        for(String version:List.of("c0.0.11a","1.5.2","1.12.2")){
            String arguments=version.equals("c0.0.11a")?LEGACY:version.equals("1.5.2")?LEGACY.substring(0,LEGACY.indexOf(" --tweakClass")):"--username ${auth_player_name} --version ${version_name} --gameDir \"${game_directory}\" --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userType ${user_type} --versionType ${version_type}";
            var fixture=new Installed(version,8,arguments);var plan=fixture.plan(8,false);
            assertEquals(version.equals("1.12.2")?"net.minecraft.client.main.Main":"net.minecraft.launchwrapper.Launch",plan.mainClass());assertTrue(plan.gameArguments().contains("Alice"));
            assertEquals(fixture.instance.directory().toString(),plan.gameArguments().get(plan.gameArguments().indexOf("--gameDir")+1));
            if(version.equals("c0.0.11a"))assertEquals("net.minecraft.launchwrapper.AlphaVanillaTweaker",plan.gameArguments().getLast());
        }
    }
    @Test void newerNativeArtifactsAndCustomResolutionArePlanned()throws Exception {
        var fixture=new Installed("26.3",25,null);byte[] content=Files.readAllBytes(fixture.directory.resolve("libraries/g/native.jar"));
        fixture.metadata.put("libraries",List.of(Map.of("name","g:regular:1","downloads",Map.of("artifact",Map.of("path","g/regular.jar","sha1",hash("SHA-1",bytes("regular"))))),Map.of("name","g:native:1:natives-windows","downloads",Map.of("artifact",Map.of("path","g/native.jar","sha1",hash("SHA-1",content))))));
        fixture.metadata.put("arguments",Map.of("jvm",List.of("-Djava.library.path=${natives_directory}/java","-cp","${classpath}"),"game",List.of("--gameDir","${game_directory}",Map.of("rules",List.of(Map.of("action","allow","features",Map.of("has_custom_resolution",true))),"value",List.of("--width","${resolution_width}","--height","${resolution_height}")))));fixture.finish();
        var plan=fixture.plan(25,false);assertEquals(3,plan.classpath().size());assertEquals(1,plan.natives().size());
        assertEquals(List.of("--gameDir",fixture.instance.directory().toString(),"--width","960","--height","540"),plan.gameArguments());
    }
    @Test void javaRequirementMustMatchVerifiedMetadata()throws Exception {
        var fixture=new Installed("26.3",25,null);var before=fixture.receipt;
        fixture.receipt=new InstallationReceipt(before.instanceId(),before.installationId(),before.versionId(),before.metadataSha1(),before.installedAt(),before.platform(),8,before.files());
        assertEquals("INSTALLATION_INVALID",assertThrows(LaunchFailure.class,()->fixture.plan(8,false)).code());
    }
    @Test void rejectsChangedMetadataUnknownFormatAndUnsuitableJava()throws Exception {
        var fixture=new Installed("26.3",25,null);
        assertEquals("JAVA_INCOMPATIBLE",assertThrows(LaunchFailure.class,()->fixture.plan(21,false)).code());
        assertEquals("JAVA_INCOMPATIBLE",assertThrows(LaunchFailure.class,()->fixture.plan(26,false)).code());
        assertNotNull(fixture.plan(26,true));
        var original=fixture.receipt;
        fixture.receipt=new InstallationReceipt(original.instanceId(),original.installationId(),original.versionId(),"0".repeat(40),original.installedAt(),original.platform(),original.requiredJavaMajor(),original.files());
        assertEquals("FILE_CHANGED",assertThrows(LaunchFailure.class,()->fixture.plan(25,false)).code());fixture.receipt=original;
        fixture.metadata.put("arguments",Map.of("jvm",List.of("-cp","${classpath}"),"game",List.of("${unknown_required}")));fixture.finish();
        assertEquals("UNSUPPORTED_LAUNCH_FORMAT",assertThrows(LaunchFailure.class,()->fixture.plan(25,false)).code());
        Path file=fixture.directory.resolve("versions/game.json");byte[] before=Files.readAllBytes(file);before[0]^=1;Files.write(file,before);
        assertEquals("FILE_CHANGED",assertThrows(LaunchFailure.class,()->fixture.plan(25,false)).code());
    }
}
