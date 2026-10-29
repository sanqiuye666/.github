package cn.dreamingfish.dispatch.server;
import cn.dreamingfish.dispatch.server.config.ServerConfig;
import cn.dreamingfish.dispatch.server.web.ServerEndpoint;
import cn.dreamingfish.dispatch.server.wiring.ServerPortsFactory;
import cn.dreamingfish.updater.protocol.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ServerEndpointTest {
    @TempDir Path root;JsonCodec json=new JsonCodec();
    ServerEndpoint endpoint(String role){var config=ServerConfig.load(null,Map.of("SQY_SERVER_ENABLED","true","SQY_SERVER_MODE","mock","SQY_ROLE",role,"SQY_MOCK_STATE_DIR",root.resolve("state").toString(),"SQY_LOG_DIR",root.resolve("logs").toString(),"SQY_ACCOUNTS_ENABLED","true","SQY_REGISTRATION_ENABLED","true","SQY_MANAGED_PACKS_ENABLED","true","SQY_PLAYER_REPORTS_ENABLED","true"));return new ServerEndpoint(config,ServerPortsFactory.create(config),role);}
    com.fasterxml.jackson.databind.JsonNode body(Map<String,?> value){return json.read(json.write(value),com.fasterxml.jackson.databind.JsonNode.class);}
    @Test void clientCannotChooseRoleOrAnotherPlayerAndLogoutExpiresLocalSession(){
        var player=endpoint("player");
        assertEquals("AUTH_FAILED",player.handle("GET","/view",null).error().code());
        assertEquals("INVALID_DATA",player.handle("POST","/register",body(Map.of("operationId",UUID.randomUUID().toString(),"username","userone","displayName","玩家一","role","admin"))).error().code());
        var registered=player.handle("POST","/register",body(Map.of("operationId",UUID.randomUUID().toString(),"username","userone","displayName","玩家一")));
        assertNull(registered.error());assertEquals("MOCK",registered.source());assertEquals(1,registered.schemaVersion());
        assertEquals("INVALID_DATA",player.handle("POST","/report",body(Map.of("playerId",UUID.randomUUID().toString()))).error().code());
        assertEquals("FORBIDDEN",player.handle("POST","/assign",body(Map.of())).error().code());
        assertNull(player.handle("POST","/logout",body(Map.of())).error());
        assertEquals("AUTH_FAILED",player.handle("GET","/view",null).error().code());
        assertEquals("MOCK",endpoint("admin").handle("GET","/view",null).source());
    }
}
