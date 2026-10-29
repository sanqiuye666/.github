package cn.dreamingfish.launcher.instances;

import cn.dreamingfish.launcher.GameInstance;
import cn.dreamingfish.launcher.InstanceCatalog;
import cn.dreamingfish.launcher.catalog.VersionManifest;
import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.storage.LauncherStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

public final class FileInstanceCatalog implements InstanceCatalog {
    private static final Path INSTANCES = Path.of("instances");
    private final LauncherStorage storage;
    private final Clock clock;
    public FileInstanceCatalog(Path dataRoot, Clock clock) {
        storage = new LauncherStorage(dataRoot); this.clock = Objects.requireNonNull(clock);
    }
    @Override public List<GameInstance> instances() {
        try { return listing().instances().stream().map(PreparedInstance::instance).toList(); }
        catch (IOException error) { throw new UncheckedIOException(error); }
    }
    public PreparedInstance create(String name, String versionId, VersionManifest manifest) throws IOException {
        String displayName = PreparedInstance.validName(name);
        if (manifest == null || manifest.versions().stream().noneMatch(v -> v.id().equals(versionId))) throw new IllegalArgumentException("请选择官方目录中存在的版本");
        try (var ignored = storage.lock(INSTANCES.resolve(".catalog.lock"))) {
            String id = UUID.randomUUID().toString();
            Path pending = INSTANCES.resolve(".pending-" + id), destination = INSTANCES.resolve(id);
            storage.ensureDirectory(pending.resolve(".minecraft"));
            Instant createdAt = clock.instant();
            storage.writeAtomically(pending.resolve("instance.json"), LauncherJson.write(Map.of(
                    "schemaVersion", 1, "id", id, "name", displayName, "gameVersion", versionId,
                    "loader", "VANILLA", "createdAt", createdAt.toString(), "state", "PREPARED")));
            storage.publishDirectory(pending, destination);
            return prepared(id, displayName, versionId, createdAt);
        }
    }
    public InstanceListing listing() throws IOException {
        var instances = new ArrayList<PreparedInstance>();
        var problems = new ArrayList<InstanceListing.Problem>();
        for (Path relative : storage.children(INSTANCES)) {
            String id = relative.getFileName().toString();
            if (id.equals(".catalog.lock") || (id.startsWith(".pending-") && canonicalUuid(id.substring(9)))) continue;
            try {
                if (!canonicalUuid(id)) throw new IllegalArgumentException("无法识别的实例目录 ID");
                storage.requireDirectory(relative);
                var node = LauncherJson.read(storage.read(relative.resolve("instance.json"), 65536), 65536);
                if (!node.path("schemaVersion").isInt() || node.path("schemaVersion").intValue() != 1) throw new IllegalArgumentException("不支持的实例记录格式");
                if (!id.equals(LauncherJson.text(node, "id", 36))) throw new IllegalArgumentException("实例 ID 与目录不一致");
                if (!"VANILLA".equals(LauncherJson.text(node, "loader", 32)) || !"PREPARED".equals(LauncherJson.text(node, "state", 32))) throw new IllegalArgumentException("不支持的实例类型或准备状态");
                String name = PreparedInstance.validName(LauncherJson.text(node, "name", 128));
                String version = LauncherJson.text(node, "gameVersion", 128);
                Instant createdAt = Instant.parse(LauncherJson.text(node, "createdAt", 64));
                storage.requireDirectory(relative.resolve(".minecraft"));
                instances.add(prepared(id, name, version, createdAt));
            } catch (IOException | RuntimeException error) { problems.add(new InstanceListing.Problem(id, error.getMessage())); }
        }
        return new InstanceListing(instances, problems);
    }
    private PreparedInstance prepared(String id, String name, String version, Instant createdAt) {
        return new PreparedInstance(new GameInstance(id, name, version, GameInstance.LoaderKind.VANILLA,
                storage.root().resolve(INSTANCES).resolve(id).resolve(".minecraft")), createdAt, PreparedInstance.State.PREPARED);
    }
    private static boolean canonicalUuid(String text) {
        try { return UUID.fromString(text).toString().equals(text); } catch (IllegalArgumentException invalid) { return false; }
    }
}
