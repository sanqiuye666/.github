package cn.dreamingfish.launcher.loaders.fabric;
import cn.dreamingfish.launcher.vanilla.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FabricReceiptTest {
 @Test void vanillaV1AndFabricV2AreExplicitAndRoundTrip()throws Exception{
  var common=new InstallationReceipt(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"1.20.6","a".repeat(40),Instant.now(),VanillaFixtures.WINDOWS,21,List.of(new InstallationReceipt.FileEntry("versions/game.json",1,"b".repeat(64))));assertEquals(1,LauncherJson.read(common.bytes(),4096).path("schemaVersion").intValue());assertEquals(common,InstallationReceipt.parse(common.bytes()));
  var files=new ArrayList<>(common.files());files.add(new InstallationReceipt.FileEntry("versions/fabric.json",1,"c".repeat(64)));var fabric=new InstallationReceipt(common.instanceId(),common.installationId(),common.versionId(),common.metadataSha1(),common.installedAt(),common.platform(),common.requiredJavaMajor(),files,"0.16.14");var json=LauncherJson.read(fabric.bytes(),4096);assertEquals(2,json.path("schemaVersion").intValue());assertEquals("0.16.14",json.path("fabricVersion").textValue());assertEquals(fabric,InstallationReceipt.parse(fabric.bytes()));
  var downgraded=(com.fasterxml.jackson.databind.node.ObjectNode)json.deepCopy();downgraded.put("schemaVersion",1);assertThrows(java.io.IOException.class,()->InstallationReceipt.parse(LauncherJson.write(downgraded)));
  assertThrows(IllegalArgumentException.class,()->new InstallationReceipt(common.instanceId(),common.installationId(),common.versionId(),common.metadataSha1(),common.installedAt(),common.platform(),21,common.files(),"0.16.14"));
 }
}
