package cn.dreamingfish.launcher.storage;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/** Strict, bounded JSON for launcher-owned records; independent of updater protocols. */
public final class LauncherJson {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40)
                    .maxStringLength(262144).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private LauncherJson() {}

    public static JsonNode read(byte[] bytes, int limit) throws IOException {
        JsonNode node = readValue(bytes, limit);
        if (!node.isObject()) throw new IOException("JSON 必须是对象");
        return node;
    }
    public static JsonNode readValue(byte[] bytes, int limit) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > limit) throw new IOException("JSON 文件为空或超过大小限制");
        JsonNode node = JSON.readTree(bytes);
        if (node == null || !node.isObject() && !node.isArray()) throw new IOException("JSON 必须是对象或数组");
        return node;
    }
    public static byte[] write(Object value) throws IOException { return JSON.writeValueAsBytes(value); }
    public static String text(JsonNode node, String key, int limit) {
        JsonNode value = node == null ? null : node.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank()
                || value.textValue().length() > limit || value.textValue().chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("缺少或无效的字段：" + key);
        }
        return value.textValue();
    }
}
