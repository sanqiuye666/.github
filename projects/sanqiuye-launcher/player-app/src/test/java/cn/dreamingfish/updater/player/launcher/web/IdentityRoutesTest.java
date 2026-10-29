package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.identity.OfflineIdentitySession;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IdentityRoutesTest {
    @Test void temporaryIdentitiesSwitchWithoutPersistentAccounts() throws Exception {
        var identities = new OfflineIdentitySession(); var route = new IdentityRoutes(identities);
        assertEquals(200, route.handle(RuntimeRoutesTest.request("GET", "/identities", null)).orElseThrow().status());
        route.handle(RuntimeRoutesTest.request("POST", "/identities", Map.of("name", "Alice"))); route.handle(RuntimeRoutesTest.request("POST", "/identities", Map.of("name", "Bob")));
        var bob = identities.list().getLast(); route.handle(RuntimeRoutesTest.request("POST", "/identities/" + bob.id() + "/select", Map.of())); assertEquals(bob, identities.current().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> route.handle(RuntimeRoutesTest.request("POST", "/identities", Map.of("name", "中文"))));
        assertThrows(LauncherApiException.class, () -> route.handle(RuntimeRoutesTest.request("POST", "/identities", Map.of("name", "Alice", "password", "forbidden"))));
        route.handle(RuntimeRoutesTest.request("POST", "/identities/" + bob.id() + "/remove", Map.of())); assertTrue(identities.current().isEmpty());
        var node = LauncherJson.read(LauncherJson.write(route.handle(RuntimeRoutesTest.request("GET", "/identities", null)).orElseThrow().body()), 16384); assertEquals(1, node.path("identities").size()); assertTrue(node.path("temporary").asBoolean());
        assertTrue(new OfflineIdentitySession().list().isEmpty());
    }
}
