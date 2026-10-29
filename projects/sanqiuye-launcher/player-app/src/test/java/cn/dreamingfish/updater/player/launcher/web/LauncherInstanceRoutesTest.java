package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.instances.FileInstanceCatalog;
import cn.dreamingfish.launcher.storage.LauncherStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static cn.dreamingfish.updater.player.launcher.web.LauncherRouteFixtures.*;

class LauncherInstanceRoutesTest {
    @TempDir Path root;
    @Test void persistsChineseInstanceAndReportsBrokenRecords() throws Exception {
        var session=new CatalogSession(catalog(root,new AtomicReference<>(manifest(""))));
        var routes=new InstanceRoutes(new FileInstanceCatalog(root,CLOCK),session);
        String snapshot=session.snapshot(false).id();
        var created=routes.handle(post("/instances",Map.of("name","三秋叶测试","versionId","v000","snapshotId",snapshot))).orElseThrow();
        assertEquals(201,created.status());var instance=body(created).path("instance");assertEquals("PREPARED",instance.path("state").asText());
        Path broken=root.resolve("instances").resolve(UUID.randomUUID().toString());Files.createDirectories(broken);Files.writeString(broken.resolve("instance.json"),"bad");
        var reopened=new InstanceRoutes(new FileInstanceCatalog(root,CLOCK),session);
        var list=body(reopened.handle(get("/instances",Map.of())).orElseThrow());
        assertEquals(1,list.path("instances").size());assertEquals(1,list.path("problems").size());
        assertEquals(instance.path("id"),list.path("instances").get(0).path("id"));assertTrue(Files.exists(broken.resolve("instance.json")));
    }
    @Test void unknownVersionsInputAndBusyDirectoryDoNotTouchOtherFiles() throws Exception {
        var session=new CatalogSession(catalog(root,new AtomicReference<>(manifest(""))));
        var routes=new InstanceRoutes(new FileInstanceCatalog(root,CLOCK),session);String id=session.snapshot(false).id();
        Path sentinel=root.resolve("keep.txt");Files.writeString(sentinel,"untouched");
        var invalid=assertThrows(LauncherApiException.class,()->routes.handle(post("/instances",Map.of("name","x","versionId","../outside","snapshotId",id))));
        assertEquals("UNKNOWN_VERSION",invalid.code());
        assertThrows(LauncherApiException.class,()->routes.handle(post("/instances",Map.of("name","x","versionId","v000","snapshotId",id,"directory","../outside"))));
        try(var lock=new LauncherStorage(root).lock(Path.of("instances/.catalog.lock"))) {
            var busy=assertThrows(LauncherApiException.class,()->routes.handle(post("/instances",Map.of("name","x","versionId","v000","snapshotId",id))));
            assertEquals(409,busy.status());assertEquals("BUSY",busy.code());
        }
        assertEquals("untouched",Files.readString(sentinel));assertTrue(new FileInstanceCatalog(root,CLOCK).listing().instances().isEmpty());
    }
}
