package cn.dreamingfish.launcher.packs;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PackProvidersTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"),ZoneOffset.UTC);
    static final class Remote implements RemoteClient {
        final Map<String,Object> payloads=new HashMap<>();final List<RemoteRequest> calls=new ArrayList<>();
        public void get(RemoteRequest request,long limit,CancellationToken token,ChunkConsumer sink)throws IOException {
            token.check();calls.add(request);Object value=payloads.get(request.uri().getPath());if(value==null)throw new IOException("测试未定义此路径");
            if(value instanceof IOException failure)throw failure;
            byte[] bytes=LauncherJson.write(value);if(bytes.length>limit)throw new IOException("测试响应超过上限");sink.accept(java.nio.ByteBuffer.wrap(bytes));
        }
    }
    @Test void modrinthSearchIsStrictlyModpacksAndVersionsStayPublicAndInProject()throws Exception {
        var remote=new Remote();
        remote.payloads.put("/v2/search",Map.of("hits",List.of(Map.of("project_id","Pack1234","project_type","modpack","title","三秋叶世界","description","安全摘要","versions",List.of("1.21.1"),"categories",List.of("fabric"))),"total_hits",1));
        remote.payloads.put("/v2/project/Pack1234",Map.of("id","Pack1234","project_type","modpack","title","三秋叶世界","description","详情","game_versions",List.of("1.21.1"),"loaders",List.of("fabric")));
        remote.payloads.put("/v2/project/Pack1234/version",List.of(version("Listed01","Pack1234","listed","2026-10-22T00:00:00Z"),version("Private1","Pack1234","draft","2026-10-22T00:00:00Z"),version("Future01","Pack1234","listed","2027-01-01T00:00:00Z")));
        PackProvider provider=new ModrinthPackProvider(remote,clock);
        var page=provider.search(new PackQuery("三秋",0,20),new CancellationToken());assertEquals(1,page.items().size());assertEquals(PackSource.MODRINTH,page.items().getFirst().source());
        assertTrue(java.net.URLDecoder.decode(remote.calls.getFirst().uri().getRawQuery(),java.nio.charset.StandardCharsets.UTF_8).contains("project_type:modpack"));
        var detail=provider.details("Pack1234",new CancellationToken());assertEquals(List.of("Listed01"),detail.versions().stream().map(PackVersion::id).toList());
        remote.payloads.put("/v2/project/Pack1234/version",List.of(version("Other001","Other123","listed","2026-10-22T00:00:00Z")));
        assertThrows(IOException.class,()->provider.details("Pack1234",new CancellationToken()));
        int before=remote.calls.size();assertThrows(IllegalArgumentException.class,()->provider.details("../secret",new CancellationToken()));assertEquals(before,remote.calls.size());
    }
    static Map<String,Object> version(String id,String project,String state,String date){return Map.of("id",id,"project_id",project,"name",id,"status",state,"date_published",date,"game_versions",List.of("1.21.1"),"loaders",List.of("fabric"));}
    @Test void ftbDoesNotTreatCurseforgeIdsAsFtbAndFiltersPrivateVersions()throws Exception {
        var remote=new Remote();remote.payloads.put("/v1/modpacks/public/modpack/search/20",Map.of("status","success","packs",List.of(125),"curseforge",List.of(9999),"count",2));
        var visible=Map.of("id",10,"name","1.0","private",false,"released",1792627200,"targets",List.of(Map.of("type","game","name","minecraft","version","1.21.1"),Map.of("type","modloader","name","neoforge","version","21.1.1")));
        remote.payloads.put("/v1/modpacks/public/modpack/125",Map.of("status","success","id",125,"name","FTB测试包","synopsis","摘要","private",false,"versions",List.of(visible,Map.of("id",11,"name","私有","private",true,"released",1792627200))));
        var provider=new FtbPackProvider(remote,clock);var page=provider.search(new PackQuery("test",0,20),new CancellationToken());
        assertEquals(List.of("125"),page.items().stream().map(PackProject::id).toList());assertTrue(remote.calls.stream().allMatch(r->r.service()==RemoteService.FTB_API));
        assertEquals(List.of("10"),provider.details("125",new CancellationToken()).versions().stream().map(PackVersion::id).toList());
        assertEquals(1,remote.calls.stream().filter(r->r.uri().getPath().endsWith("/125")).count(),"详情缓存避免同次搜索后重复取包");
        var cancelled=new CancellationToken();cancelled.cancel();int before=remote.calls.size();assertThrows(InterruptedIOException.class,()->provider.details("125",cancelled));assertEquals(before,remote.calls.size());
        assertThrows(IllegalArgumentException.class,()->provider.details("https://127.0.0.1",new CancellationToken()));
    }
    @Test void atlauncherUsesPublicSafeNamesAndBoundedCachedList()throws Exception {
        var remote=new Remote();var version=Map.of("version","1.0","minecraft","1.20.6","published",1792627200);
        remote.payloads.put("/v1/packs/full/public",Map.of("error",false,"code",200,"data",List.of(Map.of("id",1,"name","我的世界包","safeName","TestPack","type","public","description","Line one\nLine two","versions",List.of(version)),Map.of("id",2,"name","隐藏包","safeName","PrivatePack","type","private","description","","versions",List.of(version)))));
        var provider=new AtLauncherPackProvider(remote,clock);var page=provider.search(new PackQuery("世界",0,20),new CancellationToken());assertEquals(1,page.total());assertEquals("TestPack",page.items().getFirst().id());assertEquals("NETWORK",page.provenance());
        assertEquals("CACHE",provider.search(new PackQuery("世界",0,20),new CancellationToken()).provenance());assertEquals(1,remote.calls.size());
        assertThrows(IllegalArgumentException.class,()->provider.details("../../private",new CancellationToken()));
        remote.payloads.put("/v1/pack/TestPack",Map.of("error",false,"code",200,"data",Map.of("id",1,"name","我的世界包","safeName","TestPack","type","public","description","说明","versions",List.of(version))));
        assertEquals("1.0",provider.details("TestPack",new CancellationToken()).versions().getFirst().name());
        assertEquals(RemoteService.ATLAUNCHER_API,remote.calls.getLast().service());
    }
    @Test void queriesAreBoundedAndRemoteErrorsRemainFailures()throws Exception {
        assertThrows(IllegalArgumentException.class,()->new PackQuery("x\nheader",0,20));assertThrows(IllegalArgumentException.class,()->new PackQuery("x",0,21));
        var remote=new Remote();remote.payloads.put("/v1/packs/full/public",new RemoteHttpException(429,"RATE_LIMITED",60));
        assertEquals(429,assertThrows(RemoteHttpException.class,()->new AtLauncherPackProvider(remote,clock).search(new PackQuery("",0,20),new CancellationToken())).status());assertEquals(1,remote.calls.size());
    }
    @Test void ftbRejectsMalformedPrivacyFlagsBeforeCaching()throws Exception {
        var remote=new Remote();
        remote.payloads.put("/v1/modpacks/public/modpack/125",Map.of("status","success","id",125,"name","坏目录","synopsis","","private","false","versions",List.of()));
        var provider=new FtbPackProvider(remote,clock);
        assertThrows(IOException.class,()->provider.details("125",new CancellationToken()));
        remote.payloads.put("/v1/modpacks/public/modpack/125",Map.of("status","success","id",125,"name","公开目录","synopsis","","private",false,"versions",List.of()));
        assertEquals("NETWORK",provider.details("125",new CancellationToken()).provenance());
        assertEquals(2,remote.calls.size(),"坏响应不污染详情缓存");
    }
    @Test void atlauncherMalformedVisibilityCannotBecomeCachedEmptySuccess()throws Exception {
        var remote=new Remote();var provider=new AtLauncherPackProvider(remote,clock);
        remote.payloads.put("/v1/packs/full/public",Map.of("error",false,"code",200,"data",List.of(Map.of("name","坏项目","safeName","BadPack","versions",List.of()))));
        assertThrows(IOException.class,()->provider.search(new PackQuery("",0,20),new CancellationToken()));
        remote.payloads.put("/v1/packs/full/public",Map.of("error",false,"code",200,"data",List.of(Map.of("name","公开包","safeName","GoodPack","type","public","description","","versions",List.of()))));
        var result=provider.search(new PackQuery("",0,20),new CancellationToken());assertEquals("NETWORK",result.provenance());assertEquals(1,result.total());assertEquals(2,remote.calls.size());
    }
}
