package cn.dreamingfish.dispatch.server;

import cn.dreamingfish.dispatch.server.config.ServerConfig;
import cn.dreamingfish.dispatch.server.model.RemoteFailure;
import cn.dreamingfish.dispatch.server.wiring.ServerPortsFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {
    @TempDir Path root;
    @Test void disabledNeedsNoCredentialsAndCreatesNothing() throws Exception {
        var config=ServerConfig.load(null, Map.of());
        assertFalse(config.enabled());
        assertEquals("DISABLED",ServerPortsFactory.create(config).status().source());
        assertEquals(0,Files.list(root).count());
    }
    @Test void nativeOwnedConfigNeedsNoCommandWindowButExplicitFileStillWins() throws Exception {
        Path owned=root.resolve("server.env"),explicit=root.resolve("override.env");
        assertFalse(ServerConfig.loadForApplication(owned,Map.of()).enabled());
        Files.writeString(owned,String.join("\n",values().entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()));
        assertTrue(ServerConfig.loadForApplication(owned,Map.of()).enabled());
        Files.writeString(explicit,"SQY_SERVER_ENABLED=false\n");
        assertFalse(ServerConfig.loadForApplication(owned,Map.of("SQY_CONFIG_FILE",explicit.toString())).enabled());
        assertThrows(RemoteFailure.class,()->ServerConfig.loadForApplication(owned,Map.of("SQY_CONFIG_FILE","")));
    }
    @Test void precedenceMissingNamesAndNoSensitiveValues() throws Exception {
        Path file=root.resolve("test.env");
        Files.writeString(file,"SQY_SERVER_ENABLED=true\nSQY_SERVER_MODE=mock\nSQY_ROLE=player\nSQY_RETRY_MAX=2\n");
        var missing=assertThrows(RemoteFailure.class,()->ServerConfig.load(file,Map.of()));
        assertEquals("CONFIG_MISSING",missing.code());
        assertTrue(missing.getMessage().contains("SQY_MOCK_STATE_DIR"));
        var env=values();env.put("SQY_RETRY_MAX","0");
        var config=ServerConfig.load(file,env);
        assertEquals(0,config.retryMax());
        assertFalse(config.toString().contains(root.toString()));
        assertEquals(root.resolve("state"),config.stateRoot());
    }
    @Test void refusesBadTypesUnknownKeysRelativeOrOverlappingRoots() throws Exception {
        var blank=values();blank.put("SQY_SERVER_ENABLED","");
        assertEquals("CONFIG_INVALID",assertThrows(RemoteFailure.class,()->ServerConfig.load(null,blank)).code());
        for(var item:Map.of("SQY_RETRY_MAX","3","SQY_SERVER_ENABLED","yes","SQY_SERVER_MODE","typo","SQY_ROLE","owner","SQY_TIMEOUT_CONNECT_MS","0","SQY_MOCK_STATE_DIR","relative","SQY_UNKNOWN","secret").entrySet()) {
            var env=values();env.put(item.getKey(),item.getValue());
            assertEquals("CONFIG_INVALID",assertThrows(RemoteFailure.class,()->ServerConfig.load(null,env)).code(),item.getKey());
        }
        var overlap=values();overlap.put("SQY_LOG_DIR",root.resolve("state/logs").toString());
        assertThrows(RemoteFailure.class,()->ServerConfig.load(null,overlap));
    }
    @Test void realRequiresHttpsAndDoesNotFallBackToMock() {
        var env=values();env.put("SQY_SERVER_MODE","real");
        assertEquals("CONFIG_MISSING",assertThrows(RemoteFailure.class,()->ServerConfig.load(null,env)).code());
        env.put("SQY_TARGET_NAME","test");env.put("SQY_API_BASE_URL","https://example.invalid/api");
        var config=ServerConfig.load(null,env);
        assertEquals("REAL_NOT_IMPLEMENTED",assertThrows(RemoteFailure.class,()->ServerPortsFactory.create(config)).code());
        env.put("SQY_API_BASE_URL","https://user:secret@example.invalid/api");
        var failure=assertThrows(RemoteFailure.class,()->ServerConfig.load(null,env));
        assertFalse(failure.getMessage().contains("secret"));
    }
    Map<String,String> values() {
        return new HashMap<>(Map.of("SQY_SERVER_ENABLED","true","SQY_SERVER_MODE","mock","SQY_ROLE","player","SQY_MOCK_STATE_DIR",root.resolve("state").toString(),"SQY_LOG_DIR",root.resolve("logs").toString()));
    }
}
