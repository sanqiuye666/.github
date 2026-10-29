package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.downloads.TaskRequest;
import cn.dreamingfish.launcher.instances.FileInstanceCatalog;
import cn.dreamingfish.launcher.storage.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.security.*;
import java.time.Clock;
import java.util.*;

/** 沿用实例目录与安全存储，只持久化启动设置，不改变 instance.json。 */
public final class LaunchSettingsStore {
    public record VersionedSettings(UUID revision, LaunchSettings settings) { }
    private static final UUID EMPTY_REVISION = new UUID(0, 0);
    private static final int LIMIT = 1024 * 1024;
    private final LauncherStorage storage;
    private final FileInstanceCatalog instances;

    public LaunchSettingsStore(Path root) { storage = new LauncherStorage(root); instances = new FileInstanceCatalog(root, Clock.systemUTC()); }
    public VersionedSettings read(String instanceId) throws IOException {
        requireInstance(instanceId);
        Path file = file(instanceId);
        if (!storage.exists(file)) return new VersionedSettings(EMPTY_REVISION, LaunchSettings.defaults());
        try {
            var envelope = LauncherJson.read(storage.read(file, LIMIT), LIMIT);
            keys(envelope, Set.of("schemaVersion", "payload", "sha256"));
            if (!envelope.path("schemaVersion").isInt() || envelope.path("schemaVersion").intValue() != 1) throw new IllegalArgumentException();
            var payload = envelope.path("payload"); keys(payload, Set.of("revision", "settings"));
            String expected = LauncherJson.text(envelope, "sha256", 64);
            if (!expected.matches("[0-9a-f]{64}") || !expected.equals(digest(LauncherJson.write(payload)))) throw new IllegalArgumentException();
            UUID revision = UUID.fromString(LauncherJson.text(payload, "revision", 36));
            var node = payload.path("settings");
            keys(node, Set.of("javaId", "initialMemoryMiB", "maximumMemoryMiB", "width", "height", "extraJvmArgs", "acceptUnknownJava"));
            String javaId = node.path("javaId").isNull() ? null : LauncherJson.text(node, "javaId", 64);
            if (!node.path("extraJvmArgs").isArray() || node.path("extraJvmArgs").size() > 32 || !node.path("acceptUnknownJava").isBoolean()) throw new IllegalArgumentException();
            var args = new ArrayList<String>();
            for (JsonNode arg : node.path("extraJvmArgs")) { if (!arg.isTextual()) throw new IllegalArgumentException(); args.add(arg.textValue()); }
            return new VersionedSettings(revision, new LaunchSettings(javaId, integer(node, "initialMemoryMiB"), integer(node, "maximumMemoryMiB"),
                    integer(node, "width"), integer(node, "height"), args, node.path("acceptUnknownJava").booleanValue()));
        } catch (IOException | RuntimeException invalid) {
            throw new LaunchFailure("SETTINGS_INVALID", "启动设置不可读取或校验失败，原文件已保留");
        }
    }
    public VersionedSettings save(String instanceId, UUID expectedRevision, LaunchSettings settings) throws IOException {
        Objects.requireNonNull(expectedRevision); Objects.requireNonNull(settings); requireInstance(instanceId);
        try (var ignored = storage.lock(Path.of("instances", instanceId, ".launch-settings.lock"))) {
            if (!read(instanceId).revision().equals(expectedRevision)) throw new LaunchFailure("SETTINGS_CHANGED", "设置已被其他操作更新，请重新读取");
            UUID revision = UUID.randomUUID();
            var fields = new LinkedHashMap<String, Object>();
            fields.put("javaId", settings.javaId()); fields.put("initialMemoryMiB", settings.initialMemoryMiB());
            fields.put("maximumMemoryMiB", settings.maximumMemoryMiB()); fields.put("width", settings.width()); fields.put("height", settings.height());
            fields.put("extraJvmArgs", settings.extraJvmArgs()); fields.put("acceptUnknownJava", settings.acceptUnknownJava());
            var payload = Map.of("revision", revision.toString(), "settings", fields);
            byte[] bytes = LauncherJson.write(Map.of("schemaVersion", 1, "payload", payload, "sha256", digest(LauncherJson.write(payload))));
            if (bytes.length > LIMIT) throw new LaunchFailure("SETTINGS_INVALID", "启动设置超过记录上限");
            storage.writeAtomically(file(instanceId), bytes);
            return new VersionedSettings(revision, settings);
        } catch (LauncherBusyException busy) { throw new LaunchFailure("INSTANCE_BUSY", "实例设置正在被其他任务使用，请稍后重试"); }
    }
    private void requireInstance(String id) throws IOException {
        TaskRequest.uuid(id);
        if (instances.listing().instances().stream().noneMatch(value -> value.instance().id().equals(id))) {
            throw new LaunchFailure("INSTANCE_NOT_FOUND", "实例不存在或记录无效，未改动原文件");
        }
    }
    private static Path file(String id) { return Path.of("instances", id, "launch-settings.json"); }
    private static int integer(JsonNode node, String key) { if (!node.path(key).isInt()) throw new IllegalArgumentException(); return node.path(key).intValue(); }
    private static void keys(JsonNode node, Set<String> expected) {
        var actual = new HashSet<String>(); node.fieldNames().forEachRemaining(actual::add);
        if (!node.isObject() || !actual.equals(expected)) throw new IllegalArgumentException();
    }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
