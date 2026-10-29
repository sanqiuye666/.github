package cn.dreamingfish.launcher.catalog;

import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.storage.LauncherStorage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.util.Map;
import java.util.Objects;

public final class MojangVersionCatalog {
    private static final Path CACHE = Path.of("catalog/version-manifest.json");
    private static final int MAX_CACHE_BYTES = 5 * 1024 * 1024;
    private final LauncherStorage storage;
    private final Clock clock;
    private final ManifestTransport transport;
    private final VersionManifestParser parser = new VersionManifestParser();
    public MojangVersionCatalog(Path dataRoot, Clock clock, ManifestTransport transport) {
        storage = new LauncherStorage(dataRoot);
        this.clock = Objects.requireNonNull(clock); this.transport = Objects.requireNonNull(transport);
    }
    public static MojangVersionCatalog production(Path root) {
        return new MojangVersionCatalog(root, Clock.systemUTC(), new HttpManifestTransport());
    }
    public VersionCatalogSnapshot load(boolean refresh) throws IOException {
        requireNotCancelled();
        VersionCatalogSnapshot cached = readCache();
        requireNotCancelled();
        Instant now = clock.instant();
        if (!refresh && cached != null && !cached.fetchedAt().isAfter(now)
                && now.isBefore(cached.fetchedAt().plus(Duration.ofHours(24)))) return cached;
        try {
            byte[] bytes = transport.fetch();
            requireNotCancelled();
            VersionManifest manifest = parser.parse(bytes);
            Instant fetchedAt = clock.instant();
            byte[] envelope = LauncherJson.write(Map.of("schemaVersion", 1, "fetchedAt", fetchedAt.toString(),
                    "manifest", LauncherJson.read(bytes, VersionManifestParser.MAX_BYTES)));
            try (var ignored = storage.lock(Path.of("catalog/.cache.lock"))) { storage.writeAtomically(CACHE, envelope); }
            requireNotCancelled();
            return new VersionCatalogSnapshot(manifest, fetchedAt, VersionCatalogSnapshot.Source.NETWORK, null);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("版本目录请求已取消", error);
        } catch (IOException | IllegalArgumentException error) {
            requireNotCancelled();
            if (cached != null) return new VersionCatalogSnapshot(cached.manifest(), cached.fetchedAt(),
                    VersionCatalogSnapshot.Source.STALE_CACHE, "刷新未完成，正在使用已有缓存：" + failureReason(error));
            throw new IOException("无法获取官方版本目录，且没有有效缓存：" + failureReason(error), error);
        }
    }
    private static void requireNotCancelled() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("版本目录请求已取消");
    }
    private static String failureReason(Exception error) {
        if (error instanceof java.net.ConnectException) return "无法连接官方版本服务，请检查网络或代理设置";
        if (error instanceof java.net.http.HttpTimeoutException) return "官方版本服务请求超时，请稍后重试";
        String message = error.getMessage();
        return message == null || message.isBlank() ? "网络请求或本地缓存操作失败，请检查连接和目录权限" : message;
    }
    private VersionCatalogSnapshot readCache() {
        try {
            var envelope = LauncherJson.read(storage.read(CACHE, MAX_CACHE_BYTES), MAX_CACHE_BYTES);
            if (!envelope.path("schemaVersion").isInt() || envelope.path("schemaVersion").intValue() != 1) return null;
            Instant fetchedAt = Instant.parse(LauncherJson.text(envelope, "fetchedAt", 64));
            var manifest = parser.parse(LauncherJson.write(envelope.get("manifest")));
            return new VersionCatalogSnapshot(manifest, fetchedAt, VersionCatalogSnapshot.Source.CACHE, null);
        } catch (IOException | RuntimeException invalid) { return null; }
    }
}
