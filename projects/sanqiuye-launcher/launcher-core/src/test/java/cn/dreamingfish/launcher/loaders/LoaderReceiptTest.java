package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.GameInstance.LoaderKind;
import cn.dreamingfish.launcher.vanilla.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LoaderReceiptTest {
 @Test void versionThreePinsActualKindAndCannotDowngradeToFabric()throws Exception{
  var component=new LoaderIdentity(LoaderKind.QUILT,"0.28.1");var files=List.of(new InstallationReceipt.FileEntry("versions/game.json",1,"a".repeat(64)),new InstallationReceipt.FileEntry(component.profilePath(),2,"b".repeat(64)));
  var receipt=new InstallationReceipt(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"1.20.6","a".repeat(40),Instant.EPOCH,new Platform("windows","10","amd64",64),21,files,null,component);
  assertEquals(3,LauncherJson.read(receipt.bytes(),65536).path("schemaVersion").asInt());assertEquals(receipt,InstallationReceipt.parse(receipt.bytes()));assertEquals(LoaderKind.QUILT,receipt.loaderKind());assertEquals("0.28.1",receipt.loaderVersion());
  assertThrows(java.io.IOException.class,()->InstallationReceipt.parse(new String(receipt.bytes(),java.nio.charset.StandardCharsets.UTF_8).replace("\"schemaVersion\":3","\"schemaVersion\":2").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  assertThrows(IllegalArgumentException.class,()->new InstallationReceipt(receipt.instanceId(),receipt.installationId(),receipt.versionId(),receipt.metadataSha1(),receipt.installedAt(),receipt.platform(),21,files));
  assertThrows(IllegalArgumentException.class,()->new LoaderIdentity(LoaderKind.QUILT,"../outside"));
 }
}
