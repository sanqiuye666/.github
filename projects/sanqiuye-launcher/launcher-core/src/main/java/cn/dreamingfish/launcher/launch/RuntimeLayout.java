package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherStorage;
import cn.dreamingfish.launcher.vanilla.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 调用方持有实例准备锁；复制资源，核对已有投影，最后发布单次私有 natives。 */
public final class RuntimeLayout {
    public record PreparedRuntime(Path gameDirectory, Path assetsDirectory, Path nativesDirectory, List<String> command) {
        public PreparedRuntime { command = List.copyOf(command); }
    }
    private final LauncherStorage storage;
    private final NativeExtractor extractor;
    public RuntimeLayout(Path root) { this(root, new NativeExtractor()); }
    RuntimeLayout(Path root, NativeExtractor extractor) { storage = new LauncherStorage(root); this.extractor = Objects.requireNonNull(extractor); }

    public PreparedRuntime prepare(LaunchPlan plan, UUID launchId, CancellationToken token) throws IOException {
        token.check(); Path instance = Path.of("instances", plan.instanceId());
        if (!plan.installationDirectory().equals(storage.root().resolve(instance).resolve("installations").resolve(plan.installationId()))
                || !plan.instance().directory().equals(storage.root().resolve(instance).resolve(".minecraft"))) throw new LaunchFailure("INSTALLATION_INVALID", "运行目录与实例不匹配");
        Path installed = storage.root().relativize(plan.installationDirectory()), runtime = instance.resolve("runtime").resolve(plan.installationId());
        var assets = new LinkedHashMap<Path, InstallationReceipt.FileEntry>(); var resources = new LinkedHashMap<Path, InstallationReceipt.FileEntry>();
        for (var file : plan.installation().files()) {
            if (file.path().startsWith("assets/")) {
                Path target = file.path().equals("assets/indexes/game.json") ? Path.of("assets/indexes/" + plan.assetIndexId() + ".json") : SafeInstallPath.parse(file.path());
                assets.put(target, file);
            } else if (plan.mapToResources() && file.path().startsWith("resources/")) resources.put(SafeInstallPath.parse(file.path().substring(10)), file);
        }
        Path pending = instance.resolve("runtime/.pending-" + UUID.randomUUID());
        if (storage.exists(runtime)) verifyTree(runtime, assets, token);
        else {
            copy(installed, pending, assets, token); token.check(); storage.publishDirectory(pending, runtime);
        }
        Path gameResources = instance.resolve(".minecraft/resources");
        if (!resources.isEmpty()) {
            if (storage.exists(gameResources)) verifyTree(gameResources, resources, token);
            else {
                Path resourceStage = instance.resolve("runtime/.resources-pending-" + UUID.randomUUID());
                copy(installed, resourceStage, resources, token); token.check(); storage.publishDirectory(resourceStage, gameResources);
            }
        }
        Path launch = instance.resolve("launches").resolve(launchId.toString()), nativeStage = launch.resolve(".natives-pending-" + UUID.randomUUID()), natives = launch.resolve("natives");
        if (storage.exists(natives)) throw new LaunchFailure("INSTANCE_BUSY", "本次启动的原生库目录已存在");
        storage.ensureDirectory(nativeStage); Path stagingRoot = storage.root().resolve(nativeStage), nativeTarget = stagingRoot;
        var bindings = plan.paths(storage.root().resolve(runtime).resolve("assets"), stagingRoot);
        for (var arg : plan.jvmTokens()) if (arg.template().startsWith("-Djava.library.path=")) {
            nativeTarget = Path.of(arg.resolve(bindings).substring("-Djava.library.path=".length())).normalize();
            if (!nativeTarget.startsWith(stagingRoot)) throw new LaunchFailure("INVALID_ARGUMENT", "原生库参数不属于当前实例");
        }
        extractor.extract(plan.natives(), nativeTarget, token); token.check(); storage.publishDirectory(nativeStage, natives);
        Path assetRoot = storage.root().resolve(runtime).resolve("assets"), nativeRoot = storage.root().resolve(natives);
        return new PreparedRuntime(plan.instance().directory(), assetRoot, nativeRoot, plan.command(assetRoot, nativeRoot));
    }
    private void copy(Path installed, Path target, Map<Path, InstallationReceipt.FileEntry> files, CancellationToken token) throws IOException {
        storage.ensureDirectory(target);
        for (var entry : files.entrySet()) {
            token.check(); var source = entry.getValue(); Path input = installed.resolve(SafeInstallPath.parse(source.path()));
            verify(input, source, token); storage.copyNew(input, target.resolve(entry.getKey()), token); verify(target.resolve(entry.getKey()), source, token);
        }
    }
    private void verifyTree(Path root, Map<Path, InstallationReceipt.FileEntry> files, CancellationToken token) throws IOException {
        storage.requireDirectory(root); var directories = new HashSet<Path>();
        for (var entry : files.entrySet()) {
            verify(root.resolve(entry.getKey()), entry.getValue(), token);
            for (Path parent = entry.getKey().getParent(); parent != null; parent = parent.getParent()) directories.add(parent);
        }
        var queue = new ArrayDeque<Path>(); queue.add(root);
        while (!queue.isEmpty()) for (Path child : storage.children(queue.removeFirst())) {
            token.check(); Path relative = root.relativize(child);
            if (files.containsKey(relative)) continue;
            if (!directories.contains(relative)) throw changed();
            storage.requireDirectory(child); queue.add(child);
        }
    }
    private void verify(Path path, InstallationReceipt.FileEntry expected, CancellationToken token) throws IOException {
        token.check(); if (!storage.exists(path) || storage.size(path) != expected.size()) throw changed();
        var digest = new FileDigest(FileDigest.Algorithm.SHA256, expected.sha256()).newDigest(); long size = 0;
        try (var input = storage.openRead(path)) { byte[] buffer = new byte[65536]; int count; while ((count = input.read(buffer)) != -1) { token.check(); size += count; if (size > expected.size()) throw changed(); digest.update(buffer, 0, count); } }
        if (size != expected.size() || !HexFormat.of().formatHex(digest.digest()).equals(expected.sha256())) throw changed();
    }
    private static LaunchFailure changed() { return new LaunchFailure("FILE_CHANGED", "运行资源已变化，已有文件和其他实例均未被覆盖"); }
}
