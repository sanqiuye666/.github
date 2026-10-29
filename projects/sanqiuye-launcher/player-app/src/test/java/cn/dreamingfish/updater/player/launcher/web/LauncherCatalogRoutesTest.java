package cn.dreamingfish.updater.player.launcher.web;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static cn.dreamingfish.updater.player.launcher.web.LauncherRouteFixtures.*;

class LauncherCatalogRoutesTest {
    @TempDir Path root;
    @Test void allFourTypesSurvivePagination() throws Exception {
        var session=new CatalogSession(catalog(root,new AtomicReference<>(manifest(""))));
        var routes=new CatalogRoutes(session);
        var first=body(routes.handle(get("/catalog",Map.of())).orElseThrow());
        assertEquals(205,first.path("total").intValue());
        assertEquals(52,first.path("typeCounts").path("release").intValue());
        assertEquals(51,first.path("typeCounts").path("old_beta").intValue());
        var actual=new ArrayList<String>();
        for(int offset=0;offset<205;offset+=50) {
            var page=body(routes.handle(get("/catalog",Map.of("offset",""+offset,"limit","50","snapshotId",first.path("snapshotId").asText()))).orElseThrow());
            page.path("items").forEach(item->actual.add(item.path("id").asText()));
        }
        var expected=new ArrayList<String>();for(int i=0;i<205;i++)expected.add(String.format("v%03d",i));
        assertEquals(expected,actual);
        var beta=body(routes.handle(get("/catalog",Map.of("type","old_beta"))).orElseThrow());
        assertEquals(51,beta.path("filteredTotal").intValue());
        assertEquals(1,body(routes.handle(get("/catalog",Map.of("q","v204"))).orElseThrow()).path("filteredTotal").intValue());
    }
    @Test void changedSnapshotDoesNotMixPages() throws Exception {
        var response=new AtomicReference<>(manifest(""));
        var session=new CatalogSession(catalog(root,response)); var routes=new CatalogRoutes(session);
        String id=session.snapshot(false).id(); response.set(manifest("-new"));
        routes.handle(post("/catalog/refresh",Map.of())).orElseThrow();
        var error=assertThrows(LauncherApiException.class,()->routes.handle(get("/catalog",Map.of("offset","50","snapshotId",id))));
        assertEquals(409,error.status());assertEquals("CATALOG_CHANGED",error.code());
    }
    @Test void cacheFallbackIsVisibleAndCancellationNeverReturnsPage() throws Exception {
        var response=new AtomicReference<>(manifest(""));var routes=new CatalogRoutes(new CatalogSession(catalog(root,response)));
        routes.handle(get("/catalog",Map.of()));response.set(null);
        var fallback=body(routes.handle(post("/catalog/refresh",Map.of())).orElseThrow());
        assertEquals("STALE_CACHE",fallback.path("source").asText());assertTrue(fallback.path("warning").asText().contains("controlled offline"));
        try { Thread.currentThread().interrupt();assertThrows(IOException.class,()->routes.handle(get("/catalog",Map.of()))); }
        finally {Thread.interrupted();}
    }
    @Test void rejectsInvalidPagingAndUnknownFields() throws Exception {
        var routes=new CatalogRoutes(new CatalogSession(catalog(root,new AtomicReference<>(manifest("")))));
        for(var query:List.of(Map.of("offset","-1"),Map.of("limit","101"),Map.of("offset","50"),Map.of("url","x"),Map.of("type","fabric")))
            assertEquals(400,assertThrows(LauncherApiException.class,()->routes.handle(get("/catalog",query))).status());
    }
}
