package cn.dreamingfish.updater.player.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeLifetimeTest {
    @TempDir Path root;
    @Test void holdsExclusiveNativeRootAndReleasesWithoutRemovingData() throws Exception {
        Files.writeString(root.resolve("keep.txt"),"玩家内容");
        try(var owner=NativeLifetime.open(root,true)) {
            assertThrows(IOException.class,()->NativeLifetime.open(root,true));
        }
        try(var reopened=NativeLifetime.open(root,true)) { assertEquals("玩家内容",Files.readString(root.resolve("keep.txt"))); }
    }
    @Test void parentPipeEofRequestsGracefulShutdown() throws Exception {
        try(var lifetime=NativeLifetime.open(root,true);var input=new PipedInputStream();var parent=new PipedOutputStream(input)) {
            var stop=new CountDownLatch(1);
            lifetime.watch(input,stop::countDown);
            assertFalse(stop.await(80,TimeUnit.MILLISECONDS));
            parent.close();
            assertTrue(stop.await(3,TimeUnit.SECONDS));
        }
    }
    @Test void legacyWebModeDoesNotLockOrWatchStandardInput() throws Exception {
        try(var legacy=NativeLifetime.open(root,false);var nativeOwner=NativeLifetime.open(root,true)) {
            var stop=new CountDownLatch(1);legacy.watch(new ByteArrayInputStream(new byte[0]),stop::countDown);
            assertFalse(stop.await(80,TimeUnit.MILLISECONDS));
        }
    }
    @Test void refusesNonemptyLifetimeLockInsteadOfModifyingExistingFile() throws Exception {
        var lock=root.resolve(".native-launcher.lock");
        Files.writeString(lock,"保持原样");
        assertThrows(IOException.class,()->NativeLifetime.open(root,true));
        assertEquals("保持原样",Files.readString(lock));
    }
}
