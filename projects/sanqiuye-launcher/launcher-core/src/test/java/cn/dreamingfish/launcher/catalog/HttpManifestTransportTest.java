package cn.dreamingfish.launcher.catalog;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HttpManifestTransportTest {
    @Test void productionUsesOnlyFixedHttpsAndDocumentedTimeouts() {
        assertEquals(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"), HttpManifestTransport.OFFICIAL_URI);
        assertEquals(Duration.ofSeconds(10), HttpManifestTransport.CONNECT_TIMEOUT);
        assertEquals(Duration.ofSeconds(30), HttpManifestTransport.REQUEST_TIMEOUT);
    }
    @Test void handlesRealHttpStatusRedirectAndStreamLimit() throws Exception {
        var reached = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", e -> { var bytes = ManifestFixtures.bytes(); e.sendResponseHeaders(200, bytes.length); e.getResponseBody().write(bytes); e.close(); });
        server.createContext("/error", e -> { e.sendResponseHeaders(503, -1); e.close(); });
        server.createContext("/redirect", e -> { e.getResponseHeaders().set("Location", "/followed"); e.sendResponseHeaders(302, -1); e.close(); });
        server.createContext("/followed", e -> { reached.incrementAndGet(); e.sendResponseHeaders(200, -1); e.close(); });
        server.createContext("/large", e -> { e.sendResponseHeaders(200, 0); try { e.getResponseBody().write(new byte[VersionManifestParser.MAX_BYTES + 1]); } catch (IOException ignored) {} finally { e.close(); } });
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            assertArrayEquals(ManifestFixtures.bytes(), new HttpManifestTransport(URI.create(base + "/ok"), Duration.ofSeconds(2)).fetch());
            for (String path : new String[]{"/error", "/redirect", "/large"}) assertThrows(IOException.class, () -> new HttpManifestTransport(URI.create(base + path), Duration.ofSeconds(2)).fetch());
            assertEquals(0, reached.get());
        } finally { server.stop(0); }
    }
    @Test void requestTimeoutAlsoBoundsABodyThatStopsAfterHeaders() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        var release = new java.util.concurrent.CountDownLatch(1);
        server.createContext("/slow", e -> { e.sendResponseHeaders(200, 0); e.getResponseBody().write('{'); e.getResponseBody().flush(); try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } finally { e.close(); } });
        server.start();
        try {
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/slow");
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThrows(IOException.class, () -> new HttpManifestTransport(uri, Duration.ofMillis(150)).fetch()));
        } finally { release.countDown(); server.stop(0); }
    }
}
