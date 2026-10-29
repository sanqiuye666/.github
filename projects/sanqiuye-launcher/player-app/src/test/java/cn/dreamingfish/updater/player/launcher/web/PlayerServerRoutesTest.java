package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.servers.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;import static org.junit.jupiter.api.Assertions.*;
class PlayerServerRoutesTest {
 @TempDir Path root;
 @Test void onlySavedTargetsAreProbedAndListingDoesNotProbe()throws Exception {
  var store=new ServerStore(root);var snapshot=store.read();snapshot=store.save(snapshot.revision(),null,"测试服",new ServerAddress("play.example.org",25565));var calls=new AtomicInteger();ServerProbe probe=address->{calls.incrementAndGet();assertEquals("play.example.org",address.host());return new ServerProbe.Result("MOCK",null,"2026-10-27T00:00:00Z","离线示例");};
  try(var routes=new PlayerServerRoutes(store,probe)){routes.handle(RuntimeRoutesTest.request("GET","/player-servers",null));assertEquals(0,calls.get());assertThrows(java.io.IOException.class,()->routes.handle(RuntimeRoutesTest.request("POST","/player-servers/probe",Map.of("id",UUID.randomUUID().toString()))));assertEquals(0,calls.get());routes.handle(RuntimeRoutesTest.request("POST","/player-servers/probe",Map.of("id",snapshot.servers().getFirst().id())));assertEquals(1,calls.get());assertThrows(LauncherApiException.class,()->routes.handle(RuntimeRoutesTest.request("POST","/player-servers/probe",Map.of("id",UUID.randomUUID().toString(),"host","arbitrary.example.org"))));}
 }
}
