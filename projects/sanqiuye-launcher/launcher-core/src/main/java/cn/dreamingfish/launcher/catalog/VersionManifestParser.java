package cn.dreamingfish.launcher.catalog;

import cn.dreamingfish.launcher.storage.LauncherJson;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;

public final class VersionManifestParser {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    public VersionManifest parse(byte[] bytes) {
        try {
            var root = LauncherJson.read(bytes, MAX_BYTES);
            var latest = root.get("latest");
            var versions = root.get("versions");
            if (versions == null || !versions.isArray() || versions.size() > 20000) throw new IllegalArgumentException("官方目录缺少版本列表");
            var entries = new ArrayList<OfficialVersion>();
            for (var value : versions) entries.add(new OfficialVersion(
                    LauncherJson.text(value, "id", 128), OfficialVersion.Type.fromWire(LauncherJson.text(value, "type", 32)),
                    URI.create(LauncherJson.text(value, "url", 2048)), LauncherJson.text(value, "sha1", 40),
                    Instant.parse(LauncherJson.text(value, "releaseTime", 64)), Instant.parse(LauncherJson.text(value, "time", 64))));
            return new VersionManifest(LauncherJson.text(latest, "release", 128), LauncherJson.text(latest, "snapshot", 128), entries);
        } catch (Exception error) {
            throw new IllegalArgumentException("官方版本目录无效：" + error.getMessage(), error);
        }
    }
}
