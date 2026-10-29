package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.launch.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.*;

/** 只接受受管 ID 和结构化设置；内部计划、命令与目录不经过 HTTP。 */
public final class LaunchRoutes implements LauncherRoutes {
    private final GameProcessService processes;
    private final LaunchSettingsStore settings;
    private final cn.dreamingfish.launcher.servers.ServerStore servers;
    public LaunchRoutes(GameProcessService processes, LaunchSettingsStore settings) { this(processes,settings,null); }
    public LaunchRoutes(GameProcessService processes, LaunchSettingsStore settings,cn.dreamingfish.launcher.servers.ServerStore servers) { this.processes = Objects.requireNonNull(processes); this.settings = Objects.requireNonNull(settings);this.servers=servers; }
    @Override public Optional<ApiResponse> handle(ApiRequest r) throws IOException {
        if (r.path().equals("/launches/close-status")) {
            r.requireMethod("GET"); r.onlyQuery();
            return Optional.of(new ApiResponse(200, Map.of("canClose", processes.canClose())));
        }
        if (r.path().matches("/instances/[^/]+/launch-settings")) {
            r.onlyQuery(); String id = r.path().split("/")[2]; TaskRequest.uuid(id);
            LaunchSettingsStore.VersionedSettings value;
            if (r.method().equals("GET")) value = settings.read(id);
            else { r.requireMethod("POST"); r.onlyFields(Set.of("revision", "settings")); value = settings.save(id, TaskRequest.uuid(r.text("revision", 36)), parseSettings(r.body().path("settings"))); }
            return Optional.of(new ApiResponse(200, settingsView(value)));
        }
        if (r.path().equals("/launch-checks")) {
            r.requireMethod("POST"); r.onlyQuery(); r.onlyFields(Set.of("instanceId", "identityId","serverId","serverRevision"));
            cn.dreamingfish.launcher.servers.ServerAddress target=null;
            if(r.body().has("serverId")||r.body().has("serverRevision")){if(servers==null)throw ApiRequest.invalid("当前入口未提供服务器直连");target=servers.require(r.text("serverRevision",36),r.text("serverId",36)).address();}
            return Optional.of(new ApiResponse(202, Map.of("check", checkView(processes.check(r.text("instanceId", 36), TaskRequest.uuid(r.text("identityId", 36)), new CancellationToken(),target)))));
        }
        if (r.path().startsWith("/launch-checks/")) {
            r.onlyQuery(); var parts = r.path().split("/", -1); if (parts.length < 3 || parts.length > 4 || parts.length == 4 && !parts[3].equals("cancel")) throw new LauncherApiException(404, "NOT_FOUND", "启动检查操作不存在");
            UUID id = TaskRequest.uuid(parts[2]); if (parts.length == 3) r.requireMethod("GET"); else { r.requireMethod("POST"); r.onlyFields(Set.of()); processes.cancelCheck(id); }
            return Optional.of(new ApiResponse(200, Map.of("check", checkView(processes.checkStatus(id)))));
        }
        if (r.path().equals("/launches")) {
            if (r.method().equals("GET")) {
                r.onlyQuery("instanceId"); String id = r.query().get("instanceId"); if (id == null) throw ApiRequest.invalid("需要实例 ID");
                return Optional.of(new ApiResponse(200, Map.of("launches", processes.list(id).stream().map(LaunchRoutes::launchView).toList())));
            }
            r.requireMethod("POST"); r.onlyQuery(); r.onlyFields(Set.of("checkId"));
            return Optional.of(new ApiResponse(202, Map.of("launch", launchView(processes.start(TaskRequest.uuid(r.text("checkId", 36)))))));
        }
        if (r.path().startsWith("/launches/")) {
            var parts = r.path().split("/", -1); if (parts.length != 4) throw new LauncherApiException(404, "NOT_FOUND", "运行操作不存在"); UUID id = TaskRequest.uuid(parts[2]);
            if (parts[3].equals("logs")) {
                r.requireMethod("GET"); r.onlyQuery("cursor", "limit"); String cursor = r.query().get("cursor"); if (cursor != null && cursor.length() > 64) throw ApiRequest.invalid("日志游标无效");
                return Optional.of(new ApiResponse(200, processes.logs(id, cursor, CatalogRoutes.number(r.query(), "limit", 200, 1, 500))));
            }
            if (parts[3].equals("stop")) {
                r.requireMethod("POST"); r.onlyQuery(); r.onlyFields(Set.of("confirmed")); if (!r.body().path("confirmed").isBoolean()) throw ApiRequest.invalid("需要明确确认是否结束进程");
                return Optional.of(new ApiResponse(202, Map.of("launch", launchView(processes.stop(id, r.body().path("confirmed").booleanValue())))));
            }
            throw new LauncherApiException(404, "NOT_FOUND", "运行操作不存在");
        }
        return Optional.empty();
    }
    private static LaunchSettings parseSettings(JsonNode node) {
        var fields = new HashSet<String>(); node.fieldNames().forEachRemaining(fields::add);
        if (!node.isObject() || !fields.equals(Set.of("javaId", "initialMemoryMiB", "maximumMemoryMiB", "width", "height", "extraJvmArgs", "acceptUnknownJava")) || !node.path("extraJvmArgs").isArray() || node.path("extraJvmArgs").size() > 32 || !node.path("acceptUnknownJava").isBoolean()) throw ApiRequest.invalid("启动设置字段无效");
        var args = new ArrayList<String>(); for (var value : node.path("extraJvmArgs")) { if (!value.isTextual()) throw ApiRequest.invalid("JVM 参数必须为列表"); args.add(value.textValue()); }
        String java = node.path("javaId").isNull() ? null : cn.dreamingfish.launcher.storage.LauncherJson.text(node, "javaId", 64);
        return new LaunchSettings(java, integer(node, "initialMemoryMiB"), integer(node, "maximumMemoryMiB"), integer(node, "width"), integer(node, "height"), args, node.path("acceptUnknownJava").booleanValue());
    }
    private static int integer(JsonNode node, String key) { if (!node.path(key).isInt()) throw ApiRequest.invalid("启动设置数值无效"); return node.path(key).intValue(); }
    private static Map<String, Object> settingsView(LaunchSettingsStore.VersionedSettings versioned) {
        var s = versioned.settings(); var fields = new LinkedHashMap<String, Object>(); fields.put("javaId", s.javaId()); fields.put("initialMemoryMiB", s.initialMemoryMiB()); fields.put("maximumMemoryMiB", s.maximumMemoryMiB()); fields.put("width", s.width()); fields.put("height", s.height()); fields.put("extraJvmArgs", s.extraJvmArgs()); fields.put("acceptUnknownJava", s.acceptUnknownJava()); return Map.of("revision", versioned.revision().toString(), "settings", fields);
    }
    private static Map<String, Object> checkView(GameProcessService.CheckSnapshot s) {
        var result = new LinkedHashMap<String, Object>(); result.put("id", s.id().toString()); result.put("instanceId", s.instanceId()); result.put("state", s.state().name()); result.put("createdAt", s.createdAt().toString()); result.put("expiresAt", s.expiresAt().toString()); result.put("installationId", s.installationId()); result.put("versionId", s.versionId()); result.put("javaMajor", s.javaMajor()); result.put("errorCode", s.errorCode()); result.put("message", s.message()); return result;
    }
    private static Map<String, Object> launchView(LaunchSnapshot s) {
        var result = new LinkedHashMap<String, Object>(); result.put("id", s.id().toString()); result.put("instanceId", s.instanceId()); result.put("installationId", s.installationId()); result.put("state", s.state().name()); result.put("createdAt", s.createdAt().toString()); result.put("startedAt", s.startedAt() == null ? null : s.startedAt().toString()); result.put("endedAt", s.endedAt() == null ? null : s.endedAt().toString()); result.put("exitCode", s.exitCode()); result.put("errorCode", s.errorCode()); result.put("message", s.message()); return result;
    }
}
