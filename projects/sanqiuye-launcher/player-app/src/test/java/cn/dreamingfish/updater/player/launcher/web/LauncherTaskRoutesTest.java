package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.downloads.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LauncherTaskRoutesTest {
 @TempDir Path root;
 @Test void taskPagingAndInvalidActionsAreStructured()throws Exception{
  try(var manager=new TaskManager(root,Clock.systemUTC(),Map.of(TaskKind.INSTALLATION_VERIFY,(r,c)->c.publish(()->{})))){
   var task=manager.submit(new TaskRequest(TaskKind.INSTALLATION_VERIFY,Map.of("instanceId",UUID.randomUUID().toString())));var routes=new TaskRoutes(manager);
   var page=LauncherRouteFixtures.body(routes.handle(LauncherRouteFixtures.get("/tasks",Map.of("limit","1"))).orElseThrow());assertEquals(1,page.path("total").asInt());assertEquals(task.id().toString(),page.path("tasks").get(0).path("id").asText());
   assertEquals(200,routes.handle(LauncherRouteFixtures.post("/tasks/"+task.id()+"/cancel",Map.of())).orElseThrow().status());
   assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.get("/tasks/not-uuid",Map.of())));
   assertEquals(404,assertThrows(LauncherApiException.class,()->routes.handle(LauncherRouteFixtures.get("/tasks/"+UUID.randomUUID(),Map.of()))).status());
  }
 }
}
