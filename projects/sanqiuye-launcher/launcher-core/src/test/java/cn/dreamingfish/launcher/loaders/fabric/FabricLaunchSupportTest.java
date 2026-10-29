package cn.dreamingfish.launcher.loaders.fabric;
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
class FabricLaunchSupportTest {
 @TempDir Path root;
 private LaunchPlan plan()throws Exception{
  String id=UUID.randomUUID().toString(),installation=UUID.randomUUID().toString();Path base=root.resolve("instances/"+id+"/installations/"+installation);var files=new ArrayList<InstallationReceipt.FileEntry>();var profile=FabricProfileTest.profile();byte[] bytes=LauncherJson.write(profile);
  var targets=new LinkedHashMap<String,byte[]>();targets.put(FabricProfile.PATH,bytes);for(var lib:FabricProfile.parse(bytes,"1.20.6","0.16.14").libraries())targets.put("libraries/"+lib.path(),lib.path().getBytes());
  for(var entry:targets.entrySet()){Path file=base.resolve(entry.getKey());Files.createDirectories(file.getParent());Files.write(file,entry.getValue());String hash=HexFormat.of().formatHex(new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest().digest(entry.getValue()));files.add(new InstallationReceipt.FileEntry(entry.getKey(),entry.getValue().length,hash));}
  var receipt=new InstallationReceipt(id,installation,"1.20.6","a".repeat(40),Instant.now(),VanillaFixtures.WINDOWS,21,files,"0.16.14");Path client=base.resolve("versions/client.jar");var instance=new GameInstance(id,"test","1.20.6",GameInstance.LoaderKind.VANILLA,root.resolve("instances/"+id+"/.minecraft"));return new LaunchPlan(instance,receipt,null,null,UUID.randomUUID(),base,List.of(client),List.of(LaunchArguments.literal("-cp"),LaunchArguments.literal(client.toString()),LaunchArguments.literal("-Xmx768M")),"net.minecraft.client.main.Main",List.of(),"16",false,false,List.of());
 }
 @Test void prependsVerifiedFabricLibrariesAndChangesOnlyTheClientEntry()throws Exception{var original=plan();var result=new FabricLaunchSupport(root).apply(original,new CancellationToken());assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient",result.mainClass());assertEquals(GameInstance.LoaderKind.FABRIC,result.instance().loader());assertEquals(original.instance().directory(),result.instance().directory());assertEquals(3,result.classpath().size());assertEquals(original.classpath().getFirst(),result.classpath().getLast());assertTrue(result.jvmArguments().contains("-Xmx768M"));assertTrue(result.jvmArguments().stream().anyMatch(v->v.startsWith("-DFabricMcEmu=")));assertEquals(1,Collections.frequency(result.jvmArguments(),"-cp"));}
 @Test void changedOrMissingLibrariesFailBeforeProcessCreation()throws Exception{var original=plan();var lib=original.installation().files().stream().filter(f->f.path().startsWith("libraries/")).findFirst().orElseThrow();Files.write(original.installationDirectory().resolve(lib.path()),"tampered".getBytes());assertThrows(java.io.IOException.class,()->new FabricLaunchSupport(root).apply(original,new CancellationToken()));Files.delete(original.installationDirectory().resolve(lib.path()));assertThrows(java.io.IOException.class,()->new FabricLaunchSupport(root).apply(original,new CancellationToken()));}
}
