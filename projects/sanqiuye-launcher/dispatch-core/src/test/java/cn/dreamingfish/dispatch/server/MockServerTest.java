package cn.dreamingfish.dispatch.server;

import cn.dreamingfish.dispatch.server.config.ServerConfig;
import cn.dreamingfish.dispatch.server.model.*;
import cn.dreamingfish.dispatch.server.model.ServerModels.*;
import cn.dreamingfish.dispatch.server.wiring.ServerPortsFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class MockServerTest {
    @TempDir Path root;
    ServerConfig config(String role,String scenario) {
        return ServerConfig.load(null,Map.of("SQY_SERVER_ENABLED","true","SQY_SERVER_MODE","mock","SQY_ROLE",role,"SQY_MOCK_STATE_DIR",root.resolve("state").toString(),"SQY_LOG_DIR",root.resolve("logs").toString(),"SQY_REGISTRATION_ENABLED","true","SQY_ACCOUNTS_ENABLED","true","SQY_MANAGED_PACKS_ENABLED","true","SQY_PLAYER_REPORTS_ENABLED","true","SQY_MOCK_SCENARIO",scenario));
    }
    CallContext call() {return CallContext.create(Duration.ofSeconds(5));}
    @Test void separateClientsShareAccountPublishAssignmentAndReportWithoutTouchingOwnInstances() throws Exception {
        var player=ServerPortsFactory.create(config("player","success"));
        var admin=ServerPortsFactory.create(config("admin","success"));
        var profile=player.sessions().register(call(),UUID.randomUUID().toString(),"tester","测试玩家");
        assertEquals("default",profile.groupId());
        assertEquals(profile,player.sessions().login(call(),"tester"));
        var actor=Actor.player(profile.id());var manager=Actor.admin();
        assertEquals(profile.id(),admin.directory().listPlayers(call(),manager).getFirst().id());
        var pack=admin.packAdmin().beginUpload(call(),manager,UUID.randomUUID().toString(),new Upload("测试整合包","demo.zip",4096,"1.20.6","Fabric"));
        assertEquals("PREVIEW",pack.status());
        assertEquals(0,player.packs().list(call(),actor).size());
        var published=admin.publish().activate(call(),manager,UUID.randomUUID().toString(),pack.id(),pack.revision());
        assertEquals("PUBLISHED",published.status());
        admin.packAdmin().assign(call(),manager,UUID.randomUUID().toString(),published.id(),published.revision(),"group","default");
        assertEquals("MOCK",player.content().readManifest(call(),actor,pack.id()).source());
        assertEquals(1,player.packs().list(call(),actor).size());
        player.profiles().reportManagedInstance(call(),actor,UUID.randomUUID().toString(),pack.id(),"UPDATED");
        assertEquals("UPDATED",admin.directory().listPlayers(call(),manager).getFirst().report().status());
        var current=admin.packAdmin().list(call(),manager).getFirst();
        var deleted=admin.packAdmin().delete(call(),manager,UUID.randomUUID().toString(),current.id(),current.revision(),current.name());
        assertEquals("DELETED",deleted.status());assertTrue(player.packs().list(call(),actor).isEmpty());
        admin.packAdmin().restore(call(),manager,UUID.randomUUID().toString(),deleted.id(),deleted.revision());
        assertEquals(0,player.packs().list(call(),actor).size(),"恢复保留区记录不自动重新授权");
        var restored=admin.packAdmin().list(call(),manager).getFirst();
        assertEquals("UNLISTED",restored.status());assertTrue(restored.assignments().isEmpty());
        String state=Files.readString(root.resolve("state/state.json"));
        assertFalse(state.contains("password"));assertFalse(state.contains("accessToken"));
        assertCode("FEATURE_DISABLED",()->admin.runtime().inspect(call(),manager));
        assertCode("FEATURE_DISABLED",()->admin.minecraft().status(call(),manager));
        assertEquals("MOCK",admin.release().backup(call(),manager,UUID.randomUUID().toString()).source());
    }
    @Test void roleFlagsRevisionIdempotencyAndPersonalAssignmentCannotBeBypassed() {
        var admin=ServerPortsFactory.create(config("admin","success"));var player=ServerPortsFactory.create(config("player","success"));
        var profile=player.sessions().register(call(),UUID.randomUUID().toString(),"userone","玩家一");
        var other=player.sessions().register(call(),UUID.randomUUID().toString(),"usertwo","玩家二");
        var actor=Actor.player(profile.id());var manager=Actor.admin();String operation=UUID.randomUUID().toString();
        var upload=new Upload("专用包","private.zip",200,"1.20.6","原版");
        var pack=admin.packAdmin().beginUpload(call(),manager,operation,upload);
        assertEquals(pack,admin.packAdmin().beginUpload(call(),manager,operation,upload));
        assertCode("CONFLICT",()->admin.packAdmin().beginUpload(call(),manager,operation,new Upload("另一包","private.zip",200,"1.20.6","原版")));
        assertCode("FORBIDDEN",()->player.packAdmin().list(call(),manager));
        assertCode("FORBIDDEN",()->admin.packAdmin().list(call(),actor));
        assertCode("CONFLICT",()->admin.publish().activate(call(),manager,UUID.randomUUID().toString(),pack.id(),99));
        var published=admin.publish().activate(call(),manager,UUID.randomUUID().toString(),pack.id(),pack.revision());
        admin.packAdmin().assign(call(),manager,UUID.randomUUID().toString(),published.id(),published.revision(),"player",profile.id());
        assertCode("FORBIDDEN",()->player.content().readManifest(call(),Actor.player(other.id()),pack.id()));
        assertCode("INVALID_DATA",()->admin.packAdmin().beginUpload(call(),manager,UUID.randomUUID().toString(),new Upload("危险","../evil.zip",200,"1.20.6","原版")));
        var now=admin.packAdmin().list(call(),manager).getFirst();
        assertCode("CONFIRMATION_REQUIRED",()->admin.packAdmin().delete(call(),manager,UUID.randomUUID().toString(),now.id(),now.revision(),""));
        assertCode("CANCELLED",()->admin.directory().listPlayers(new CallContext(UUID.randomUUID().toString(),Instant.now().plusSeconds(5),()->true),manager));
        assertCode("CONNECT_FAILED",()->admin.directory().listPlayers(new CallContext(UUID.randomUUID().toString(),Instant.now().minusSeconds(1),()->false),manager));
    }
    @Test void errorsAreStructuredAndLogsDoNotContainInputs() throws Exception {
        for(var scenario:Map.of("connect-failed","CONNECT_FAILED","auth-failed","AUTH_FAILED","server-error","SERVER_ERROR","invalid-data","INVALID_DATA","conflict","CONFLICT").entrySet()) {
            var port=ServerPortsFactory.create(config("admin",scenario.getKey()));
            assertCode(scenario.getValue(),()->port.directory().listPlayers(call(),Actor.admin()));
        }
        var disabled=ServerPortsFactory.create(ServerConfig.load(null,Map.of()));
        assertCode("FEATURE_DISABLED",()->disabled.directory().listPlayers(call(),Actor.admin()));
        var logs=Files.readString(root.resolve("logs/server-calls.jsonl"));
        assertFalse(logs.contains(root.toString()));assertFalse(logs.contains("password"));
        assertTrue(logs.contains("requestId"));assertTrue(logs.contains("durationMs"));assertTrue(logs.contains("AUTH_FAILED"));
    }
    @Test void concurrentClientsDoNotLoseWritesAndCorruptStateIsPreserved() throws Exception {
        var pool=Executors.newFixedThreadPool(4);
        try {
            var futures=new ArrayList<Future<?>>();
            for(int i=0;i<12;i++){int n=i;futures.add(pool.submit(()->ServerPortsFactory.create(config("player","success")).sessions().register(call(),UUID.randomUUID().toString(),"player"+n,"玩家"+n)));}
            for(var future:futures)future.get(10,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        var admin=ServerPortsFactory.create(config("admin","success"));
        assertEquals(12,admin.directory().listPlayers(call(),Actor.admin()).size());
        var path=root.resolve("state/state.json");Files.writeString(path,"broken-state");
        assertCode("INVALID_DATA",()->admin.directory().listPlayers(call(),Actor.admin()));
        assertEquals("broken-state",Files.readString(path));
    }
    static void assertCode(String code,org.junit.jupiter.api.function.Executable action) {assertEquals(code,assertThrows(RemoteFailure.class,action).code());}
}
