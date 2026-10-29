package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.dispatch.server.config.ServerConfig;
import cn.dreamingfish.dispatch.server.web.ServerEndpoint;
import cn.dreamingfish.dispatch.server.wiring.ServerPortsFactory;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ServerRoutesTest {
    @TempDir Path root;
    @Test void existingSessionGateStillProtectsMockAndPlayerCannotForgeAdmin() throws Exception {
        var config=ServerConfig.load(null,Map.of("SQY_SERVER_ENABLED","true","SQY_SERVER_MODE","mock","SQY_ROLE","player","SQY_MOCK_STATE_DIR",root.resolve("state").toString(),"SQY_LOG_DIR",root.resolve("logs").toString(),"SQY_ACCOUNTS_ENABLED","true","SQY_REGISTRATION_ENABLED","true","SQY_MANAGED_PACKS_ENABLED","true"));
        var harness=new LauncherWebServerTest();harness.temporary=root.resolve("resources");harness.start();harness.server.close();
        try(var server=LauncherWebServer.start(0,new LauncherApi(List.of(new ServerRoutes(new ServerEndpoint(config,ServerPortsFactory.create(config),"player")))),harness.resources)){
            harness.server=server;String path="/api/launcher/v1/server/status";
            assertEquals(403,harness.request(path,"GET",null,Map.of("X-SQY-Client","launcher")).statusCode());
            var headers=harness.authorized(harness.token());var response=harness.request(path,"GET",null,headers);
            assertEquals("MOCK",LauncherJson.read(response.body(),32768).path("source").textValue());
            assertEquals(400,harness.request(path+"?role=admin","GET",null,headers).statusCode());
            var forbidden=harness.request("/api/launcher/v1/server/assign","POST","{}",headers);
            assertEquals("FORBIDDEN",LauncherJson.read(forbidden.body(),32768).path("error").path("code").textValue());
        }
    }
}
