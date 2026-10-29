package cn.dreamingfish.launcher.catalog;

import java.time.Instant;
import java.util.Objects;

public record VersionCatalogSnapshot(VersionManifest manifest, Instant fetchedAt, Source source, String warning) {
    public enum Source { NETWORK, CACHE, STALE_CACHE }
    public VersionCatalogSnapshot {
        Objects.requireNonNull(manifest); Objects.requireNonNull(fetchedAt); Objects.requireNonNull(source);
    }
}
