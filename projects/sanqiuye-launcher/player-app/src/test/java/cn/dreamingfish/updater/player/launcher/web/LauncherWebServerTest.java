package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LauncherWebServerTest {
    @TempDir Path temporary;
    LauncherWebServer server;
    URLClassLoader resources;
    final AtomicInteger calls = new AtomicInteger();
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    @BeforeEach void start() throws Exception {
        Path directory = temporary.resolve("cn/dreamingfish/updater/player/launcher/web/ui");
        Files.createDirectories(directory.resolve("assets"));
        Files.writeString(directory.resolve("index.html"), "<main>三秋叶测试</main>");
        Files.writeString(directory.resolve("assets/app.js"), "console.log('fixture');");
        Files.writeString(directory.resolve("build-info.json"), "{\"outputs\":{\"index.html\":\"fixture\",\"assets/app.js\":\"fixture\"}}");
        resources = new URLClassLoader(new URL[]{temporary.toUri().toURL()}, null);
        server = createServer();
    }
    LauncherWebServer createServer() throws Exception {
        return LauncherWebServer.start(0, new LauncherApi(List.of(request -> {
            if(request.path().equals("/limited"))return Optional.of(new ApiResponse(429,Map.of("error",Map.of("code","RATE_LIMITED","message","等待","retryAfterSeconds",7))));
            if (!request.path().equals("/ping")) return Optional.empty();
            request.onlyFields(Set.of("name"));
            calls.incrementAndGet();
            return Optional.of(new ApiResponse(200, Map.of("ok", true)));
        })), resources);
    }
    @AfterEach void stop() throws Exception { server.close(); resources.close(); }
    HttpResponse<byte[]> request(String path, String method, String body, Map<String,String> headers) throws Exception {
        var builder = HttpRequest.newBuilder(server.baseUri().resolve(path)).timeout(Duration.ofSeconds(4));
        headers.forEach(builder::header);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    String token() throws Exception {
        var response = request("/api/launcher/v1/session", "GET", null, Map.of("X-SQY-Client", "launcher"));
        assertEquals(200, response.statusCode());
        return LauncherJson.read(response.body(), 16384).path("sessionToken").asText();
    }
    Map<String,String> authorized(String token) { return Map.of("X-SQY-Client","launcher","X-SQY-Session",token,"Content-Type","application/json"); }
    @Test void rateLimitHasRetryAfterHeaderAndStructuredBody()throws Exception{
        var response=request("/api/launcher/v1/limited","GET",null,authorized(token()));
        assertEquals(429,response.statusCode());assertEquals("7",response.headers().firstValue("Retry-After").orElse(""));
        assertEquals("RATE_LIMITED",LauncherJson.read(response.body(),16384).path("error").path("code").asText());
    }

    @Test void bootstrapRejectsForeignRequests() throws Exception {
        for (var headers : List.of(Map.<String,String>of(), Map.of("X-SQY-Client","launcher","Origin","https://example.invalid"),
                Map.of("X-SQY-Client","launcher","Sec-Fetch-Site","cross-site"),
                Map.of("X-SQY-Client","launcher","Sec-Fetch-Site","same-site"))) {
            var response = request("/api/launcher/v1/session","GET",null,headers);
            assertEquals(403,response.statusCode());
            assertFalse(LauncherJson.read(response.body(),16384).has("sessionToken"));
        }
        assertEquals(0,calls.get());
    }
    @Test void rejectsWrongAndDuplicateHost() throws Exception {
        assertEquals(403,rawStatus("Host: attacker.invalid\r\n"));
        assertEquals(400,rawStatus("Host: attacker.invalid\r\nHost: 127.0.0.1\r\n"));
    }
    int rawStatus(String host) throws Exception {
        try (var socket = new Socket("127.0.0.1",server.baseUri().getPort())) {
            socket.setSoTimeout(4000);
            socket.getOutputStream().write(("GET /api/launcher/v1/session HTTP/1.1\r\n"+host+"X-SQY-Client: launcher\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String line = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII)).readLine();
            return Integer.parseInt(line.split(" ")[1]);
        }
    }
    @Test void requiresCurrentSessionAndRejectsUrlToken() throws Exception {
        assertEquals(403,request("/api/launcher/v1/ping","GET",null,Map.of("X-SQY-Client","launcher")).statusCode());
        assertEquals(403,request("/api/launcher/v1/ping","GET",null,authorized("wrong")).statusCode());
        String token = token();
        assertEquals(403,request("/api/launcher/v1/ping?token="+token,"GET",null,Map.of("X-SQY-Client","launcher")).statusCode());
        assertEquals(200,request("/api/launcher/v1/ping","GET",null,authorized(token)).statusCode());
        assertEquals(1,calls.get());
    }
    @Test void restartInvalidatesOldToken() throws Exception {
        String old = token(); server.close(); server = createServer();
        assertEquals(403,request("/api/launcher/v1/ping","GET",null,authorized(old)).statusCode());
    }
    @Test void assetsCannotEscapeAndAreNotEmbeddable() throws Exception {
        var home = request("/","GET",null,Map.of());
        assertEquals(200,home.statusCode());
        assertTrue(new String(home.body(),StandardCharsets.UTF_8).contains("三秋叶"));
        assertEquals("DENY",home.headers().firstValue("X-Frame-Options").orElseThrow());
        assertTrue(home.headers().firstValue("Content-Security-Policy").orElseThrow().contains("frame-ancestors 'none'"));
        assertTrue(home.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        for (String path : List.of("/missing","/%2e%2e/secret","/assets%2fapp.js","/C:/Windows/win.ini")) {
            assertTrue(request(path,"GET",null,Map.of()).statusCode()>=400,path);
        }
        assertEquals(200,request("/assets/app.js","GET",null,Map.of()).statusCode());
        assertEquals(0,request("/assets/app.js","HEAD",null,Map.of()).body().length);
    }
    @Test void boundedRequestsAndStrictJson() throws Exception {
        var headers = authorized(token());
        assertEquals(413,request("/api/launcher/v1/ping","POST"," ".repeat(16385),headers).statusCode());
        assertEquals(400,request("/api/launcher/v1/ping?a=1&a=2","GET",null,headers).statusCode());
        for(String json : List.of("{\"name\":\"a\",\"name\":\"b\"}","{} {}","{\"url\":\"https://example.invalid\"}"))
            assertEquals(400,request("/api/launcher/v1/ping","POST",json,headers).statusCode());
        assertEquals(405,request("/api/launcher/v1/ping","OPTIONS",null,headers).statusCode());
        assertEquals(404,request("/api/launcher/v1/no-route","GET",null,headers).statusCode());
        assertEquals(0,calls.get());
    }
}
