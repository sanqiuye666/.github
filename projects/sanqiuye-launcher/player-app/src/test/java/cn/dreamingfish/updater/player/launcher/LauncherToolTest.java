package cn.dreamingfish.updater.player.launcher;

import cn.dreamingfish.launcher.catalog.ManifestTransport;
import cn.dreamingfish.launcher.storage.LauncherJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LauncherToolTest {
    @TempDir Path temporary;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC);
    private static final String MANIFEST = "{\"latest\":{\"release\":\"1.20.1\",\"snapshot\":\"1.20.1\"},\"versions\":[{\"id\":\"1.20.1\",\"type\":\"release\",\"url\":\"https://piston-meta.mojang.com/v1/version.json\",\"sha1\":\"" + "a".repeat(40) + "\",\"time\":\"2024-01-01T00:00:00Z\",\"releaseTime\":\"2024-01-01T00:00:00Z\"}]}";
    private record Result(int code, String out, String err) {
        JsonNode success() throws IOException { assertEquals(0, code, err); assertEquals("", err); return LauncherJson.read(out.getBytes(StandardCharsets.UTF_8), 5 * 1024 * 1024); }
    }
    private Result run(ManifestTransport transport, String... args) {
        var out = new StringWriter(); var err = new StringWriter();
        int code = LauncherToolMain.run(args, new PrintWriter(out, true), new PrintWriter(err, true), transport, CLOCK);
        return new Result(code, out.toString(), err.toString());
    }
    private Result command(String... tail) {
        var args = new ArrayList<>(List.of("--data-root", temporary.resolve("launcher").toString())); args.addAll(List.of(tail));
        return run(() -> MANIFEST.getBytes(StandardCharsets.UTF_8), args.toArray(String[]::new));
    }
    @Test void returnsRealCatalogShapeAndCacheSourceWithFiltering() throws Exception {
        var catalog = command("catalog", "--query", "1.20", "--type", "release").success();
        assertEquals("NETWORK", catalog.path("source").asText());
        assertEquals("release", catalog.path("versions").get(0).path("type").asText());
        assertEquals("CACHE", command("catalog").success().path("source").asText());
        assertEquals(0, command("catalog", "--query", "missing").success().path("versions").size());
        assertEquals("NETWORK", command("catalog", "--refresh").success().path("source").asText());
    }
    @Test void createsPreparedInstancesAndListsThemAcrossInvocationsWithoutTouchingAccounts() throws Exception {
        Path sentinel = temporary.resolve("accounts.json"); Files.writeString(sentinel, "private-original");
        var created = command("create", "--name", "三秋叶世界", "--version", "1.20.1").success().path("instance");
        assertEquals("PREPARED", created.path("state").asText());
        assertEquals("三秋叶世界", created.path("name").asText());
        assertTrue(Files.isDirectory(Path.of(created.path("directory").asText())));
        var listed = command("instances").success();
        assertEquals(1, listed.path("instances").size()); assertEquals(0, listed.path("problems").size());
        assertEquals(created.path("id"), listed.path("instances").get(0).path("id"));
        assertEquals("private-original", Files.readString(sentinel));
    }
    @Test void invalidArgumentsReturnOneJsonErrorAndDoNotCreateARoot() throws Exception {
        String root = temporary.resolve("not-created").toString();
        for (String[] args : new String[][]{
                {}, {"catalog"}, {"--data-root", "relative", "instances"},
                {"--data-root", root, "execute"}, {"--data-root", root, "catalog", "--url", "https://evil.example"},
                {"--data-root", root, "catalog", "--refresh", "--refresh"},
                {"--data-root", root, "catalog", "--query"}, {"--data-root", root, "catalog", "--type", "wrong"},
                {"--data-root", root, "create", "--name", "name"},
                {"--data-root", root, "instances", "--refresh"}}) {
            Result result = run(() -> { throw new AssertionError("Invalid arguments must not access the network"); }, args);
            assertNotEquals(0, result.code()); assertEquals("", result.out());
            assertEquals("INVALID_ARGUMENT", LauncherJson.read(result.err().getBytes(StandardCharsets.UTF_8), 65536).path("error").asText());
        }
        assertFalse(Files.exists(Path.of(root)));
    }
    @Test void reportsOfflineFailureAndListsEmptyInstancesWithoutNetworkOrWrites() throws Exception {
        String root = temporary.resolve("empty").toString();
        var failure = run(() -> { throw new IOException("offline"); }, "--data-root", root, "catalog");
        assertNotEquals(0, failure.code()); assertEquals("", failure.out());
        assertEquals("IO_ERROR", LauncherJson.read(failure.err().getBytes(StandardCharsets.UTF_8), 65536).path("error").asText());
        var listing = run(() -> { throw new AssertionError("Listing must not access the network"); }, "--data-root", root, "instances").success();
        assertEquals(0, listing.path("instances").size()); assertFalse(Files.exists(Path.of(root)));
        assertNotEquals(0, command("create", "--name", "safe", "--version", "../../outside").code());
        assertFalse(Files.exists(temporary.resolve("launcher/instances")));
    }
    @Test void keepsDefaultSidecarAndBootstrapBoundEntrySeparate() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<mainClass>cn.dreamingfish.updater.player.PlayerSidecarMain</mainClass>"));
        String sidecar = Files.readString(Path.of("src/main/java/cn/dreamingfish/updater/player/PlayerSidecarMain.java"));
        assertFalse(sidecar.contains("LauncherToolMain"));
    }
    @Test void responseTimeCancellationProducesNonzeroCancelledInsteadOfSuccessfulCache() throws Exception {
        command("catalog").success();
        try {
            Result result = run(() -> { Thread.currentThread().interrupt(); return MANIFEST.getBytes(StandardCharsets.UTF_8); },
                    "--data-root", temporary.resolve("launcher").toString(), "catalog", "--refresh");
            assertEquals(4, result.code()); assertEquals("", result.out());
            assertEquals("CANCELLED", LauncherJson.read(result.err().getBytes(StandardCharsets.UTF_8), 65536).path("error").asText());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
