package cn.dreamingfish.launcher.loaders.fabric;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.vanilla.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FabricPlannerTest {
 @TempDir Path root;
 @Test void malformedDirectoryIsReportedAsSourceFormatFailure()throws Exception{
  var f=VanillaFixtures.setup(root,"1.20.6",false,false,false);
  for(String payload:List.of("{}","[{\"loader\":{\"version\":9,\"stable\":true}}]","[{\"loader\":{\"version\":\"../bad\",\"stable\":true}}]")){
   RemoteClient remote=(r,n,t,s)->s.accept(ByteBuffer.wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   var planner=new FabricPlanner(new VanillaPlanner(f.catalog(),f.store(),VanillaFixtures.WINDOWS),f.store(),remote);
   assertEquals("SOURCE_INVALID_RESPONSE",assertThrows(TaskFailure.class,()->planner.versions("1.20.6",new CancellationToken())).code());
  }
 }
 @Test void officialVersionsAndPinnedPlanReuseCacheAndRejectChangedMetadata()throws Exception{
  var f=VanillaFixtures.setup(root,"1.20.6",false,false,false);String endpoint="https://meta.fabricmc.net/v2/versions/loader/1.20.6";
  f.responses().put(endpoint,LauncherJson.write(List.of(Map.of("loader",Map.of("version","0.16.14","stable",true),"intermediary",Map.of("version","1.20.6")))));
  f.responses().put(endpoint+"/0.16.14/profile/json",LauncherJson.write(FabricProfileTest.profile()));
  for(String artifact:List.of("intermediary","fabric-loader")){String v=artifact.equals("intermediary")?"1.20.6":"0.16.14";String url="https://maven.fabricmc.net/net/fabricmc/"+artifact+"/"+v+"/"+artifact+"-"+v+".jar";byte[] bytes=artifact.getBytes();f.responses().put(url,bytes);f.responses().put(url+".sha1",VanillaFixtures.sha1(bytes).getBytes());}
  var calls=new ArrayList<String>();RemoteClient remote=(r,max,token,sink)->{token.check();calls.add(r.uri().toString());byte[] bytes=f.responses().get(r.uri().toString());if(bytes==null||bytes.length>max)throw new IOException("fixture missing or over limit");sink.accept(ByteBuffer.wrap(bytes));};
  var planner=new FabricPlanner(new VanillaPlanner(f.catalog(),f.store(),VanillaFixtures.WINDOWS),f.store(),remote);
  assertEquals("0.16.14",planner.versions("1.20.6",new CancellationToken()).getFirst().version());
  var plan=planner.plan("1.20.6","0.16.14",new CancellationToken());assertEquals("0.16.14",plan.installation().fabricVersion());assertTrue(plan.installation().files().stream().anyMatch(v->v.relativePath().toString().replace('\\','/').equals("versions/fabric.json")));
  var pinned=planner.planPinned("1.20.6",plan.installation().metadataSha1(),"0.16.14",plan.profileSha256(),plan.fingerprint(),new CancellationToken());assertEquals(plan.fingerprint(),pinned.fingerprint());assertEquals(1,calls.stream().filter(v->v.endsWith("profile/json")).count());
  assertThrows(TaskFailure.class,()->planner.planPinned("1.20.6",plan.installation().metadataSha1(),"0.16.14",plan.profileSha256(),"a".repeat(64),new CancellationToken()));
  assertThrows(IllegalArgumentException.class,()->planner.versions("../../outside",new CancellationToken()));
 }
 @Test void metadataCacheChecksBytesAndDoesNotReplaceVerifiedObjects()throws Exception{
  RemoteClient offline=(r,n,t,s)->{throw new IOException("offline");};var store=new DownloadStore(root,offline);byte[] bytes="metadata".getBytes();String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));var spec=new DownloadSpec(RemoteService.FABRIC_META,java.net.URI.create("https://meta.fabricmc.net/v2/versions/loader/1.20.6"),bytes.length,1024,new FileDigest(FileDigest.Algorithm.SHA256,hash),"Fabric元数据");
  var result=store.rememberMetadata(spec,bytes,new CancellationToken());assertArrayEquals(bytes,store.read(result,1024,new CancellationToken()));assertEquals(result,store.fetch(spec,new CancellationToken(),n->{}));assertThrows(TaskFailure.class,()->store.rememberMetadata(spec,"changed".getBytes(),new CancellationToken()));assertArrayEquals(bytes,store.read(result,1024,new CancellationToken()));
 }
}
