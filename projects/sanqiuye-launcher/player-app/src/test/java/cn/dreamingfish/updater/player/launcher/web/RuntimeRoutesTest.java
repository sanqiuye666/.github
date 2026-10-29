package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.runtime.JavaRuntimeService;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeRoutesTest {
    static ApiRequest request(String method, String path, Object body) throws Exception { return new ApiRequest(method, path, Map.of(), body == null ? null : LauncherJson.read(LauncherJson.write(body), 131072)); }
    @Test void onlyExplicitPostsProbeAndDtosOmitExecutablePaths() throws Exception {
        var service = new JavaRuntimeService();
        try (var routes = new RuntimeRoutes(service, Clock.systemUTC())) {
            for (int i = 0; i < 3; i++) assertEquals(200, routes.handle(request("GET", "/runtimes", null)).orElseThrow().status());
            assertTrue(service.runtimes().isEmpty());
            assertThrows(LauncherApiException.class, () -> routes.handle(request("POST", "/runtimes", Map.of("home", System.getProperty("java.home"), "command", "evil"))));
            assertEquals(201, routes.handle(request("POST", "/runtimes", Map.of("home", System.getProperty("java.home")))).orElseThrow().status());
            var list = routes.handle(request("GET", "/runtimes", null)).orElseThrow();
            String json = new String(LauncherJson.write(list.body()), StandardCharsets.UTF_8); assertTrue(json.contains("majorVersion")); assertFalse(json.contains("executable")); assertFalse(json.contains(System.getProperty("java.home").replace("\\", "\\\\")));
            var started = routes.handle(request("POST", "/runtimes/discover", Map.of())).orElseThrow(); assertEquals(202, started.status());
            var node = LauncherJson.read(LauncherJson.write(started.body()), 131072); String id = node.path("discovery").path("id").asText();
            var current = node.path("discovery");
            for (int i = 0; i < 600 && current.path("state").asText().equals("RUNNING"); i++) { Thread.sleep(25); current = LauncherJson.read(LauncherJson.write(routes.handle(request("GET", "/runtimes/discover/" + id, null)).orElseThrow().body()), 131072).path("discovery"); }
            assertEquals("COMPLETED", current.path("state").asText()); assertTrue(current.path("runtimes").size() >= 1);
            assertThrows(LauncherApiException.class, () -> routes.handle(request("GET", "/runtimes/discover", null)));
        }
    }
}
