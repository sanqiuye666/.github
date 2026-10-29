package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.*;
import cn.dreamingfish.launcher.runtime.JavaRuntime;
import cn.dreamingfish.launcher.vanilla.InstallationReceipt;
import java.nio.file.Path;
import java.util.*;

/** 内部固定快照，不直接序列化给 HTTP；命令只在运行路径准备好后生成。 */
public record LaunchPlan(GameInstance instance, InstallationReceipt installation, JavaRuntime javaRuntime,
                         GameIdentity identity, UUID settingsRevision, Path installationDirectory,
                         List<Path> classpath, List<LaunchArguments.Argument> jvmTokens, String mainClass,
                         List<LaunchArguments.Argument> gameTokens, String assetIndexId,
                         boolean virtualAssets, boolean mapToResources, List<NativeArchive> natives) {
    public record NativeArchive(Path archive, long size, String sha256, List<String> excludes) {
        public NativeArchive { excludes = List.copyOf(excludes); }
    }
    public LaunchPlan {
        classpath = List.copyOf(classpath); jvmTokens = List.copyOf(jvmTokens); gameTokens = List.copyOf(gameTokens); natives = List.copyOf(natives);
    }
    public String instanceId() { return instance.id(); }
    public String installationId() { return installation.installationId(); }
    public String metadataSha1() { return installation.metadataSha1(); }
    public List<String> jvmArguments() { return jvmTokens.stream().map(LaunchArguments.Argument::template).toList(); }
    public List<String> gameArguments() { return gameTokens.stream().map(LaunchArguments.Argument::template).toList(); }
    public Map<LaunchArguments.Slot, String> paths(Path assets, Path nativeRoot) {
        Path gameAssets = virtualAssets ? assets.resolve("virtual").resolve(assetIndexId)
                : mapToResources ? instance.directory().resolve("resources") : assets;
        return Map.of(LaunchArguments.Slot.ASSETS_ROOT, assets.toString(), LaunchArguments.Slot.NATIVES_DIRECTORY, nativeRoot.toString(), LaunchArguments.Slot.GAME_ASSETS, gameAssets.toString());
    }
    public List<String> command(Path assets, Path nativeRoot) throws LaunchFailure {
        var paths = paths(assets, nativeRoot); var command = new ArrayList<String>(); command.add(javaRuntime.executable().toString());
        for (var arg : jvmTokens) command.add(arg.resolve(paths));
        command.add(mainClass);
        for (var arg : gameTokens) command.add(arg.resolve(paths));
        return List.copyOf(command);
    }
}
