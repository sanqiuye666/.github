package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.*;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.launch.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.vanilla.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LoaderLaunchSupportTest {
 @TempDir Path root;
 private LaunchPlan quilt()throws Exception{
  String id=UUID.randomUUID().toString(),install=UUID.randomUUID().toString();Path base=root.resolve("instances/"+id+"/installations/"+install);var identity=new LoaderIdentity(GameInstance.LoaderKind.QUILT,"0.28.1");byte[] profile=LauncherJson.write(QuiltProfileTest.profile());var payload=new LinkedHashMap<String,byte[]>();payload.put(identity.profilePath(),profile);for(var lib:QuiltProfile.parse(profile,"1.20.6","0.28.1").libraries())payload.put("libraries/"+lib.path(),lib.path().getBytes());var entries=new ArrayList<InstallationReceipt.FileEntry>();for(var entry:payload.entrySet()){Path file=base.resolve(entry.getKey());Files.createDirectories(file.getParent());Files.write(file,entry.getValue());entries.add(new InstallationReceipt.FileEntry(entry.getKey(),entry.getValue().length,LoaderPlan.hash(entry.getValue())));}
  var receipt=new InstallationReceipt(id,install,"1.20.6","a".repeat(40),Instant.EPOCH,VanillaFixtures.WINDOWS,21,entries,null,identity);Path client=base.resolve("versions/client.jar");return new LaunchPlan(new GameInstance(id,"quilt","1.20.6",GameInstance.LoaderKind.VANILLA,root.resolve("instances/"+id+"/.minecraft")),receipt,null,null,UUID.randomUUID(),base,List.of(client),List.of(LaunchArguments.literal("-cp"),LaunchArguments.literal(client.toString()),LaunchArguments.literal("-Xmx768M")),"net.minecraft.client.main.Main",List.of(),"16",false,false,List.of());
 }
 @Test void onlyVerifiedQuiltFilesJoinClasspathAndInstanceDirectoryStaysIsolated()throws Exception{
  var before=quilt();var after=new LoaderLaunchSupport(root).apply(before,new CancellationToken());assertEquals(GameInstance.LoaderKind.QUILT,after.instance().loader());assertEquals("org.quiltmc.loader.impl.launch.knot.KnotClient",after.mainClass());assertEquals(before.instance().directory(),after.instance().directory());assertEquals(4,after.classpath().size());assertEquals(1,Collections.frequency(after.jvmArguments(),"-cp"));assertTrue(after.jvmArguments().contains("-Xmx768M"));
  var lib=before.installation().files().stream().filter(f->f.path().startsWith("libraries/")).findFirst().orElseThrow();Files.writeString(before.installationDirectory().resolve(lib.path()),"changed");assertThrows(java.io.IOException.class,()->new LoaderLaunchSupport(root).apply(before,new CancellationToken()));
 }
 @Test void neoForgeUsesControlledBootstrapAndKeepsBaseIdentityMemoryAndDirectory()throws Exception{
  var before=quilt();var id=new LoaderIdentity(GameInstance.LoaderKind.NEOFORGE,"20.6.139");byte[] bytes=LauncherJson.write(ForgeProfileTest.version());Files.write(before.installationDirectory().resolve(id.profilePath()),bytes);var entries=List.of(new InstallationReceipt.FileEntry(id.profilePath(),bytes.length,LoaderPlan.hash(bytes)));var old=before.installation();var receipt=new InstallationReceipt(old.instanceId(),old.installationId(),old.versionId(),old.metadataSha1(),old.installedAt(),old.platform(),21,entries,null,id);var base=new LaunchPlan(before.instance(),receipt,before.javaRuntime(),before.identity(),before.settingsRevision(),before.installationDirectory(),before.classpath(),before.jvmTokens(),before.mainClass(),List.of(LaunchArguments.literal("--gameDir"),LaunchArguments.literal(before.instance().directory().toString())),before.assetIndexId(),false,false,List.of());var after=new LoaderLaunchSupport(root).apply(base,new CancellationToken());assertEquals(GameInstance.LoaderKind.NEOFORGE,after.instance().loader());assertEquals("cpw.mods.bootstraplauncher.BootstrapLauncher",after.mainClass());assertEquals(base.instance().directory(),after.instance().directory());assertTrue(after.jvmArguments().contains("-Xmx768M"));assertEquals(1,Collections.frequency(after.gameArguments(),"--gameDir"));assertTrue(after.gameArguments().contains("forgeclient"));
 }
}
