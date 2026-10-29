package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.identity.OfflineIdentitySession;
import cn.dreamingfish.launcher.instances.FileInstanceCatalog;
import cn.dreamingfish.launcher.launch.*;
import cn.dreamingfish.launcher.runtime.JavaRuntimeService;
import cn.dreamingfish.launcher.storage.*;
import cn.dreamingfish.launcher.vanilla.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LaunchRoutesTest {
    @TempDir Path root;
    final Clock clock = Clock.systemUTC();
    final String instance = UUID.randomUUID().toString(), installation = UUID.randomUUID().toString();
    final JavaRuntimeService runtimes = new JavaRuntimeService(); final OfflineIdentitySession identities = new OfflineIdentitySession();
    static String hash(String algorithm, byte[] data) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data)); }
    void fixture() throws Exception {
        var storage = new LauncherStorage(root); Path base = Path.of("instances", instance); storage.ensureDirectory(base.resolve(".minecraft"));
        storage.writeAtomically(base.resolve("instance.json"), LauncherJson.write(Map.of("schemaVersion", 1, "id", instance, "name", "接口测试", "gameVersion", "test", "loader", "VANILLA", "createdAt", clock.instant().toString(), "state", "PREPARED")));
        byte[] client = "not-a-real-game".getBytes(StandardCharsets.UTF_8), index = "{\"objects\":{}}".getBytes(StandardCharsets.UTF_8);
        byte[] metadata = LauncherJson.write(Map.of("id", "test", "type", "release", "mainClass", "net.minecraft.client.main.Main", "javaVersion", Map.of("majorVersion", Runtime.version().feature()), "downloads", Map.of("client", Map.of("sha1", hash("SHA-1", client), "size", client.length)), "libraries", List.of(), "assetIndex", Map.of("id", "test-assets", "sha1", hash("SHA-1", index), "size", index.length), "arguments", Map.of("jvm", List.of("-cp", "${classpath}"), "game", List.of("--gameDir", "${game_directory}"))));
        var entries = new ArrayList<InstallationReceipt.FileEntry>(); var files = Map.of("versions/game.json", metadata, "versions/client.jar", client, "assets/indexes/game.json", index);
        for (var file : files.entrySet()) { storage.writeAtomically(base.resolve("installations").resolve(installation).resolve(file.getKey()), file.getValue()); entries.add(new InstallationReceipt.FileEntry(file.getKey(), file.getValue().length, hash("SHA-256", file.getValue()))); }
        var receipt = new InstallationReceipt(instance, installation, "test", hash("SHA-1", metadata), clock.instant(), Platform.current(), Runtime.version().feature(), entries);
        storage.writeAtomically(base.resolve("installations").resolve(installation).resolve("installation.json"), receipt.bytes()); storage.writeAtomically(base.resolve("active-installation.json"), LauncherJson.write(Map.of("schemaVersion", 1, "instanceId", instance, "installationId", installation, "versionId", "test", "receiptSha256", hash("SHA-256", receipt.bytes()))));
    }
    GameProcessService service() { var catalog = new FileInstanceCatalog(root, clock); return new GameProcessService(root, catalog, new VanillaInstaller(root, catalog, null, null, clock), runtimes, identities, new LaunchSettingsStore(root), clock); }
    @Test void requiresLocalSessionAndRejectsExecutionFields() throws Exception {
        fixture(); var harness = new LauncherWebServerTest(); harness.temporary = root.resolve("http-resources"); harness.start(); harness.server.close();
        try (var process = service(); var runtime = new RuntimeRoutes(runtimes, clock)) {
            harness.server = LauncherWebServer.start(0, new LauncherApi(List.of(runtime, new IdentityRoutes(identities), new LaunchRoutes(process, new LaunchSettingsStore(root)))), harness.resources);
            for (String path : List.of("runtimes", "identities", "launches/close-status", "launches?instanceId=" + instance)) {
                assertEquals(403, harness.request("/api/launcher/v1/" + path, "GET", null, Map.of("X-SQY-Client", "launcher")).statusCode());
                assertEquals(403, harness.request("/api/launcher/v1/" + path, "GET", null, Map.of("X-SQY-Client", "launcher", "Origin", "https://foreign.invalid")).statusCode());
            }
            var headers = harness.authorized(harness.token());
            var closeStatus = harness.request("/api/launcher/v1/launches/close-status", "GET", null, headers);
            assertEquals(200, closeStatus.statusCode());
            assertTrue(LauncherJson.read(closeStatus.body(), 16384).path("canClose").booleanValue());
            for (String field : List.of("executable", "command", "pid", "javaagent")) assertEquals(400, harness.request("/api/launcher/v1/launches", "POST", "{\"checkId\":\"" + UUID.randomUUID() + "\",\"" + field + "\":\"evil\"}", headers).statusCode());
            assertEquals(413, harness.request("/api/launcher/v1/launches", "POST", " ".repeat(16385), headers).statusCode());
            String revision = new LaunchSettingsStore(root).read(instance).revision().toString(); var fields = new LinkedHashMap<String, Object>(); fields.put("javaId", null); fields.put("initialMemoryMiB", 256); fields.put("maximumMemoryMiB", 512); fields.put("width", 960); fields.put("height", 540); fields.put("extraJvmArgs", List.of("-Dlabel=" + "x".repeat(4088), "-Dtwo=" + "x".repeat(4089), "-Dthree=" + "x".repeat(4087), "-Dfour=" + "x".repeat(4088))); fields.put("acceptUnknownJava", false);
            String json = new String(LauncherJson.write(Map.of("revision", revision, "settings", fields)), StandardCharsets.UTF_8); assertTrue(json.length() > 16384);
            assertEquals(200, harness.request("/api/launcher/v1/instances/" + instance + "/launch-settings", "POST", json, headers).statusCode());
            fields.put("extraJvmArgs", List.of("-javaagent:forbidden.jar"));
            String forbidden = new String(LauncherJson.write(Map.of("revision", new LaunchSettingsStore(root).read(instance).revision().toString(), "settings", fields)), StandardCharsets.UTF_8);
            assertEquals(400, harness.request("/api/launcher/v1/instances/" + instance + "/launch-settings", "POST", forbidden, headers).statusCode());
            for (int i = 0; i < 3; i++) {
                var oversized = harness.request("/api/launcher/v1/instances/" + instance + "/launch-settings", "POST", " ".repeat(131073), headers);
                assertEquals(413, oversized.statusCode()); assertEquals("REQUEST_TOO_LARGE", LauncherJson.read(oversized.body(), 16384).path("error").path("code").asText());
            }
            assertTrue(process.list(instance).isEmpty()); assertTrue(runtimes.runtimes().isEmpty());
        } finally { harness.stop(); }
    }
    @Test void launchChecksPreserveMissingJavaChangedFileAndBusyReasons() throws Exception {
        fixture(); var identity = identities.create("Alice");
        try (var process = service()) {
            var api = new LauncherApi(List.of(new LaunchRoutes(process, new LaunchSettingsStore(root))));
            assertEquals("JAVA_NOT_FOUND", failedCheck(api, identity.id()));
            runtimes.registerHome(Path.of(System.getProperty("java.home")), new CancellationToken());
            Path client = root.resolve("instances/" + instance + "/installations/" + installation + "/versions/client.jar"); byte[] original = Files.readAllBytes(client); Files.writeString(client, "changed");
            assertEquals("FILE_CHANGED", failedCheck(api, identity.id())); Files.write(client, original);
            try (var lock = new LauncherStorage(root).lock(Path.of("instances", instance, "installations/.install.lock"))) { assertEquals("INSTANCE_BUSY", failedCheck(api, identity.id())); }
            assertTrue(process.list(instance).isEmpty());
        }
    }
    String failedCheck(LauncherApi api, UUID identity) throws Exception {
        var response = api.handle(RuntimeRoutesTest.request("POST", "/launch-checks", Map.of("instanceId", instance, "identityId", identity.toString())));
        var check = LauncherJson.read(LauncherJson.write(response.body()), 16384).path("check"); String id = check.path("id").asText();
        for (int i = 0; i < 400 && check.path("state").asText().equals("CHECKING"); i++) { Thread.sleep(20); check = LauncherJson.read(LauncherJson.write(api.handle(RuntimeRoutesTest.request("GET", "/launch-checks/" + id, null)).body()), 16384).path("check"); }
        assertEquals("FAILED", check.path("state").asText()); String json = check.toString(); assertFalse(json.contains("command")); assertFalse(json.contains(root.toString())); return check.path("errorCode").asText();
    }
    @Test void reportsStalePlanWithoutRetryingStart() throws Exception {
        fixture(); runtimes.registerHome(Path.of(System.getProperty("java.home")), new CancellationToken()); var identity = identities.create("Alice");
        try (var process = service()) {
            var settings = new LaunchSettingsStore(root); var route = new LauncherApi(List.of(new LaunchRoutes(process, settings)));
            var response = route.handle(RuntimeRoutesTest.request("POST", "/launch-checks", Map.of("instanceId", instance, "identityId", identity.id().toString()))); assertEquals(202, response.status());
            var check = LauncherJson.read(LauncherJson.write(response.body()), 16384).path("check"); String id = check.path("id").asText();
            for (int i = 0; i < 400 && check.path("state").asText().equals("CHECKING"); i++) { Thread.sleep(20); check = LauncherJson.read(LauncherJson.write(route.handle(RuntimeRoutesTest.request("GET", "/launch-checks/" + id, null)).body()), 16384).path("check"); }
            assertEquals("READY", check.path("state").asText()); var previous = settings.read(instance); settings.save(instance, previous.revision(), previous.settings());
            var failure = assertThrows(LauncherApiException.class, () -> route.handle(RuntimeRoutesTest.request("POST", "/launches", Map.of("checkId", id)))); assertEquals(409, failure.status()); assertEquals("SETTINGS_CHANGED", failure.code()); assertTrue(process.list(instance).isEmpty());
        }
    }
    @Test void directConnectRequiresAnUnchangedSavedTargetAndNeverAcceptsAnArbitraryHost() throws Exception {
        fixture(); var identity=identities.create("Alice"); var servers=new cn.dreamingfish.launcher.servers.ServerStore(root);
        var first=servers.read(); var saved=servers.save(first.revision(),null,"原地址",new cn.dreamingfish.launcher.servers.ServerAddress("one.example.org",25565));
        String id=saved.servers().getFirst().id(); servers.save(saved.revision(),id,"新地址",new cn.dreamingfish.launcher.servers.ServerAddress("two.example.org",25566));
        try(var process=service()) {
            var api=new LauncherApi(List.of(new LaunchRoutes(process,new LaunchSettingsStore(root),servers)));
            var stale=assertThrows(LauncherApiException.class,()->api.handle(RuntimeRoutesTest.request("POST","/launch-checks",Map.of("instanceId",instance,"identityId",identity.id().toString(),"serverId",id,"serverRevision",saved.revision()))));
            assertEquals(409,stale.status());assertEquals("SERVER_CHANGED",stale.code());
            assertThrows(LauncherApiException.class,()->api.handle(RuntimeRoutesTest.request("POST","/launch-checks",Map.of("instanceId",instance,"identityId",identity.id().toString(),"host","arbitrary.example.org"))));
            assertTrue(process.list(instance).isEmpty());
        }
    }
}
