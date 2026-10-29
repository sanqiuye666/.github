package cn.dreamingfish.launcher.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class LauncherStorageTest {
    @TempDir Path temporary;

    @Test void cancellationDuringAChunkedCopyStopsBeforePublicationAndCleansItsStage() throws Exception {
        var storage=new LauncherStorage(temporary);var token=new cn.dreamingfish.launcher.downloads.CancellationToken();
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        var input=new java.io.ByteArrayInputStream(new byte[4*65536]){
            @Override public synchronized int read(byte[] b,int off,int len){int n=super.read(b,off,len);if(reads.incrementAndGet()==2)token.cancel();return n;}
        };
        assertThrows(java.io.InterruptedIOException.class,()->storage.copyNew(input,Path.of("copied/file"),token));
        assertEquals(2,reads.get());assertFalse(Files.exists(temporary.resolve("copied/file")));
        try(var children=Files.list(temporary.resolve("copied"))){assertEquals(0,children.count());}
    }

    @Test void constructsWithoutWritesAndAtomicallyStoresBoundedData() throws Exception {
        Path root = temporary.resolve("new");
        var storage = new LauncherStorage(root);
        assertFalse(Files.exists(root));
        storage.writeAtomically(Path.of("catalog/test.json"), "first".getBytes(StandardCharsets.UTF_8));
        storage.writeAtomically(Path.of("catalog/test.json"), "second".getBytes(StandardCharsets.UTF_8));
        assertEquals("second", new String(storage.read(Path.of("catalog/test.json"), 6), StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> storage.read(Path.of("catalog/test.json"), 5));
        assertThrows(IOException.class, () -> storage.read(Path.of("missing"), 100));
        try (var files = Files.list(root.resolve("catalog"))) { assertEquals(1, files.count()); }
    }

    @Test void rejectsAbsoluteAndTraversingPathsAndPreservesDestinationOnFailure() throws Exception {
        var storage = new LauncherStorage(temporary.resolve("root"));
        assertThrows(IllegalArgumentException.class, () -> new LauncherStorage(Path.of("relative")));
        for (Path unsafe : new Path[]{Path.of("../outside"), temporary.resolve("absolute"), Path.of("child/../file")}) {
            assertThrows(IllegalArgumentException.class, () -> storage.writeAtomically(unsafe, new byte[]{1}));
        }
        Path directory = temporary.resolve("root/existing");
        storage.ensureDirectory(Path.of("existing"));
        Files.writeString(directory.resolve("sentinel"), "keep");
        assertThrows(IOException.class, () -> storage.writeAtomically(Path.of("existing"), new byte[]{1}));
        assertEquals("keep", Files.readString(directory.resolve("sentinel")));
    }

    @Test void lockConflictsFailWithoutWaitingAndCanBeReacquired() throws Exception {
        var storage = new LauncherStorage(temporary);
        try (var ignored = storage.lock(Path.of("instances/.catalog.lock"))) {
            assertThrows(IOException.class, () -> storage.lock(Path.of("instances/.catalog.lock")));
        }
        try (var ignored = storage.lock(Path.of("instances/.catalog.lock"))) { assertNotNull(ignored); }
    }

    @Test void refusesDirectoryLinksWithoutTouchingTheirTarget() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("root"));
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.writeString(outside.resolve("sentinel"), "keep");
        Path link = root.resolve("alias");
        if (System.getProperty("os.name").startsWith("Windows")) {
            var child = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), outside.toString()).redirectErrorStream(true).start();
            assertEquals(0, child.waitFor(), new String(child.getInputStream().readAllBytes()));
        } else Files.createSymbolicLink(link, outside);
        try {
            var storage = new LauncherStorage(root);
            assertThrows(IOException.class, () -> storage.writeAtomically(Path.of("alias/sentinel"), new byte[]{1}));
            assertThrows(IOException.class, () -> storage.read(Path.of("alias/sentinel"), 100));
            assertThrows(IOException.class, () -> new LauncherStorage(link).ensureDirectory(Path.of("nested")));
            assertEquals("keep", Files.readString(outside.resolve("sentinel")));
        } finally { Files.delete(link); }
    }
}
