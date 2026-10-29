package cn.dreamingfish.dispatch.server;
import cn.dreamingfish.dispatch.server.config.ServerConfig;
import cn.dreamingfish.dispatch.server.wiring.ServerPortsFactory;
import cn.dreamingfish.dispatch.server.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CloudAccountPortTest {
 @TempDir Path root;
 private Map<String,String> config(){return new HashMap<>(Map.of("SQY_SERVER_ENABLED","true","SQY_SERVER_MODE","real","SQY_ROLE","player","SQY_TARGET_NAME","integration","SQY_API_BASE_URL","http://127.0.0.1:1/api/v1/","SQY_LOG_DIR",root.resolve("logs").toString(),"SQY_ALLOW_LOOPBACK_HTTP","true","SQY_ACCOUNTS_ENABLED","true","SQY_REGISTRATION_ENABLED","true","SQY_RETRY_MAX","0"));}
 @Test void onlyExplicitLoopbackHttpIsAllowedAndRealFailureNeverBecomesMock(){
  var env=config();env.put("SQY_ALLOW_LOOPBACK_HTTP","false");assertThrows(RemoteFailure.class,()->ServerConfig.load(null,env));env.put("SQY_ALLOW_LOOPBACK_HTTP","true");env.put("SQY_API_BASE_URL","http://example.invalid/api/v1/");assertThrows(RemoteFailure.class,()->ServerConfig.load(null,env));env.put("SQY_API_BASE_URL","http://127.0.0.1:1/api/v1/");
  var ports=ServerPortsFactory.create(ServerConfig.load(null,env));assertEquals("LIVE",ports.status().source());var failure=assertThrows(RemoteFailure.class,()->ports.panelAccounts().login(CallContext.create(Duration.ofSeconds(2)),"player","only a test password"));assertEquals("CONNECT_FAILED",failure.code());
 }
 @Test void mockAccountIsExplicitAndDoesNotPersistPassword() throws Exception {
  var env=config();env.put("SQY_SERVER_MODE","mock");env.put("SQY_MOCK_STATE_DIR",root.resolve("state").toString());var ports=ServerPortsFactory.create(ServerConfig.load(null,env));var c=CallContext.create(Duration.ofSeconds(3));var account=ports.panelAccounts().register(c,UUID.randomUUID().toString(),"mock_player","测试玩家","only a test password");assertEquals("MOCK",ports.status().source());assertEquals(account.id(),ports.panelAccounts().current(c).id());
  try(var files=Files.walk(root)){for(var file:files.filter(Files::isRegularFile).toList())assertFalse(Files.readString(file).contains("only a test password"));}
  ports.panelAccounts().logout(c);assertThrows(RemoteFailure.class,()->ports.panelAccounts().current(c));
 }
}
