package cn.dreamingfish.updater.player.launcher;

import cn.dreamingfish.launcher.catalog.*;
import cn.dreamingfish.launcher.instances.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;

/** Explicit M2A tool main; never selected by the updater sidecar or Bootstrap. */
public final class LauncherToolMain {
    private LauncherToolMain() {}
    public static void main(String[] args) {
        int code = run(args, new PrintWriter(System.out, true, StandardCharsets.UTF_8),
                new PrintWriter(System.err, true, StandardCharsets.UTF_8), new HttpManifestTransport(), Clock.systemUTC());
        if (code != 0) System.exit(code);
    }
    public static int run(String[] args, PrintWriter out, PrintWriter err, ManifestTransport transport, Clock clock) {
        try {
            Arguments input = Arguments.parse(args);
            var result = new LinkedHashMap<String, Object>();
            result.put("schemaVersion", 1); result.put("command", input.command());
            var instances = new FileInstanceCatalog(input.root(), clock);
            if (input.command().equals("instances")) {
                var listing = instances.listing();
                result.put("instances", listing.instances().stream().map(LauncherToolMain::instanceDto).toList());
                result.put("problems", listing.problems());
            } else {
                var snapshot = new MojangVersionCatalog(input.root(), clock, transport).load(input.options().containsKey("--refresh"));
                result.put("source", snapshot.source().name()); result.put("fetchedAt", snapshot.fetchedAt().toString());
                result.put("warning", snapshot.warning());
                if (input.command().equals("catalog")) {
                    String type = input.options().get("--type");
                    var versions = snapshot.manifest().search(input.options().getOrDefault("--query", ""), type == null ? null : OfficialVersion.Type.fromWire(type));
                    result.put("latestRelease", snapshot.manifest().latestRelease());
                    result.put("latestSnapshot", snapshot.manifest().latestSnapshot());
                    result.put("count", versions.size());
                    result.put("versions", versions.stream().map(LauncherToolMain::versionDto).toList());
                } else result.put("instance", instanceDto(instances.create(input.options().get("--name"), input.options().get("--version"), snapshot.manifest())));
            }
            out.println(new String(LauncherJson.write(result), StandardCharsets.UTF_8)); out.flush();
            return 0;
        } catch (IllegalArgumentException error) {
            error(err, "INVALID_ARGUMENT", error.getMessage()); return 2;
        } catch (IOException error) {
            boolean cancelled = Thread.currentThread().isInterrupted();
            error(err, cancelled ? "CANCELLED" : "IO_ERROR", error.getMessage()); return cancelled ? 4 : 3;
        }
    }
    private static Map<String, Object> versionDto(OfficialVersion version) {
        return Map.of("id", version.id(), "type", version.type().wireName(), "url", version.metadataUrl().toString(),
                "sha1", version.sha1(), "releaseTime", version.releaseTime().toString(), "time", version.updatedAt().toString());
    }
    private static Map<String, Object> instanceDto(PreparedInstance prepared) {
        var instance = prepared.instance();
        return Map.of("id", instance.id(), "name", instance.name(), "gameVersion", instance.gameVersion(),
                "loader", instance.loader().name(), "directory", instance.directory().toString(),
                "createdAt", prepared.createdAt().toString(), "state", prepared.state().name(), "statusText", "已准备，未安装");
    }
    private static void error(PrintWriter writer, String kind, String message) {
        try { writer.println(new String(LauncherJson.write(Map.of("schemaVersion", 1, "error", kind, "message", message == null ? "操作未完成" : message)), StandardCharsets.UTF_8)); }
        catch (IOException impossible) { writer.println("{\"error\":\"IO_ERROR\",\"message\":\"无法输出结果\"}"); }
        writer.flush();
    }
    private record Arguments(Path root, String command, Map<String, String> options) {
        static Arguments parse(String[] args) {
            if (args == null || args.length < 3 || !args[0].equals("--data-root")) throw new IllegalArgumentException("请先指定 --data-root <绝对目录>，再选择 catalog、instances 或 create");
            Path root = Path.of(args[1]);
            if (!root.isAbsolute()) throw new IllegalArgumentException("--data-root 必须是绝对目录");
            String command = args[2];
            Set<String> allowed = switch (command) {
                case "catalog" -> Set.of("--refresh", "--query", "--type");
                case "create" -> Set.of("--name", "--version");
                case "instances" -> Set.of();
                default -> throw new IllegalArgumentException("仅支持 catalog、instances 和 create");
            };
            var options = new HashMap<String, String>();
            for (int index = 3; index < args.length; index++) {
                String flag = args[index];
                if (!allowed.contains(flag) || options.containsKey(flag)) throw new IllegalArgumentException("命令包含未知或重复参数");
                if (flag.equals("--refresh")) options.put(flag, "true");
                else {
                    if (++index == args.length) throw new IllegalArgumentException("命令缺少参数值");
                    options.put(flag, args[index]);
                }
            }
            if (options.containsKey("--type")) OfficialVersion.Type.fromWire(options.get("--type"));
            if (command.equals("create") && (!options.containsKey("--name") || !options.containsKey("--version"))) throw new IllegalArgumentException("create 需要 --name 和 --version");
            return new Arguments(root.normalize(), command, Map.copyOf(options));
        }
    }
}
