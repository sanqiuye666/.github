package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.downloads.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeLayoutTest {
    @TempDir Path temporary;
    VanillaLaunchPlannerTest.Installed fixture(Path root,String version)throws Exception {
        var owner=new VanillaLaunchPlannerTest();owner.temporary=temporary;
        var data=owner.new Installed(root,version,8,VanillaLaunchPlannerTest.LEGACY);
        byte[] nativeJar=NativeExtractorTest.zip(Map.of("native.dll",new byte[]{1,2,3},"META-INF/MANIFEST.MF",new byte[]{4}));
        data.put("libraries/g/native.jar",nativeJar);
        data.metadata.put("libraries",List.of(Map.of("name","g:regular:1","downloads",Map.of("artifact",Map.of("path","g/regular.jar","sha1",VanillaLaunchPlannerTest.hash("SHA-1",VanillaLaunchPlannerTest.bytes("regular"))),"classifiers",Map.of("native-test",Map.of("path","g/native.jar","sha1",VanillaLaunchPlannerTest.hash("SHA-1",nativeJar)))),"natives",Map.of(data.platform.osName(),"native-test"),"extract",Map.of("exclude",List.of("META-INF/")))));
        byte[] asset=VanillaLaunchPlannerTest.bytes("资源文件");String hash=VanillaLaunchPlannerTest.hash("SHA-1",asset),path=hash.substring(0,2)+"/"+hash;
        data.put("assets/objects/"+path,asset);data.put("assets/virtual/test-assets/sound/test.ogg",asset);data.put("resources/sound/test.ogg",asset);
        byte[] index=cn.dreamingfish.launcher.storage.LauncherJson.write(Map.of("objects",Map.of("sound/test.ogg",Map.of("hash",hash,"size",asset.length)),"virtual",true,"map_to_resources",true));
        data.put("assets/indexes/game.json",index);data.metadata.put("assetIndex",Map.of("id","test-assets","sha1",VanillaLaunchPlannerTest.hash("SHA-1",index)));data.finish();return data;
    }
    @Test void isolatesResourcesWithoutWritableSharedLinks()throws Exception {
        Path root=temporary.resolve("同一数据根");var first=fixture(root,"c0.0.11a");var second=fixture(root,"1.5.2");
        Path settings=second.instance.directory().resolve("options.txt"),save=second.instance.directory().resolve("saves/保留/level.dat");Files.createDirectories(save.getParent());Files.writeString(settings,"独立设置");Files.writeString(save,"独立存档");
        Path cache=root.resolve("downloads/objects/sentinel");Files.createDirectories(cache.getParent());Files.writeString(cache,"原缓存");
        var layout=new RuntimeLayout(root);var one=layout.prepare(first.plan(8,false),UUID.randomUUID(),new CancellationToken());var two=layout.prepare(second.plan(8,false),UUID.randomUUID(),new CancellationToken());
        assertEquals(first.instance.directory(),one.gameDirectory());assertNotEquals(one.nativesDirectory(),two.nativesDirectory());
        assertTrue(Files.exists(one.assetsDirectory().resolve("indexes/test-assets.json")));assertTrue(Files.exists(first.instance.directory().resolve("resources/sound/test.ogg")));
        assertTrue(one.command().contains(one.assetsDirectory().resolve("virtual/test-assets").toString()));assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(one.nativesDirectory().resolve("native.dll")));
        assertEquals("独立设置",Files.readString(settings));assertEquals("独立存档",Files.readString(save));assertEquals("原缓存",Files.readString(cache));
        for(var file:first.receipt.files())assertEquals(file.sha256(),VanillaLaunchPlannerTest.hash("SHA-256",Files.readAllBytes(first.directory.resolve(file.path()))));
        Path projected=one.assetsDirectory().resolve("virtual/test-assets/sound/test.ogg");Files.writeString(projected,"手动修改");
        assertEquals("FILE_CHANGED",assertThrows(LaunchFailure.class,()->layout.prepare(first.plan(8,false),UUID.randomUUID(),new CancellationToken())).code());
        assertEquals("手动修改",Files.readString(projected));assertEquals("独立存档",Files.readString(save));
    }
    @Test void cancellationNeverPublishesSingleLaunchNatives()throws Exception {
        Path root=temporary.resolve("取消准备");var fixture=fixture(root,"c0.0.11a");var token=new CancellationToken();UUID id=UUID.randomUUID();
        var extractor=new NativeExtractor(){@Override public void extract(List<LaunchPlan.NativeArchive> archives,Path staging,CancellationToken cancellation)throws java.io.IOException{super.extract(archives,staging,cancellation);cancellation.cancel();}};
        assertThrows(java.io.InterruptedIOException.class,()->new RuntimeLayout(root,extractor).prepare(fixture.plan(8,false),id,token));
        assertFalse(Files.exists(root.resolve("instances/"+fixture.id+"/launches/"+id+"/natives")));
    }
}
