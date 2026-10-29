package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.options.GameOptionsStore;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GameOptionsRoutesTest {
    @TempDir Path root;
    @Test void sessionValidationRoundTripConflictAndUnknownFields() throws Exception {
        var harness=new LauncherWebServerTest();harness.temporary=root.resolve("resources");harness.start();harness.server.close();
        try(var server=LauncherWebServer.start(0,new LauncherApi(List.of(new GameOptionsRoutes(new GameOptionsStore(root)))),harness.resources)) {
            harness.server=server;String path="/api/launcher/v1/game-options";
            assertEquals(403,harness.request(path,"GET",null,Map.of("X-SQY-Client","launcher")).statusCode());
            var headers=harness.authorized(harness.token());var read=harness.request(path,"GET",null,headers);assertEquals(200,read.statusCode());
            var node=LauncherJson.read(read.body(),32768);assertEquals(18,node.path("fields").size());assertEquals(2,node.path("supportedVersions").size());assertFalse(node.path("enabled").booleanValue());
            String json="{\"revision\":\""+node.path("revision").asText()+"\",\"enabled\":true,\"values\":{\"fov\":90,\"modelPart_hat\":false}}";
            assertEquals(200,harness.request(path,"POST",json,headers).statusCode());
            assertEquals(409,harness.request(path,"POST",json,headers).statusCode());
            for(String values:List.of("{\"version\":1}","{\"fov\":110.5}","{\"fov\":true}","{\"mainHand\":\"evil\"}")) {
                String invalid="{\"revision\":\""+new GameOptionsStore(root).read().revision()+"\",\"enabled\":true,\"values\":"+values+"}";
                assertEquals(400,harness.request(path,"POST",invalid,headers).statusCode());
            }
            assertEquals(400,harness.request(path+"?path=elsewhere","GET",null,headers).statusCode());
            assertEquals(400,harness.request(path,"POST",json.substring(0,json.length()-1)+",\"path\":\"outside\"}",headers).statusCode());
            assertEquals(Map.of("fov",90,"modelPart_hat",false),new GameOptionsStore(root).read().values());
        }
    }
}
