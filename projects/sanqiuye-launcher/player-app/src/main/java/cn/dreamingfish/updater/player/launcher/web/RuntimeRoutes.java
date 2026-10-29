package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.runtime.*;
import cn.dreamingfish.launcher.vanilla.Platform;
import java.io.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 只在明确 POST 后检测；任务表最多四项、一个活动检测，不把完整路径返回界面。 */
public final class RuntimeRoutes implements LauncherRoutes, AutoCloseable {
    private final JavaRuntimeService runtimes;
    private final Clock clock;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon(true).name("sqy-java-discovery").factory());
    private final LinkedHashMap<UUID, Discovery> discoveries = new LinkedHashMap<>();
    private boolean closed;
    private static final class Discovery {
        final UUID id = UUID.randomUUID(); final Instant created; final CancellationToken token = new CancellationToken();
        String state = "RUNNING", code, message = "正在检测已知 Java 安装位置"; Instant updated;
        Discovery(Instant now) { created = now; updated = now; }
    }
    public RuntimeRoutes(JavaRuntimeService runtimes, Clock clock) { this.runtimes = Objects.requireNonNull(runtimes); this.clock = Objects.requireNonNull(clock); }
    @Override public synchronized Optional<ApiResponse> handle(ApiRequest r) throws IOException {
        if (!r.path().equals("/runtimes") && !r.path().startsWith("/runtimes/")) return Optional.empty();
        r.onlyQuery();
        if (r.path().equals("/runtimes")) {
            if (r.method().equals("GET")) return Optional.of(new ApiResponse(200, listing()));
            r.requireMethod("POST"); r.onlyFields(Set.of("home"));
            if (closed) throw new LauncherApiException(503, "SERVICE_CLOSED", "Java 检测服务已关闭");
            try { runtimes.registerHome(Path.of(r.text("home", 4096)), new CancellationToken()); }
            catch (IOException error) { throw new LauncherApiException(400, "JAVA_NOT_FOUND", "所选目录未检测到可用 Java，请选择包含 bin/java 的安装目录"); }
            return Optional.of(new ApiResponse(201, listing()));
        }
        if (r.path().equals("/runtimes/discover")) {
            r.requireMethod("POST"); r.onlyFields(Set.of());
            if (closed) throw new LauncherApiException(503, "SERVICE_CLOSED", "Java 检测服务已关闭");
            if (discoveries.values().stream().anyMatch(value -> value.state.equals("RUNNING"))) throw new LauncherApiException(409, "DISCOVERY_BUSY", "已有 Java 检测正在进行");
            discoveries.values().removeIf(value -> !value.created.plusSeconds(300).isAfter(clock.instant()));
            if (discoveries.size() >= 4) discoveries.remove(discoveries.keySet().iterator().next());
            var task = new Discovery(clock.instant()); discoveries.put(task.id, task);
            worker.submit(() -> {
                String state = "COMPLETED", code = null, message = "Java 检测完成；仅列出实际可用环境";
                try { runtimes.discover(task.token); task.token.check(); }
                catch (InterruptedIOException cancelled) { state = "CANCELLED"; code = "CANCELLED"; message = "Java 检测已取消"; }
                catch (IOException | RuntimeException error) { state = "FAILED"; code = "JAVA_DISCOVERY_FAILED"; message = "Java 检测未完成，请手选安装目录或重试"; }
                synchronized (this) { task.state = state; task.code = code; task.message = message; task.updated = clock.instant(); }
            });
            return Optional.of(new ApiResponse(202, Map.of("discovery", view(task))));
        }
        String[] parts = r.path().split("/", -1);
        if (parts.length < 4 || parts.length > 5 || !parts[2].equals("discover") || parts.length == 5 && !parts[4].equals("cancel")) throw new LauncherApiException(404, "NOT_FOUND", "Java 操作不存在");
        UUID id = TaskRequest.uuid(parts[3]); var task = discoveries.get(id);
        if (task == null) throw new LauncherApiException(404, "DISCOVERY_NOT_FOUND", "检测记录不存在或已过期");
        if (parts.length == 4) r.requireMethod("GET");
        else { r.requireMethod("POST"); r.onlyFields(Set.of()); if (task.state.equals("RUNNING")) { task.token.cancel(); task.state = "CANCELLED"; task.code = "CANCELLED"; task.message = "Java 检测已取消"; task.updated = clock.instant(); } }
        return Optional.of(new ApiResponse(200, Map.of("discovery", view(task))));
    }
    private Map<String, Object> listing() {
        var result = new LinkedHashMap<String, Object>(); result.put("runtimes", runtimes.runtimes().stream().map(RuntimeRoutes::runtime).toList());
        result.put("diagnostics", runtimes.diagnostics().stream().map(value -> Map.of("message", value.message())).toList()); result.put("platformBits", Platform.current().bits()); return result;
    }
    private Map<String, Object> view(Discovery task) {
        var result = new LinkedHashMap<>(listing()); result.put("id", task.id.toString()); result.put("state", task.state); result.put("createdAt", task.created.toString()); result.put("updatedAt", task.updated.toString()); result.put("errorCode", task.code); result.put("message", task.message); return result;
    }
    private static Map<String, Object> runtime(JavaRuntime value) { return Map.of("id", value.id(), "majorVersion", value.majorVersion(), "bits", value.bits(), "vendor", value.vendor()); }
    @Override public synchronized void close() { closed = true; discoveries.values().forEach(value -> value.token.cancel()); worker.shutdownNow(); }
}
