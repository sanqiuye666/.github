package cn.dreamingfish.launcher.catalog;

import cn.dreamingfish.launcher.storage.LauncherJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MojangVersionCatalogTest {
    @TempDir Path root;
    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    private static Clock at(Instant instant) { return Clock.fixed(instant, ZoneOffset.UTC); }
    private Path cache() { return root.resolve("catalog/version-manifest.json"); }
    private void seed() throws IOException { new MojangVersionCatalog(root, at(NOW), ManifestFixtures::bytes).load(false); }

    @Test void fetchesAndReopensFreshCacheWithoutNetwork() throws Exception {
        var result = new MojangVersionCatalog(root, at(NOW), ManifestFixtures::bytes).load(false);
        assertEquals(VersionCatalogSnapshot.Source.NETWORK, result.source());
        assertEquals(NOW, result.fetchedAt());
        var calls = new AtomicInteger();
        var cached = new MojangVersionCatalog(root, at(NOW.plusSeconds(86399)), () -> { calls.incrementAndGet(); throw new IOException("offline"); }).load(false);
        assertEquals(VersionCatalogSnapshot.Source.CACHE, cached.source());
        assertEquals(0, calls.get());
        assertEquals(4, cached.manifest().versions().size());
    }
    @Test void explicitRefreshFetchesEvenWhenCacheIsFresh() throws Exception {
        seed(); var calls = new AtomicInteger();
        var result = new MojangVersionCatalog(root, at(NOW.plusSeconds(1)), () -> { calls.incrementAndGet(); return ManifestFixtures.bytes(); }).load(true);
        assertEquals(1, calls.get()); assertEquals(VersionCatalogSnapshot.Source.NETWORK, result.source());
        assertEquals(NOW.plusSeconds(1), result.fetchedAt());
    }
    @Test void expiryBoundaryAndFutureTimestampsTriggerNetworkAndHonestFallback() throws Exception {
        seed(); byte[] original = Files.readAllBytes(cache());
        for (Instant time : new Instant[]{NOW.plusSeconds(86400), NOW.minusSeconds(1)}) {
            var calls = new AtomicInteger();
            var result = new MojangVersionCatalog(root, at(time), () -> { calls.incrementAndGet(); throw new IOException("offline"); }).load(false);
            assertEquals(1, calls.get()); assertEquals(VersionCatalogSnapshot.Source.STALE_CACHE, result.source());
            assertNotNull(result.warning()); assertFalse(result.warning().isBlank());
            assertArrayEquals(original, Files.readAllBytes(cache()));
        }
    }
    @Test void badNetworkDataDoesNotReplaceValidCache() throws Exception {
        seed(); byte[] original = Files.readAllBytes(cache());
        var result = new MojangVersionCatalog(root, at(NOW), () -> "{}".getBytes()).load(true);
        assertEquals(VersionCatalogSnapshot.Source.STALE_CACHE, result.source());
        assertArrayEquals(original, Files.readAllBytes(cache()));
    }
    @Test void firstFailureDoesNotCreateFalseCache() {
        assertThrows(IOException.class, () -> new MojangVersionCatalog(root, at(NOW), () -> { throw new IOException("offline"); }).load(false));
        assertFalse(Files.exists(cache()));
    }
    @Test void invalidUnknownAndOversizeCacheCannotBecomeSuccessfulOfflineData() throws Exception {
        seed(); var envelope = (ObjectNode) LauncherJson.read(Files.readAllBytes(cache()), 5 * 1024 * 1024);
        envelope.put("schemaVersion", 99);
        for (byte[] invalid : new byte[][]{ "broken".getBytes(), LauncherJson.write(envelope), new byte[5 * 1024 * 1024 + 1] }) {
            Files.write(cache(), invalid);
            assertThrows(IOException.class, () -> new MojangVersionCatalog(root, at(NOW), () -> { throw new IOException("offline"); }).load(false));
            assertArrayEquals(invalid, Files.readAllBytes(cache()));
        }
    }
    @Test void cancellationIsNotReportedAsCacheSuccess() throws Exception {
        seed();
        try {
            assertThrows(IOException.class, () -> new MojangVersionCatalog(root, at(NOW), () -> { throw new InterruptedException(); }).load(true));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
    @Test void cancellationAfterResponseDoesNotFallBackOrReplaceCache() throws Exception {
        seed(); byte[] original = Files.readAllBytes(cache());
        try {
            assertThrows(IOException.class, () -> new MojangVersionCatalog(root, at(NOW), () -> {
                Thread.currentThread().interrupt(); return ManifestFixtures.bytes();
            }).load(true));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertArrayEquals(original, Files.readAllBytes(cache()));
    }
    @Test void alreadyCancelledFreshCacheReadsDoNotReportSuccess() throws Exception {
        seed();
        try {
            Thread.currentThread().interrupt();
            assertThrows(IOException.class, () -> new MojangVersionCatalog(root, at(NOW), ManifestFixtures::bytes).load(false));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
    @Test void missingNetworkMessagesStillGiveAReadableFailureReason() throws Exception {
        seed();
        var result = new MojangVersionCatalog(root, at(NOW), () -> { throw new java.net.ConnectException(); }).load(true);
        assertFalse(result.warning().contains("null"));
        assertTrue(result.warning().contains("连接"));
        Path fresh = root.resolve("fresh");
        var failure = assertThrows(IOException.class, () -> new MojangVersionCatalog(fresh, at(NOW), () -> { throw new java.net.ConnectException(); }).load(false));
        assertFalse(failure.getMessage().contains("null"));
        assertTrue(failure.getMessage().contains("连接"));
    }
}
