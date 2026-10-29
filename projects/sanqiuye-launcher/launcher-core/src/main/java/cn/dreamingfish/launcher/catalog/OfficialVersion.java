package cn.dreamingfish.launcher.catalog;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

public record OfficialVersion(String id, Type type, URI metadataUrl, String sha1,
                              Instant releaseTime, Instant updatedAt) {
    public enum Type {
        RELEASE, SNAPSHOT, OLD_ALPHA, OLD_BETA;
        public String wireName() { return name().toLowerCase(Locale.ROOT); }
        public static Type fromWire(String text) {
            for (Type type : values()) if (type.wireName().equals(text)) return type;
            throw new IllegalArgumentException("不支持的官方版本类型");
        }
    }
    public OfficialVersion {
        if (id == null || id.isBlank() || id.length() > 128 || id.chars().anyMatch(Character::isISOControl)
                || type == null || releaseTime == null || updatedAt == null
                || sha1 == null || !sha1.matches("[a-fA-F0-9]{40}")) {
            throw new IllegalArgumentException("官方版本字段无效");
        }
        if (metadataUrl == null || !"https".equals(metadataUrl.getScheme())
                || metadataUrl.getHost() == null
                || !Set.of("piston-meta.mojang.com", "launchermeta.mojang.com").contains(metadataUrl.getHost())
                || metadataUrl.getUserInfo() != null || metadataUrl.getFragment() != null
                || metadataUrl.getQuery() != null || (metadataUrl.getPort() != -1 && metadataUrl.getPort() != 443)
                || metadataUrl.getPath() == null || !metadataUrl.getPath().endsWith(".json")) {
            throw new IllegalArgumentException("版本元数据必须使用官方 HTTPS 地址");
        }
        sha1 = sha1.toLowerCase(Locale.ROOT);
    }
}
