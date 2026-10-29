package cn.dreamingfish.launcher.catalog;

import java.nio.charset.StandardCharsets;

public final class ManifestFixtures {
    private ManifestFixtures() {}
    public static String json() {
        return "{\"latest\":{\"release\":\"1.20.1\",\"snapshot\":\"24w01a\"},\"versions\":["
                + entry("1.20.1", "release") + "," + entry("24w01a", "snapshot") + ","
                + entry("a1.0", "old_alpha") + "," + entry("b1.0", "old_beta") + "]}";
    }
    public static String entry(String id, String type) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type
                + "\",\"url\":\"https://piston-meta.mojang.com/v1/packages/" + "a".repeat(40)
                + "/version.json\",\"sha1\":\"" + "a".repeat(40)
                + "\",\"releaseTime\":\"2024-01-01T00:00:00+00:00\",\"time\":\"2024-01-02T00:00:00Z\"}";
    }
    public static byte[] bytes() { return json().getBytes(StandardCharsets.UTF_8); }
}
