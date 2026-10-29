package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.vanilla.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class QuiltPlannerTest {
 @TempDir Path root;
 @Test void unsortedOfficialVersionsAndFixedPlansKeepActualQuiltIdentity()throws Exception{
  var f=VanillaFixtures.setup(root,"1.20.6",false,false,false);String api="https://meta.quiltmc.org/v3/versions/loader/1.20.6";
  f.responses().put(api,LauncherJson.write(List.of(Map.of("loader",Map.of("version","0.26.0"),"hashed",Map.of("version","1.20.6")),Map.of("loader",Map.of("version","0.28.1"),"hashed",Map.of("version","1.20.6")))));
  byte[] profile=LauncherJson.write(QuiltProfileTest.profile());f.responses().put(api+"/0.28.1/profile/json",profile);
  for(var lib:QuiltProfile.parse(profile,"1.20.6","0.28.1").libraries()){byte[] bytes=lib.path().getBytes();f.responses().put(lib.uri().toString(),bytes);f.responses().put(lib.uri()+".sha1",VanillaFixtures.sha1(bytes).getBytes());}
  RemoteClient remote=(r,n,t,s)->{t.check();byte[] bytes=f.responses().get(r.uri().toString());if(bytes==null)throw new java.io.IOException("unexpected fixture");s.accept(ByteBuffer.wrap(bytes));};var planner=new QuiltProvider(new VanillaPlanner(f.catalog(),f.store(),VanillaFixtures.WINDOWS),f.store(),remote);
  assertEquals("0.28.1",planner.versions("1.20.6",new CancellationToken()).getFirst().version());var plan=planner.plan("1.20.6","0.28.1",new CancellationToken());assertEquals(cn.dreamingfish.launcher.GameInstance.LoaderKind.QUILT,plan.installation().component().kind());assertNull(plan.installation().fabricVersion());assertEquals(plan.fingerprint(),planner.pinned("1.20.6",plan.installation().metadataSha1(),"0.28.1",plan.sourceSha256(),plan.fingerprint(),new CancellationToken()).fingerprint());
  assertThrows(TaskFailure.class,()->planner.pinned("1.20.6",plan.installation().metadataSha1(),"0.28.1",plan.sourceSha256(),"a".repeat(64),new CancellationToken()));
 }
}
