package cn.dreamingfish.launcher.options;

import cn.dreamingfish.launcher.downloads.CancellationToken;
import cn.dreamingfish.launcher.launch.LaunchFailure;
import cn.dreamingfish.launcher.storage.LauncherStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GameOptionsTest {
    @TempDir Path root;
    final String id = UUID.randomUUID().toString(), other = UUID.randomUUID().toString();
    Path game() throws Exception { Path p = root.resolve("instances/" + id + "/.minecraft/options.txt"); Files.createDirectories(p.getParent()); return p; }
    GameOptionsStore.Snapshot template(boolean enabled, Map<String,Object> values) throws Exception {
        var store = new GameOptionsStore(root); return store.save(store.read().revision(), enabled, values);
    }
    @Test void encodingAndStrictValidation() {
        assertThrows(IllegalArgumentException.class, () -> GameOption.MAX_FPS.encode(144));
        assertEquals("140", GameOption.MAX_FPS.encode(140));
        assertEquals(10, GameOption.MAX_FPS.view().get("step"));
        assertEquals("0.5", GameOption.FOV.encode(90));
        assertEquals("0.5", GameOption.MOUSE_SENSITIVITY.encode(100));
        assertEquals("\"right\"", GameOption.MAIN_HAND.encode("right"));
        for (Object invalid : List.of(111, -1, 90.5, "90", true)) assertThrows(IllegalArgumentException.class, () -> GameOption.FOV.encode(invalid));
        assertThrows(IllegalArgumentException.class, () -> GameOption.MAIN_HAND.encode("left\nkey:evil"));
    }
    @Test void revisionsConflictsAndDamagedRecordNeverResetSilently() throws Exception {
        var store = new GameOptionsStore(root); var empty = store.read(); assertFalse(empty.enabled()); assertTrue(empty.values().isEmpty());
        var saved = store.save(empty.revision(), true, Map.of("fov", 90));
        assertEquals(saved, new GameOptionsStore(root).read());
        assertEquals("OPTIONS_CHANGED", assertThrows(LaunchFailure.class, () -> store.save(empty.revision(), false, Map.of())).code());
        assertThrows(IllegalArgumentException.class, () -> store.save(saved.revision(), true, Map.of("version", 1)));
        Path file = root.resolve("game-options.json"); Files.writeString(file, "broken record");
        assertEquals("OPTIONS_INVALID", assertThrows(LaunchFailure.class, store::read).code());
        assertEquals("broken record", Files.readString(file));
    }
    @Test void mergesOnlySelectedFieldsPreservesBytesAndIsolatesOtherInstance() throws Exception {
        byte[] original = "version:3839\r\nfov:0.0\r\n\r\nlastServer:example:25565\r\nfov:0.1\r\nkey_key.forward:key.keyboard.w\r\n".getBytes(StandardCharsets.UTF_8);
        Path file = game(); Files.write(file, original);
        Path sentinel = root.resolve("instances/" + other + "/.minecraft/options.txt"); Files.createDirectories(sentinel.getParent()); Files.writeString(sentinel, "other instance");
        Path save = file.getParent().resolve("saves/keep"); Files.createDirectories(save.getParent()); Files.writeString(save, "save");
        var snapshot = template(true, Map.of("fov", 90, "mainHand", "left")); UUID run = UUID.randomUUID();
        var sync = new GameOptionsSync(root);
        assertEquals(2, sync.apply(id, "1.20.6", run, snapshot, new CancellationToken()));
        assertEquals("version:3839\r\nfov:0.5\r\n\r\nlastServer:example:25565\r\nkey_key.forward:key.keyboard.w\r\nmainHand:\"left\"\r\n", Files.readString(file));
        assertArrayEquals(original, Files.readAllBytes(root.resolve("instances/" + id + "/options-backups/" + run + "-options.txt")));
        assertEquals("other instance", Files.readString(sentinel)); assertEquals("save", Files.readString(save));
        assertEquals(0, sync.apply(id, "1.20.6", UUID.randomUUID(), snapshot, new CancellationToken()));
        try (var backups = Files.list(root.resolve("instances/" + id + "/options-backups"))) { assertEquals(1, backups.count()); }
    }
    @Test void newFileAndBothVerifiedVersionsAndNoopUnsupported() throws Exception {
        game(); var sync = new GameOptionsSync(root); var enabled = template(true, Map.of("mouseSensitivity", 120));
        assertEquals(1, sync.apply(id, "1.21.1", UUID.randomUUID(), enabled, new CancellationToken()));
        assertEquals("version:3955\nmouseSensitivity:0.6\n", Files.readString(game()));
        var off = new GameOptionsStore.Snapshot(UUID.randomUUID(), false, Map.of("fov", 90));
        assertEquals(0, sync.apply(other, "26.2", UUID.randomUUID(), off, new CancellationToken()));
        assertEquals(0, sync.apply(other, "26.2", UUID.randomUUID(), new GameOptionsStore.Snapshot(UUID.randomUUID(), true, Map.of()), new CancellationToken()));
        assertEquals("OPTIONS_UNSUPPORTED", assertThrows(LaunchFailure.class, () -> sync.apply(id, "26.2", UUID.randomUUID(), enabled, new CancellationToken())).code());
    }
    @Test void invalidFilesBackupCollisionAndCancellationKeepOriginal() throws Exception {
        var sync = new GameOptionsSync(root); var selected = template(true, Map.of("fov", 95)); Path file = game();
        for (byte[] bad : List.of("version:3955\nfov:0.0\n".getBytes(), "version:3839\ninvalid line".getBytes(), "version:3839\r\nfov:0.0\n".getBytes(), new byte[]{(byte)0xff}, new byte[1024*1024+1])) {
            Files.write(file, bad); assertThrows(LaunchFailure.class, () -> sync.apply(id, "1.20.6", UUID.randomUUID(), selected, new CancellationToken())); assertArrayEquals(bad, Files.readAllBytes(file));
        }
        byte[] original = "version:3839\nfov:0.0\n".getBytes(); Files.write(file, original);
        UUID run = UUID.randomUUID(); Path backup = root.resolve("instances/" + id + "/options-backups/" + run + "-options.txt"); Files.createDirectories(backup.getParent()); Files.writeString(backup, "existing backup");
        assertThrows(LaunchFailure.class, () -> sync.apply(id, "1.20.6", run, selected, new CancellationToken()));
        assertArrayEquals(original, Files.readAllBytes(file)); assertEquals("existing backup", Files.readString(backup));
        var cancelled = new CancellationToken(); cancelled.cancel(); assertThrows(java.io.InterruptedIOException.class, () -> sync.apply(id, "1.20.6", UUID.randomUUID(), selected, cancelled)); assertArrayEquals(original, Files.readAllBytes(file));
        try (var ignored = new LauncherStorage(root).lock(Path.of(".game-options.lock"))) {
            assertThrows(LaunchFailure.class, () -> new GameOptionsStore(root).save(selected.revision(), false, Map.of()));
        }
    }
}
