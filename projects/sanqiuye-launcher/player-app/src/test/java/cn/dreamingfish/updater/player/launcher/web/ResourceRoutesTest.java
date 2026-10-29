package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.resources.*;import cn.dreamingfish.launcher.storage.LauncherJson;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class ResourceRoutesTest {
 @TempDir Path root;
 @Test void rejectsUnknownKindAndFieldsWithoutOpeningFiles()throws Exception {try(var routes=new ResourceRoutes(new ResourceStore(root))){assertThrows(IllegalArgumentException.class,()->routes.handle(new ApiRequest("GET","/resources",Map.of("instanceId",UUID.randomUUID().toString(),"kind","../mods"),null)));assertThrows(LauncherApiException.class,()->routes.handle(new ApiRequest("POST","/resources/import",Map.of(),LauncherJson.read("{\"instanceId\":\"bad\",\"kind\":\"RESOURCEPACKS\",\"source\":\"x\",\"target\":\"arbitrary\"}".getBytes(),1000))));assertTrue(routes.handle(new ApiRequest("GET","/unrelated",Map.of(),null)).isEmpty());}}
}
