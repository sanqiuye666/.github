package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.*;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.runtime.JavaRuntime;
import cn.dreamingfish.launcher.storage.*;
import cn.dreamingfish.launcher.vanilla.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** 仅消费固定安装和已经探测的 Java；没有网络请求或进程创建。 */
public final class VanillaLaunchPlanner {
    private final LauncherStorage storage;
    public VanillaLaunchPlanner(Path root) { storage = new LauncherStorage(root); }

    public LaunchPlan plan(GameInstance instance, InstallationReceipt receipt, JavaRuntime java,
                           GameIdentity identity, LaunchSettingsStore.VersionedSettings versioned, CancellationToken token) throws IOException {
        token.check(); TaskRequest.uuid(instance.id());
        Path instanceRoot = Path.of("instances", instance.id()), base = instanceRoot.resolve("installations").resolve(receipt.installationId());
        if (!instance.directory().equals(storage.root().resolve(instanceRoot).resolve(".minecraft")) || instance.loader() != GameInstance.LoaderKind.VANILLA && instance.loader() != receipt.loaderKind()
                || !instance.id().equals(receipt.instanceId()) || !instance.gameVersion().equals(receipt.versionId()) || identity.kind() != GameIdentity.Kind.OFFLINE) {
            throw new LaunchFailure("INSTALLATION_INVALID", "实例、身份与固定安装不匹配");
        }
        var files = new HashMap<String, InstallationReceipt.FileEntry>(); for (var file : receipt.files()) files.put(file.path(), file);
        byte[] metadata = read(base, files, "versions/game.json", 4 * 1024 * 1024, receipt.metadataSha1(), token);
        try {
            JsonNode node = LauncherJson.read(metadata, 4 * 1024 * 1024);
            if (!instance.gameVersion().equals(LauncherJson.text(node, "id", 128)) || node.has("inheritsFrom")) throw LaunchArguments.unsupported();
            if (node.has("javaVersion") && (!node.path("javaVersion").path("majorVersion").isInt()
                    || !Objects.equals(receipt.requiredJavaMajor(), node.path("javaVersion").path("majorVersion").intValue()))) {
                throw new LaunchFailure("INSTALLATION_INVALID", "官方 Java 要求与安装记录不一致，请重新验证安装");
            }
            validateJava(java, receipt, versioned.settings());
            String mainClass = LauncherJson.text(node, "mainClass", 256);
            if (!mainClass.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) throw LaunchArguments.unsupported();
            var features = Map.of("is_demo_user", false, "has_custom_resolution", true, "has_quick_plays_support", false,
                    "is_quick_play_singleplayer", false, "is_quick_play_multiplayer", false, "is_quick_play_realms", false);
            var classpath = new LinkedHashSet<Path>(); var natives = new LinkedHashMap<Path, LaunchPlan.NativeArchive>();
            if (!node.path("libraries").isArray()) throw LaunchArguments.unsupported();
            for (var library : node.path("libraries")) {
                token.check(); if (!LaunchArguments.allowed(library.get("rules"), receipt.platform(), features)) continue;
                JsonNode downloads = library.path("downloads");
                if (downloads.has("artifact")) {
                    String path = libraryFile(downloads.get("artifact")); verify(base, files, path, sha1(downloads.get("artifact")), token);
                    classpath.add(storage.root().resolve(base).resolve(path));
                    String[] coordinates = library.path("name").asText().split(":");
                    if (coordinates.length >= 4 && coordinates[3].startsWith("natives-")) nativeArchive(base, files, path, library, natives);
                }
                JsonNode nativeName = library.path("natives").get(receipt.platform().osName());
                if (nativeName != null) {
                    if (!nativeName.isTextual()) throw LaunchArguments.unsupported();
                    String classifier = nativeName.textValue().replace("${arch}", Integer.toString(receipt.platform().bits()));
                    JsonNode artifact = downloads.path("classifiers").path(classifier);
                    String path = libraryFile(artifact); verify(base, files, path, sha1(artifact), token);
                    nativeArchive(base, files, path, library, natives);
                }
            }
            verify(base, files, "versions/client.jar", sha1(node.path("downloads").path("client")), token);
            classpath.add(storage.root().resolve(base).resolve("versions/client.jar"));
            JsonNode index = node.path("assetIndex"); String indexId = LauncherJson.text(index, "id", 128);
            if (SafeInstallPath.parse(indexId).getNameCount() != 1) throw LaunchArguments.unsupported();
            var assets = AssetIndex.parse(read(base, files, "assets/indexes/game.json", 32 * 1024 * 1024, sha1(index), token));
            String separator = receipt.platform().osName().equals("windows") ? ";" : ":";
            if (classpath.stream().anyMatch(path -> path.toString().contains(separator))) throw new LaunchFailure("INVALID_ARGUMENT", "数据目录包含 Java 类路径分隔符，请选择其他数据目录");
            var fixed = new HashMap<String, String>();
            fixed.put("classpath", String.join(separator, classpath.stream().map(Path::toString).toList()));
            fixed.put("classpath_separator", separator); fixed.put("library_directory", storage.root().resolve(base).resolve("libraries").toString());
            fixed.put("game_directory", instance.directory().toString()); fixed.put("assets_index_name", indexId);
            fixed.put("resolution_width", Integer.toString(versioned.settings().width())); fixed.put("resolution_height", Integer.toString(versioned.settings().height()));
            fixed.put("auth_player_name", identity.name()); fixed.put("auth_uuid", identity.id().toString().replace("-", ""));
            fixed.put("auth_access_token", "0"); fixed.put("auth_session", "0"); fixed.put("auth_xuid", ""); fixed.put("clientid", "");
            fixed.put("user_type", "legacy"); fixed.put("user_properties", "{}"); fixed.put("version_name", instance.gameVersion());
            fixed.put("version_type", LauncherJson.text(node, "type", 64)); fixed.put("launcher_name", "SanQiuYe"); fixed.put("launcher_version", "0.1.0-SNAPSHOT");
            List<LaunchArguments.Argument> game; var jvm = new ArrayList<LaunchArguments.Argument>();
            if (node.has("arguments")) {
                var arguments = node.path("arguments");
                jvm.addAll(LaunchArguments.read(arguments.get("jvm"), receipt.platform(), features, fixed));
                game = LaunchArguments.read(arguments.get("game"), receipt.platform(), features, fixed);
            } else {
                jvm.add(LaunchArguments.compile("-Djava.library.path=${natives_directory}", fixed)); jvm.add(LaunchArguments.literal("-cp")); jvm.add(LaunchArguments.literal(fixed.get("classpath")));
                var tokens = new ArrayList<LaunchArguments.Argument>();
                for (String value : LaunchArguments.splitLegacy(LauncherJson.text(node, "minecraftArguments", 32768))) tokens.add(LaunchArguments.compile(value, fixed));
                game = List.copyOf(tokens);
            }
            var jvmText = jvm.stream().map(LaunchArguments.Argument::template).toList();
            if (Collections.frequency(jvmText, "-cp") != 1 || jvmText.indexOf("-cp") + 1 >= jvmText.size()
                    || !jvmText.get(jvmText.indexOf("-cp") + 1).equals(fixed.get("classpath"))) throw LaunchArguments.unsupported();
            // 实例设置完整提供用户 JVM 配置，不叠加 Mojang 的可选 default-user-jvm 建议组。
            jvm.removeIf(value -> value.template().startsWith("-Xmx") || value.template().startsWith("-Xms"));
            jvm.add(LaunchArguments.literal("-Xms" + versioned.settings().initialMemoryMiB() + "M"));
            jvm.add(LaunchArguments.literal("-Xmx" + versioned.settings().maximumMemoryMiB() + "M"));
            for (String value : versioned.settings().extraJvmArgs()) jvm.add(LaunchArguments.literal(value));
            if (node.has("logging")) {
                var logging = node.path("logging").path("client"); verify(base, files, "logging/client.xml", sha1(logging.path("file")), token);
                var loggingFixed = new HashMap<>(fixed); loggingFixed.put("path", storage.root().resolve(base).resolve("logging/client.xml").toString());
                jvm.add(LaunchArguments.compile(LauncherJson.text(logging, "argument", 4096), loggingFixed));
            }
            var mutableGame = new ArrayList<>(game);
            if (mainClass.equals("net.minecraft.client.main.Main")) {
                for (var dimension : List.of(Map.entry("--width", versioned.settings().width()), Map.entry("--height", versioned.settings().height()))) {
                    var text = mutableGame.stream().map(LaunchArguments.Argument::template).toList(); int position = text.indexOf(dimension.getKey());
                    if (position < 0) { mutableGame.add(LaunchArguments.literal(dimension.getKey())); mutableGame.add(LaunchArguments.literal(dimension.getValue().toString())); }
                    else if (Collections.frequency(text, dimension.getKey()) != 1 || position + 1 >= text.size() || !text.get(position + 1).equals(dimension.getValue().toString())) throw LaunchArguments.unsupported();
                }
            }
            game = List.copyOf(mutableGame);
            var gameText = game.stream().map(LaunchArguments.Argument::template).toList(); int directoryCount = 0;
            for (int i = 0; i < gameText.size(); i++) if (gameText.get(i).equals("--gameDir")) {
                directoryCount++; if (i + 1 >= gameText.size() || !gameText.get(i + 1).equals(instance.directory().toString())) throw LaunchArguments.unsupported();
            }
            if (directoryCount != 1) throw LaunchArguments.unsupported();
            token.check();
            var vanilla=new LaunchPlan(instance, receipt, java, identity, versioned.revision(), storage.root().resolve(base), List.copyOf(classpath), jvm, mainClass, game, indexId, assets.virtual(), assets.mapToResources(), List.copyOf(natives.values()));
            return new cn.dreamingfish.launcher.loaders.LoaderLaunchSupport(storage.root()).apply(new cn.dreamingfish.launcher.loaders.fabric.FabricLaunchSupport(storage.root()).apply(vanilla,token),token);
        } catch (LaunchFailure failure) { throw failure; }
        catch (IllegalArgumentException invalid) { throw LaunchArguments.unsupported(); }
    }
    private static void validateJava(JavaRuntime java, InstallationReceipt receipt, LaunchSettings settings) throws LaunchFailure {
        int recommended = receipt.requiredJavaMajor() == null ? 8 : receipt.requiredJavaMajor();
        if (java.bits() != receipt.platform().bits() || receipt.requiredJavaMajor() != null && java.majorVersion() < recommended
                || java.majorVersion() != recommended && !settings.acceptUnknownJava()) throw new LaunchFailure("JAVA_INCOMPATIBLE", "此 Java 与该安装的版本或位数要求不符，请重新选择");
        if (settings.javaId() != null && !settings.javaId().equals(java.id())) throw new LaunchFailure("JAVA_NOT_FOUND", "所选 Java 与实例设置不一致，请重新检查");
    }
    private void nativeArchive(Path base, Map<String, InstallationReceipt.FileEntry> files, String path, JsonNode library, Map<Path, LaunchPlan.NativeArchive> natives) throws LaunchFailure {
        var excludes = new ArrayList<String>(); JsonNode values = library.path("extract").path("exclude");
        if (!values.isMissingNode()) { if (!values.isArray() || values.size() > 100) throw LaunchArguments.unsupported(); for (var value : values) { if (!value.isTextual() || value.textValue().length() > 1024) throw LaunchArguments.unsupported(); excludes.add(value.textValue()); } }
        if (excludes.isEmpty()) excludes.add("META-INF/");
        var file = files.get(path); if (file == null) throw new LaunchFailure("INSTALLATION_INVALID", "固定安装缺少必要文件记录");
        Path absolute = storage.root().resolve(base).resolve(path); natives.putIfAbsent(absolute, new LaunchPlan.NativeArchive(absolute, file.size(), file.sha256(), excludes));
    }
    private static String libraryFile(JsonNode artifact) throws LaunchFailure {
        try { return SafeInstallPath.parse("libraries/" + LauncherJson.text(artifact, "path", 1024)).toString().replace('\\', '/'); }
        catch (IllegalArgumentException invalid) { throw LaunchArguments.unsupported(); }
    }
    private static String sha1(JsonNode node) throws LaunchFailure {
        try { String value = LauncherJson.text(node, "sha1", 40); if (!value.matches("[0-9a-f]{40}")) throw LaunchArguments.unsupported(); return value; }
        catch (IllegalArgumentException invalid) { throw LaunchArguments.unsupported(); }
    }
    private byte[] read(Path base, Map<String, InstallationReceipt.FileEntry> files, String path, int limit, String sha1, CancellationToken token) throws IOException {
        token.check(); var file = files.get(path);
        if (file == null || file.size() > limit) throw new LaunchFailure("INSTALLATION_INVALID", "固定安装记录缺失或超过读取上限");
        if (!storage.exists(base.resolve(path))) throw new LaunchFailure("FILE_CHANGED", "安装文件缺失，已有文件未被改动");
        byte[] bytes = storage.read(base.resolve(SafeInstallPath.parse(path)), limit);
        var own = new FileDigest(FileDigest.Algorithm.SHA256, file.sha256()).newDigest(); var source = new FileDigest(FileDigest.Algorithm.SHA1, sha1).newDigest();
        if (bytes.length != file.size() || !HexFormat.of().formatHex(own.digest(bytes)).equals(file.sha256()) || !HexFormat.of().formatHex(source.digest(bytes)).equals(sha1)) throw new LaunchFailure("FILE_CHANGED", "安装元数据摘要已变化，原文件已保留");
        token.check(); return bytes;
    }
    private void verify(Path base, Map<String, InstallationReceipt.FileEntry> files, String path, String sha1, CancellationToken token) throws IOException {
        token.check(); var file = files.get(path);
        if (file == null) throw new LaunchFailure("INSTALLATION_INVALID", "固定安装缺少必要文件记录");
        Path relative = base.resolve(SafeInstallPath.parse(path));
        if (!storage.exists(relative) || storage.size(relative) != file.size()) throw new LaunchFailure("FILE_CHANGED", "安装文件缺失或长度已变化，原文件已保留");
        var own = new FileDigest(FileDigest.Algorithm.SHA256, file.sha256()).newDigest(); var source = new FileDigest(FileDigest.Algorithm.SHA1, sha1).newDigest(); long total = 0;
        try (var input = storage.openRead(relative)) { byte[] buffer = new byte[65536]; int count; while ((count = input.read(buffer)) != -1) { token.check(); total += count; if (total > file.size()) throw new LaunchFailure("FILE_CHANGED", "安装文件长度已变化"); own.update(buffer, 0, count); source.update(buffer, 0, count); } }
        if (total != file.size() || !HexFormat.of().formatHex(own.digest()).equals(file.sha256()) || !HexFormat.of().formatHex(source.digest()).equals(sha1)) throw new LaunchFailure("FILE_CHANGED", "安装文件摘要已变化，原文件已保留");
        token.check();
    }
}
