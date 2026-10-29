import cn.dreamingfish.launcher.catalog.*;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.launch.*;
import cn.dreamingfish.launcher.runtime.*;
import cn.dreamingfish.launcher.storage.*;
import cn.dreamingfish.launcher.vanilla.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 真实最终 JAR 的元数据格式和文件计划审计；不安装或执行全目录游戏。 */
public final class LaunchPlanAudit {
    private static final Map<String, Boolean> FEATURES = Map.of("is_demo_user", false, "has_custom_resolution", true, "has_quick_plays_support", false, "is_quick_play_singleplayer", false, "is_quick_play_multiplayer", false, "is_quick_play_realms", false);
    public static void main(String[] args) throws Exception {
        var options = new HashMap<String, String>(); for (int i = 0; i < args.length; i += 2) options.put(args[i], args[i + 1]);
        Path root = Path.of(options.get("--data-root")), report = Path.of(options.get("--report"));
        if (!root.isAbsolute() || !report.isAbsolute()) throw new IllegalArgumentException("测试路径必须为绝对路径");
        var storage = new LauncherStorage(root); var marker = LauncherJson.read(storage.read(Path.of(".sqy-m2d-smoke.json"), 4096), 4096);
        if (!marker.path("plan").asText().equals("launcher-m2d")) throw new IllegalArgumentException("需要专用已标记测试根");
        var catalog = MojangVersionCatalog.production(root); var snapshot = catalog.load(true); var manifest = snapshot.manifest();
        var store = new DownloadStore(root, new HttpRemoteClient()); var platform = Platform.current(); var planner = new VanillaPlanner(catalog, store, platform);
        var runtimes = new JavaRuntimeService(); runtimes.discover(new CancellationToken());
        var results = new ArrayList<Map<String, Object>>();
        try (var workers = Executors.newFixedThreadPool(4)) {
            var completion = new ExecutorCompletionService<Map<String, Object>>(workers); int next = 0, done = 0, inFlight = 0;
            while (done < manifest.versions().size()) {
                while (next < manifest.versions().size() && inFlight < 4) {
                    var version = manifest.versions().get(next++);
                    completion.submit(() -> inspect(version, root, store, planner, platform, runtimes)); inFlight++;
                }
                results.add(completion.take().get()); done++; inFlight--;
                if (done % 25 == 0) System.out.println("启动格式审计 " + done + "/" + manifest.versions().size());
            }
        }
        var counts = new TreeMap<String, Long>(); results.forEach(row -> counts.merge((String) row.get("status"), 1L, Long::sum)); results.sort(Comparator.comparing(row -> (String) row.get("versionId")));
        var payload = new LinkedHashMap<String, Object>(); payload.put("schemaVersion", 1); payload.put("auditedAt", Instant.now().toString()); payload.put("catalogTotal", manifest.versions().size()); payload.put("catalogSource", snapshot.source().name()); payload.put("catalogWarning", snapshot.warning());
        payload.put("coverage", "OFFICIAL_METADATA_ARGUMENT_FORMAT_AND_FILE_PLAN_ONLY"); payload.put("gameExecutions", 0); payload.put("counts", counts); payload.put("results", results);
        Files.createDirectories(report.getParent()); Files.write(report, LauncherJson.write(payload)); System.out.println(counts);
        if (counts.getOrDefault("LOCAL_DEFECT", 0L) > 0) System.exit(1);
    }
    private static Map<String, Object> inspect(OfficialVersion version, Path root, DownloadStore store, VanillaPlanner planner, Platform platform, JavaRuntimeService runtimes) {
        var row = new LinkedHashMap<String, Object>(); row.put("versionId", version.id()); row.put("metadataSha1", version.sha1()); row.put("type", version.type().wireName());
        try {
            var token = new CancellationToken(); var spec = new DownloadSpec(RemoteService.MOJANG_META, version.metadataUrl(), -1, 4 * 1024 * 1024, new FileDigest(FileDigest.Algorithm.SHA1, version.sha1()), "官方版本元数据");
            var object = store.fetch(spec, token, bytes -> {}); JsonNode node = LauncherJson.read(store.read(object, 4 * 1024 * 1024, token), 4 * 1024 * 1024);
            var filePlan = planner.planPinned(version.id(), version.sha1(), token); row.put("fileCount", filePlan.files().size()); row.put("totalBytes", filePlan.totalBytes());
            String main = LauncherJson.text(node, "mainClass", 256);
            if (!main.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*") || node.has("inheritsFrom")) throw new LaunchFailure("UNSUPPORTED_LAUNCH_FORMAT", "未知主类或继承格式");
            var fixed = new HashMap<String, String>();
            for (String key : List.of("classpath", "classpath_separator", "library_directory", "game_directory", "assets_index_name", "resolution_width", "resolution_height", "auth_player_name", "auth_uuid", "auth_access_token", "auth_session", "auth_xuid", "clientid", "user_type", "user_properties", "version_name", "version_type", "launcher_name", "launcher_version", "path")) fixed.put(key, "format-only-" + key);
            fixed.put("resolution_width", "960"); fixed.put("resolution_height", "540"); fixed.put("game_directory", root.resolve(".format-only-game").toString());
            List<LaunchArguments.Argument> game, jvm;
            if (node.has("arguments")) { jvm = LaunchArguments.read(node.path("arguments").get("jvm"), platform, FEATURES, fixed); game = LaunchArguments.read(node.path("arguments").get("game"), platform, FEATURES, fixed); }
            else {
                jvm = List.of(LaunchArguments.literal("-cp"), LaunchArguments.literal(fixed.get("classpath"))); var values = new ArrayList<LaunchArguments.Argument>();
                for (String value : LaunchArguments.splitLegacy(LauncherJson.text(node, "minecraftArguments", 32768))) values.add(LaunchArguments.compile(value, fixed)); game = List.copyOf(values);
            }
            var jvmText = jvm.stream().map(LaunchArguments.Argument::template).toList(); int cp = jvmText.indexOf("-cp");
            if (Collections.frequency(jvmText, "-cp") != 1 || cp + 1 >= jvmText.size() || !jvmText.get(cp + 1).equals(fixed.get("classpath"))) throw new LaunchFailure("UNSUPPORTED_LAUNCH_FORMAT", "无法固定类路径参数");
            var gameText = game.stream().map(LaunchArguments.Argument::template).toList(); int dir = gameText.indexOf("--gameDir");
            if (Collections.frequency(gameText, "--gameDir") != 1 || dir + 1 >= gameText.size() || !gameText.get(dir + 1).equals(fixed.get("game_directory"))) throw new LaunchFailure("UNSUPPORTED_LAUNCH_FORMAT", "无法固定游戏目录参数");
            if (node.has("logging")) LaunchArguments.compile(LauncherJson.text(node.path("logging").path("client"), "argument", 4096), fixed);
            var bindings = Map.of(LaunchArguments.Slot.NATIVES_DIRECTORY, root.resolve(".format-only-natives").toString(), LaunchArguments.Slot.ASSETS_ROOT, root.resolve(".format-only-assets").toString(), LaunchArguments.Slot.GAME_ASSETS, root.resolve(".format-only-game-assets").toString());
            for (var value : jvm) value.resolve(bindings); for (var value : game) value.resolve(bindings);
            int major = filePlan.requiredJavaMajor() == null ? 8 : filePlan.requiredJavaMajor(); boolean available = runtimes.select(major, platform.bits()).isPresent();
            row.put("status", available ? "FORMAT_CHECKED" : "JAVA_MISSING"); row.put("reason", available ? "元数据格式与文件计划通过；没有安装、执行此游戏" : "格式和文件计划通过，但本机缺匹配 Java；没有执行此游戏");
            row.put("requiredJavaMajor", major); row.put("mainClass", main); row.put("gameArgumentCount", game.size()); row.put("jvmArgumentCount", jvm.size());
        } catch (LaunchFailure unsupported) { row.put("status", "UNSUPPORTED_FORMAT"); row.put("reason", unsupported.getMessage()); }
        catch (Exception failure) {
            boolean local = failure instanceof RuntimeException || failure instanceof FileSystemException || failure instanceof TaskFailure task && !task.code().equals("IO_ERROR") || failure.getCause() instanceof IllegalArgumentException;
            row.put("status", local ? "LOCAL_DEFECT" : "EXTERNAL_FAILURE"); row.put("reason", failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
        return row;
    }
}
