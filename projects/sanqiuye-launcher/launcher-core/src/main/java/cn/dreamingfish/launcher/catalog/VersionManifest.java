package cn.dreamingfish.launcher.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public record VersionManifest(String latestRelease, String latestSnapshot, List<OfficialVersion> versions) {
    public VersionManifest {
        if (versions == null || versions.isEmpty() || versions.size() > 20000) throw new IllegalArgumentException("官方版本目录数量无效");
        versions = List.copyOf(versions);
        var ids = new HashSet<String>();
        for (var entry : versions) if (!ids.add(entry.id())) throw new IllegalArgumentException("官方目录包含重复版本 ID");
        if (versions.stream().noneMatch(v -> v.id().equals(latestRelease) && v.type() == OfficialVersion.Type.RELEASE)
                || versions.stream().noneMatch(v -> v.id().equals(latestSnapshot)
                    && (v.type() == OfficialVersion.Type.SNAPSHOT || v.type() == OfficialVersion.Type.RELEASE))) {
            throw new IllegalArgumentException("最新版本必须存在于官方目录中");
        }
    }
    public List<OfficialVersion> search(String query, OfficialVersion.Type type) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return versions.stream().filter(v -> type == null || v.type() == type)
                .filter(v -> v.id().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
}
