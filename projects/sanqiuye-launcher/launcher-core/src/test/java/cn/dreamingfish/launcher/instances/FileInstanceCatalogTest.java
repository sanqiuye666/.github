package cn.dreamingfish.launcher.instances;

import cn.dreamingfish.launcher.GameInstance;
import cn.dreamingfish.launcher.catalog.*;
import cn.dreamingfish.launcher.storage.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FileInstanceCatalogTest {
    @TempDir Path temporary;
    private Path root() { return temporary.resolve("data"); }
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC);
    private VersionManifest manifest() { return new VersionManifestParser().parse(ManifestFixtures.bytes()); }
    private FileInstanceCatalog catalog() { return new FileInstanceCatalog(root(), CLOCK); }

    @Test void isolatesAndReopensPreparedVanillaInstancesWithoutChangingExistingData() throws Exception {
        Path original = temporary.resolve("old-instance"); Files.createDirectory(original);
        Files.writeString(original.resolve("accounts.json"), "untouched");
        var catalog = catalog(); assertTrue(catalog.instances().isEmpty()); assertFalse(Files.exists(root()));
        var first = catalog.create(" 三秋叶生存 ", "1.20.1", manifest());
        var second = catalog.create("快照实验", "24w01a", manifest());
        assertEquals("三秋叶生存", first.instance().name());
        assertNotEquals(first.instance().id(), second.instance().id());
        assertNotEquals(first.instance().directory(), second.instance().directory());
        assertTrue(Files.isDirectory(first.instance().directory()));
        assertEquals(root().resolve("instances").resolve(first.instance().id()).resolve(".minecraft"), first.instance().directory());
        assertEquals(GameInstance.LoaderKind.VANILLA, first.instance().loader());
        assertEquals(PreparedInstance.State.PREPARED, first.state());
        var reopened = catalog().listing();
        assertEquals(2, reopened.instances().size()); assertTrue(reopened.problems().isEmpty());
        assertEquals(2, catalog().instances().size());
        assertEquals("untouched", Files.readString(original.resolve("accounts.json")));
        assertFalse(Files.exists(first.instance().directory().resolve("versions")));
    }
    @Test void rejectsUnknownVersionsAndInvalidNamesBeforeWriting() {
        var catalog = catalog();
        assertThrows(IllegalArgumentException.class, () -> catalog.create("name", "../../outside", manifest()));
        for (String name : new String[]{"", "   ", "bad\nname", "x".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> catalog.create(name, "1.20.1", manifest()));
        }
        assertFalse(Files.exists(root()));
    }
    @Test void displayNamesNeverBecomePathsAndEmojiCountAsCharacters() throws Exception {
        var entry = catalog().create("../../只是名称", "1.20.1", manifest());
        assertTrue(entry.instance().directory().startsWith(root().resolve("instances")));
        assertEquals("../../只是名称", entry.instance().name());
        assertEquals(64, catalog().create("🌿".repeat(64), "1.20.1", manifest()).instance().name().codePointCount(0, 128));
    }
    @Test void reportsDamagedRecordsWhilePreservingGoodOnesAndTheirFiles() throws Exception {
        var good = catalog().create("good", "1.20.1", manifest());
        var bad = catalog().create("bad", "1.20.1", manifest());
        Path metadata = root().resolve("instances").resolve(bad.instance().id()).resolve("instance.json");
        for (String field : new String[]{"schemaVersion", "id", "state", "loader"}) {
            var json = (ObjectNode) LauncherJson.read(Files.readAllBytes(metadata), 65536);
            if (field.equals("schemaVersion")) json.put(field, 99); else json.put(field, "invalid");
            byte[] invalid = LauncherJson.write(json); Files.write(metadata, invalid);
            var listing = catalog().listing();
            assertEquals(1, listing.instances().size()); assertEquals(good.instance().id(), listing.instances().getFirst().instance().id());
            assertEquals(1, listing.problems().size()); assertArrayEquals(invalid, Files.readAllBytes(metadata));
            Files.write(metadata, LauncherJson.write(java.util.Map.of("schemaVersion", 1, "id", bad.instance().id(), "name", "bad", "gameVersion", "1.20.1", "loader", "VANILLA", "createdAt", CLOCK.instant().toString(), "state", "PREPARED")));
        }
        Files.writeString(metadata, "broken"); assertEquals(1, catalog().listing().problems().size());
    }
    @Test void refusesLockedCreationAndDoesNotPublishIncompleteStagingDirectories() throws Exception {
        var storage = new LauncherStorage(root());
        try (var ignored = storage.lock(Path.of("instances/.catalog.lock"))) {
            assertThrows(IOException.class, () -> catalog().create("busy", "1.20.1", manifest()));
        }
        storage.ensureDirectory(Path.of("instances/.pending-" + UUID.randomUUID()));
        assertTrue(catalog().instances().isEmpty());
        assertEquals(PreparedInstance.State.PREPARED, catalog().create("after", "1.20.1", manifest()).state());
        assertEquals(1, catalog().instances().size());
    }
    @Test void missingGameDirectoryIsAProblemRatherThanAnInstalledInstance() throws Exception {
        var instance = catalog().create("missing", "1.20.1", manifest());
        Files.delete(instance.instance().directory());
        assertTrue(catalog().instances().isEmpty()); assertEquals(1, catalog().listing().problems().size());
        assertFalse(Files.exists(instance.instance().directory()));
    }
    @Test void anotherProcessHoldingTheDirectoryLockPreventsCreation() throws Exception {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        var builder = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"), LockProbe.class.getName(), root().toString());
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        var child = builder.start();
        try {
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(child.getInputStream()));
            assertEquals("LOCKED", reader.readLine());
            assertThrows(IOException.class, () -> catalog().create("busy", "1.20.1", manifest()));
            assertTrue(catalog().instances().isEmpty());
        } finally { child.getOutputStream().close(); if (!child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) child.destroyForcibly(); }
        assertEquals(0, child.exitValue());
        assertEquals(PreparedInstance.State.PREPARED, catalog().create("after", "1.20.1", manifest()).state());
    }
    public static class LockProbe {
        public static void main(String[] args) throws Exception {
            try (var lock = new LauncherStorage(Path.of(args[0])).lock(Path.of("instances/.catalog.lock"))) {
                System.out.println("LOCKED"); System.out.flush(); System.in.read();
            }
        }
    }
}
