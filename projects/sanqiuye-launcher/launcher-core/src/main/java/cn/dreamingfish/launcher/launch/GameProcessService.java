package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.*;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.identity.OfflineIdentitySession;
import cn.dreamingfish.launcher.instances.FileInstanceCatalog;
import cn.dreamingfish.launcher.options.*;
import cn.dreamingfish.launcher.mods.InstanceModStore;
import cn.dreamingfish.launcher.runtime.*;
import cn.dreamingfish.launcher.storage.*;
import cn.dreamingfish.launcher.vanilla.*;
import java.io.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static cn.dreamingfish.launcher.launch.LaunchSnapshot.State.*;

/** 只执行内部固定计划；持有本次创建的进程能力，历史 PID 永不授予停止权限。 */
public final class GameProcessService implements AutoCloseable {
    public enum CheckState { CHECKING, READY, FAILED, CANCELLED, CONSUMED }
    public record CheckSnapshot(UUID id, String instanceId, CheckState state, Instant createdAt, Instant expiresAt,
                                String installationId, String versionId, Integer javaMajor, String errorCode, String message) { }
    private static final Duration CHECK_LIFETIME = Duration.ofMinutes(5);
    private final LauncherStorage storage;
    private final FileInstanceCatalog instances;
    private final VanillaInstaller installer;
    private final JavaRuntimeService runtimes;
    private final OfflineIdentitySession identities;
    private final LaunchSettingsStore settings;
    private final GameOptionsStore options;
    private final InstanceModStore mods;
    @FunctionalInterface interface ModVerification { String verify(InstallationReceipt core, CancellationToken token)throws IOException; }
    private final ModVerification modVerification;
    private final VanillaLaunchPlanner planner;
    private final RuntimeLayout layout;
    private final Clock clock;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID, Check> checks = new LinkedHashMap<>();
    private final Map<UUID, Run> runs = new LinkedHashMap<>();
    private boolean closed;
    private static final class Check {
        final UUID id = UUID.randomUUID(); final String instance; final Instant created; final CancellationToken token = new CancellationToken();
        final cn.dreamingfish.launcher.servers.ServerAddress server;
        CheckSnapshot snapshot; LaunchPlan plan; GameOptionsStore.Snapshot options; String modsFingerprint;
        Check(String instance, Instant now,cn.dreamingfish.launcher.servers.ServerAddress server) { this.instance = instance;this.server=server; created = now; snapshot = new CheckSnapshot(id, instance, CheckState.CHECKING, now, now.plus(CHECK_LIFETIME), null, null, null, null, "正在复验安装与运行环境"); }
    }
    private static final class Run {
        final UUID id = UUID.randomUUID(); final LaunchPlan plan; final GameOptionsStore.Snapshot options; final CancellationToken token = new CancellationToken();
        final String modsFingerprint; final LaunchLogStore logs; LauncherStorage.Lease runningLock, installLock; LaunchSnapshot snapshot;
        cn.dreamingfish.launcher.servers.ServerAddress server;
        Process process; Long pid; Instant processStartedAt;
        Run(LaunchPlan plan, GameOptionsStore.Snapshot options, String modsFingerprint, Instant now, Path root) {
            this.plan = plan; this.options = options; this.modsFingerprint = modsFingerprint; logs = new LaunchLogStore(root, "instances/" + plan.instanceId() + "/launches/" + id);
            snapshot = new LaunchSnapshot(id, plan.instanceId(), plan.installationId(), PREPARING, now, null, null, null, null, "正在准备本实例运行文件");
        }
    }
    public GameProcessService(Path root, FileInstanceCatalog instances, VanillaInstaller installer, JavaRuntimeService runtimes,
                              OfflineIdentitySession identities, LaunchSettingsStore settings, Clock clock) {
        this(root,instances,installer,runtimes,identities,settings,clock,null);
    }
    GameProcessService(Path root, FileInstanceCatalog instances, VanillaInstaller installer, JavaRuntimeService runtimes,
                       OfflineIdentitySession identities, LaunchSettingsStore settings, Clock clock, ModVerification verification) {
        storage = new LauncherStorage(root); this.instances = Objects.requireNonNull(instances); this.installer = Objects.requireNonNull(installer);
        this.runtimes = Objects.requireNonNull(runtimes); this.identities = Objects.requireNonNull(identities); this.settings = Objects.requireNonNull(settings); this.clock = Objects.requireNonNull(clock);
        planner = new VanillaLaunchPlanner(root); layout = new RuntimeLayout(root); options = new GameOptionsStore(root); mods = new InstanceModStore(root, installer, clock); modVerification = verification==null?mods::verifyForLaunch:verification;
    }
    public synchronized CheckSnapshot check(String instanceId, UUID identityId, CancellationToken parent) throws IOException {
        return check(instanceId,identityId,parent,null);
    }
    public synchronized CheckSnapshot check(String instanceId, UUID identityId, CancellationToken parent,cn.dreamingfish.launcher.servers.ServerAddress server) throws IOException {
        requireOpen(); GameInstance instance = instance(instanceId); GameIdentity identity = identities.require(identityId); parent.check();
        if(server!=null)server.command(List.of(),instance.gameVersion());
        checks.values().removeIf(value -> !clock.instant().isBefore(value.created.plus(CHECK_LIFETIME)) && value.snapshot.state() != CheckState.CHECKING);
        if (checks.size() >= 16) throw failure("CHECK_LIMIT", "启动检查已达 16 项，请取消或等待旧检查过期");
        var check = new Check(instanceId, clock.instant(),server); checks.put(check.id, check);
        var registration = parent.onCancel(check.token::cancel);
        workers.submit(() -> {
            try (registration) {
              mods.recoverPending(instanceId, check.token);
              try (var lock = storage.lock(base(instanceId).resolve("installations/.install.lock"))) {
                var receipt = installer.verifyActive(instanceId, check.token); var versioned = settings.read(instanceId);
                checkMemory(versioned.settings(), availableMemory());
                var java = chooseJava(receipt, versioned.settings());
                var plan = planner.plan(instance, receipt, java, identity, versioned, check.token);
                var template = options.read(); GameOptionsSync.requireCompatible(instance.gameVersion(), template);
                String fingerprint = modVerification.verify(receipt, check.token);
                check.token.check();
                synchronized (this) {
                    if (closed || !clock.instant().isBefore(check.created.plus(CHECK_LIFETIME))) throw new InterruptedIOException();
                    check.modsFingerprint = fingerprint; check.plan = plan; check.options = template; check.snapshot = new CheckSnapshot(check.id, instanceId, CheckState.READY, check.created, check.created.plus(CHECK_LIFETIME), plan.installationId(), instance.gameVersion(), java.majorVersion(), null, "检查完成；启动前仍会核对安装与设置");
                }
              }
            } catch (Exception error) { synchronized (this) { check.snapshot = new CheckSnapshot(check.id, instanceId, error instanceof InterruptedIOException ? CheckState.CANCELLED : CheckState.FAILED, check.created, check.created.plus(CHECK_LIFETIME), null, null, null, code(error), message(error)); } }
        });
        return check.snapshot;
    }
    public synchronized CheckSnapshot checkStatus(UUID id) throws LaunchFailure { return requireCheck(id).snapshot; }
    public synchronized void cancelCheck(UUID id) throws LaunchFailure {
        var check = requireCheck(id); check.token.cancel();
        if (check.snapshot.state() == CheckState.READY) check.snapshot = new CheckSnapshot(id, check.instance, CheckState.CANCELLED, check.created, check.created.plus(CHECK_LIFETIME), null, null, null, "CANCELLED", "启动检查已取消");
    }
    public LaunchSnapshot start(UUID checkId) throws IOException {
        final Check check; final Run run;
        synchronized (this) {
            requireOpen(); check = requireCheck(checkId); requireReady(check);
            run = new Run(check.plan, check.options, check.modsFingerprint, clock.instant(), storage.root());
            run.server=check.server;
        }
        try {
            run.runningLock = storage.lock(base(check.instance).resolve(".running.lock"));
            ensureNoHistoricalProcess(check.instance);
            run.installLock = storage.lock(base(check.instance).resolve("installations/.install.lock"));
            // 大目录扫描仅持有本实例租约，不阻塞其他实例的停止、取消及状态读取。
            fresh(check.plan, check.options.revision(), check.modsFingerprint, check.token);
            synchronized (this) {
                requireOpen(); check.token.check(); requireReady(check);
                freshMetadata(check.plan, check.options.revision());
                persist(run); runs.put(run.id, run);
                check.snapshot = new CheckSnapshot(check.id, check.instance, CheckState.CONSUMED, check.created, check.created.plus(CHECK_LIFETIME), check.plan.installationId(), check.plan.instance().gameVersion(), check.plan.javaRuntime().majorVersion(), null, "已提交一次启动");
                workers.submit(() -> execute(run)); return run.snapshot;
            }
        } catch (IOException | RuntimeException error) { release(run); if (error instanceof LauncherBusyException) throw failure("INSTANCE_BUSY", "实例正在安装、准备或运行，请稍后再试"); throw error; }
    }
    private void requireReady(Check check)throws LaunchFailure {
        if (check.snapshot.state() != CheckState.READY || !clock.instant().isBefore(check.created.plus(CHECK_LIFETIME))) throw failure("CHECK_EXPIRED", "启动检查已使用、失效或未完成，请重新检查");
    }
    private void execute(Run run) {
        try {
            run.token.check(); fresh(run.plan, run.options.revision(), run.modsFingerprint, run.token); var receipt = installer.verifyActive(run.plan.instanceId(), run.token);
            var java = new JavaProbe().probe(run.plan.javaRuntime().executable(), run.token);
            if (!java.id().equals(run.plan.javaRuntime().id()) || java.majorVersion() != run.plan.javaRuntime().majorVersion() || java.bits() != run.plan.javaRuntime().bits()) throw failure("JAVA_INCOMPATIBLE", "Java 已变化，请重新检查");
            // 再次解析同一份安装，拒绝检查后发生的文件变化，不采用新的活动安装。
            var pinned = planner.plan(run.plan.instance(), receipt, java, run.plan.identity(), settings.read(run.plan.instanceId()), run.token);
            checkMemory(settings.read(run.plan.instanceId()).settings(), availableMemory());
            var prepared = layout.prepare(pinned, run.id, run.token);
            fresh(run.plan, run.options.revision(), run.modsFingerprint, run.token);
            synchronized (this) {
              try (var templateLock = options.lock()) {
                requireOpen(); run.token.check(); freshMetadata(run.plan, run.options.revision());
                int applied = new GameOptionsSync(storage.root()).apply(run.plan.instanceId(), run.plan.instance().gameVersion(), run.id, run.options, run.token);
                update(run, STARTING, null, null, "正在创建游戏进程");
                var builder = new ProcessBuilder(run.server==null?prepared.command():run.server.command(prepared.command(),run.plan.instance().gameVersion())).directory(prepared.gameDirectory().toFile());
                for (String variable : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH")) builder.environment().remove(variable);
                run.process = builder.start(); run.pid = run.process.pid(); run.processStartedAt = run.process.info().startInstant().orElse(null);
                update(run, RUNNING, null, null, "游戏进程正在运行；此状态不代表已进入菜单" + (applied > 0 ? "；已同步" + applied + "项默认设置，原文件如存在则已备份到本实例options-backups" : ""));
                run.installLock.close(); run.installLock = null;
              }
            }
            Future<?> output = workers.submit(() -> collect(run, run.process.getInputStream(), "stdout"));
            Future<?> error = workers.submit(() -> collect(run, run.process.getErrorStream(), "stderr"));
            int exit = run.process.waitFor(); output.get(); error.get(); run.logs.close();
            synchronized (this) {
                if (!closed) {
                    boolean logFailure = "LOG_WRITE_FAILED".equals(run.snapshot.errorCode());
                    update(run, EXITED, exit, logFailure ? "LOG_WRITE_FAILED" : null, "游戏进程已退出，退出码 " + exit + (logFailure ? "；日志采集失败，请检查磁盘" : ""));
                }
            }
        } catch (Exception error) {
            synchronized (this) {
                try {
                    if (!closed) {
                        if (run.process != null && run.process.isAlive()) update(run, UNCERTAIN, null, "PROCESS_STATE_UNKNOWN", "进程状态或日志记录异常，请检查游戏窗口");
                        else update(run, error instanceof InterruptedIOException ? CANCELLED : FAILED, null, code(error), message(error));
                    }
                } catch (IOException recordFailure) { run.snapshot = snapshot(run, UNCERTAIN, null, "RECORD_WRITE_FAILED", "运行记录写入失败，请检查游戏窗口与磁盘"); }
            }
        } finally { synchronized (this) { release(run); } }
    }
    private void collect(Run run, InputStream stream, String source) {
        try { run.logs.consume(stream, source); }
        catch (IOException error) { synchronized (this) { if (!closed) { run.snapshot = snapshot(run, run.snapshot.state(), null, "LOG_WRITE_FAILED", "游戏日志采集失败，请检查磁盘与游戏窗口"); try { persist(run); } catch (IOException ignored) { /* 快照保留可读失败状态。 */ } } } }
    }
    public synchronized LaunchSnapshot stop(UUID id, boolean confirmed) throws IOException {
        requireOpen(); if (!confirmed) throw failure("CONFIRMATION_REQUIRED", "结束进程可能丢失未保存进度，请先确认");
        var run = runs.get(id); if (run == null) throw failure("PROCESS_NOT_OWNED", "本服务未持有此进程，不能结束历史进程");
        if (Set.of(EXITED, FAILED, CANCELLED).contains(run.snapshot.state())) return run.snapshot;
        update(run, STOPPING, null, null, "已请求结束进程；不代表游戏已经保存"); run.token.cancel();
        if (run.process != null) {
            Process process = run.process;
            var descendants = process.descendants().map(handle -> Map.entry(handle, handle.info().startInstant())).toList();
            workers.submit(() -> {
                process.destroy();
                try { if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    for (var child : descendants) if (child.getValue().isPresent() && child.getValue().equals(child.getKey().info().startInstant()) && child.getKey().isAlive()) child.getKey().destroyForcibly();
                    if (process.isAlive()) process.destroyForcibly();
                } } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
        }
        return run.snapshot;
    }
    public synchronized List<LaunchSnapshot> list(String instanceId) throws IOException {
        instance(instanceId); var result = new ArrayList<LaunchSnapshot>();
        for (Path folder : launchFolders(instanceId)) {
            UUID id = UUID.fromString(folder.getFileName().toString()); var run = runs.get(id);
            result.add(run == null ? historical(folder).snapshot() : run.snapshot);
        }
        result.sort(Comparator.comparing(LaunchSnapshot::createdAt).thenComparing(value -> value.id().toString())); return List.copyOf(result);
    }
    /** 关闭判断使用实际进程能力；历史PID只读核对，不接管或结束。 */
    public synchronized boolean canClose() throws IOException {
        for (var run : runs.values()) {
            if (Set.of(PREPARING, STARTING, RUNNING, STOPPING).contains(run.snapshot.state())
                    || run.process != null && run.process.isAlive()) return false;
        }
        var listing = instances.listing();
        if (!listing.problems().isEmpty()) throw failure("RECORD_INVALID", "实例记录异常，请检查后再关闭");
        for (var entry : listing.instances()) for (Path folder : launchFolders(entry.instance().id())) {
            if (runs.containsKey(UUID.fromString(folder.getFileName().toString()))) continue;
            var record = historical(folder);
            if (record.snapshot.state() != UNCERTAIN) continue;
            if (record.pid == null) return false;
            var process = ProcessHandle.of(record.pid);
            if (process.isPresent() && process.get().isAlive()) {
                var start = process.get().info().startInstant();
                if (record.processStartedAt == null || start.isEmpty() || start.get().equals(record.processStartedAt)) return false;
            }
        }
        return true;
    }
    public synchronized LaunchLogStore.LogPage logs(UUID id, String cursor, int limit) throws IOException {
        var run = runs.get(id); if (run != null) return run.logs.page(cursor, limit);
        for (var instance : instances.listing().instances()) for (Path folder : launchFolders(instance.instance().id())) if (folder.getFileName().toString().equals(id.toString())) {
            historical(folder); return new LaunchLogStore(storage.root(), folder.toString()).page(cursor, limit);
        }
        throw failure("LAUNCH_NOT_FOUND", "没有找到该启动记录");
    }
    private JavaRuntime chooseJava(InstallationReceipt receipt, LaunchSettings value) throws IOException {
        if (value.javaId() != null) try { return runtimes.require(value.javaId()); } catch (IOException absent) { throw failure("JAVA_NOT_FOUND", "所选 Java 尚未登记，请重新检测"); }
        return runtimes.select(receipt.requiredJavaMajor() == null ? 8 : receipt.requiredJavaMajor(), receipt.platform().bits()).orElseThrow(() -> failure("JAVA_NOT_FOUND", "没有符合本版本要求的 Java，请检测或选择安装目录"));
    }
    static void checkMemory(LaunchSettings value, long availableBytes) throws LaunchFailure {
        if (availableBytes >= 0 && value.maximumMemoryMiB() * 1024L * 1024 > availableBytes) throw failure("MEMORY_INSUFFICIENT", "设置的最大内存超过当前可用物理内存，请降低内存设置或关闭其他程序后重新检查");
    }
    private static long availableMemory() {
        var system = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        return system instanceof com.sun.management.OperatingSystemMXBean memory ? memory.getFreeMemorySize() : -1;
    }
    private void fresh(LaunchPlan plan, UUID optionsRevision, String modsFingerprint, CancellationToken token) throws IOException {
        freshMetadata(plan, optionsRevision);
        if (!modVerification.verify(plan.installation(), token).equals(modsFingerprint)) throw failure("MOD_FILES_CHANGED", "模组目录已变化，请重新检查后启动");
    }
    private void freshMetadata(LaunchPlan plan, UUID optionsRevision)throws IOException {
        if (!options.read().revision().equals(optionsRevision)) throw failure("OPTIONS_CHANGED", "默认游戏设置已变化，请重新检查");
        if (!settings.read(plan.instanceId()).revision().equals(plan.settingsRevision())) throw failure("SETTINGS_CHANGED", "启动设置已变化，请重新检查");
        var listing = installer.listing(plan.instanceId());
        if (listing.installations().size() != 1) throw failure("INSTALLATION_INVALID", "活动安装不可用，请检查安装记录");
        if (!listing.installations().getFirst().equals(plan.installation())) throw failure("INSTALLATION_CHANGED", "活动安装已变化，请重新检查");
    }
    private GameInstance instance(String id) throws IOException {
        TaskRequest.uuid(id); return instances.listing().instances().stream().map(value -> value.instance()).filter(value -> value.id().equals(id)).findFirst().orElseThrow(() -> failure("INSTANCE_NOT_FOUND", "隔离实例不存在或记录无效"));
    }
    private Check requireCheck(UUID id) throws LaunchFailure { var value = checks.get(id); if (value == null) throw failure("CHECK_NOT_FOUND", "没有找到该启动检查，请重新检查"); return value; }
    private void requireOpen() throws LaunchFailure { if (closed) throw failure("SERVICE_CLOSED", "本机启动服务已关闭"); }
    private static Path base(String id) { TaskRequest.uuid(id); return Path.of("instances", id); }
    private static Path record(Run run) { return base(run.plan.instanceId()).resolve("launches").resolve(run.id.toString()).resolve("launch.json"); }
    private LaunchSnapshot snapshot(Run run, LaunchSnapshot.State state, Integer exit, String code, String message) {
        var old = run.snapshot; Instant started = old.startedAt(); if (started == null && run.process != null) started = clock.instant();
        Instant ended = Set.of(EXITED, FAILED, CANCELLED).contains(state) ? clock.instant() : null;
        return new LaunchSnapshot(run.id, run.plan.instanceId(), run.plan.installationId(), state, old.createdAt(), started, ended, exit, code, message);
    }
    private void update(Run run, LaunchSnapshot.State state, Integer exit, String code, String message) throws IOException { run.snapshot = snapshot(run, state, exit, code, message); persist(run); }
    private void persist(Run run) throws IOException {
        var value = run.snapshot; var fields = new LinkedHashMap<String, Object>();
        fields.put("schemaVersion", 1); fields.put("id", value.id().toString()); fields.put("instanceId", value.instanceId()); fields.put("installationId", value.installationId());
        fields.put("state", value.state().name()); fields.put("createdAt", value.createdAt().toString()); fields.put("startedAt", string(value.startedAt())); fields.put("endedAt", string(value.endedAt()));
        fields.put("exitCode", value.exitCode()); fields.put("errorCode", value.errorCode()); fields.put("message", value.message()); fields.put("pid", run.pid); fields.put("processStartedAt", string(run.processStartedAt));
        byte[] bytes = LauncherJson.write(fields); if (bytes.length > 1024 * 1024) throw new IOException("运行记录超过上限"); storage.writeAtomically(record(run), bytes);
    }
    private record Historical(LaunchSnapshot snapshot, Long pid, Instant processStartedAt) { }
    private Historical historical(Path folder) throws IOException {
        try {
            var node = LauncherJson.read(storage.read(folder.resolve("launch.json"), 1024 * 1024), 1024 * 1024);
            var keys = new HashSet<String>(); node.fieldNames().forEachRemaining(keys::add);
            if (!keys.equals(Set.of("schemaVersion", "id", "instanceId", "installationId", "state", "createdAt", "startedAt", "endedAt", "exitCode", "errorCode", "message", "pid", "processStartedAt")) || !node.path("schemaVersion").isInt() || node.path("schemaVersion").intValue() != 1) throw new IOException();
            UUID id = TaskRequest.uuid(LauncherJson.text(node, "id", 36)); String instance = LauncherJson.text(node, "instanceId", 36), installation = LauncherJson.text(node, "installationId", 36); TaskRequest.uuid(installation);
            if (!folder.equals(base(instance).resolve("launches").resolve(id.toString()))) throw new IOException();
            var state = LaunchSnapshot.State.valueOf(LauncherJson.text(node, "state", 32));
            Instant created = Instant.parse(LauncherJson.text(node, "createdAt", 64)), started = instant(node, "startedAt"), ended = instant(node, "endedAt");
            Integer exit = node.path("exitCode").isNull() ? null : node.path("exitCode").isInt() ? node.path("exitCode").intValue() : invalidInteger();
            Long pid = node.path("pid").isNull() ? null : node.path("pid").canConvertToLong() && node.path("pid").isIntegralNumber() && node.path("pid").longValue() > 0 ? node.path("pid").longValue() : invalidLong();
            Instant processStarted = instant(node, "processStartedAt"); String code = node.path("errorCode").isNull() ? null : LauncherJson.text(node, "errorCode", 64), message = LauncherJson.text(node, "message", 512);
            if (!Set.of(EXITED, FAILED, CANCELLED).contains(state)) { state = UNCERTAIN; code = "PROCESS_STATE_UNKNOWN"; message = "后端已重启，历史进程状态待确认；本服务不会接管或结束它"; }
            return new Historical(new LaunchSnapshot(id, instance, installation, state, created, started, ended, exit, code, message), pid, processStarted);
        } catch (IOException | RuntimeException invalid) { throw failure("RECORD_INVALID", "启动历史记录损坏，原文件已保留，请检查后重试"); }
    }
    private List<Path> launchFolders(String instanceId) throws IOException {
        var folders = new ArrayList<Path>();
        for (Path folder : storage.children(base(instanceId).resolve("launches"))) {
            try { TaskRequest.uuid(folder.getFileName().toString()); } catch (IllegalArgumentException ignored) { continue; }
            storage.requireDirectory(folder); if (storage.exists(folder.resolve("launch.json"))) folders.add(folder);
        }
        return folders;
    }
    private void ensureNoHistoricalProcess(String instanceId) throws IOException {
        HistoricalProcessGuard.ensureIdle(storage,instanceId);
        for (Path folder : launchFolders(instanceId)) {
            var record = historical(folder);
            if (record.snapshot.state() != UNCERTAIN) continue;
            if (record.pid == null) throw failure("INSTANCE_BUSY", "存在状态待确认的启动记录，请先检查游戏窗口");
            var process = ProcessHandle.of(record.pid);
            if (process.isPresent() && process.get().isAlive()) {
                var start = process.get().info().startInstant();
                if (record.processStartedAt == null || start.isEmpty() || start.get().equals(record.processStartedAt)) throw failure("INSTANCE_BUSY", "上次游戏进程可能仍在运行，请先在游戏窗口退出");
            }
        }
    }
    private static String string(Instant value) { return value == null ? null : value.toString(); }
    private static Instant instant(com.fasterxml.jackson.databind.JsonNode node, String key) { return node.path(key).isNull() ? null : Instant.parse(LauncherJson.text(node, key, 64)); }
    private static Integer invalidInteger() { throw new IllegalArgumentException(); }
    private static Long invalidLong() { throw new IllegalArgumentException(); }
    private static LaunchFailure failure(String code, String message) { return new LaunchFailure(code, message); }
    private static String code(Exception error) {
        if (error instanceof LaunchFailure value) return value.code();
        if (error instanceof TaskFailure value && value.code().startsWith("MOD_")) return value.code();
        if (error instanceof TaskFailure value) return Set.of("FILE_CHANGED", "FILE_MISSING").contains(value.code()) ? "FILE_CHANGED" : "INSTALLATION_INVALID";
        if (error instanceof InterruptedIOException) return "CANCELLED";
        if (error instanceof LauncherBusyException) return "INSTANCE_BUSY";
        return "PROCESS_START_FAILED";
    }
    private static String message(Exception error) {
        if (error instanceof LaunchFailure || error instanceof TaskFailure) return error.getMessage();
        return switch (code(error)) { case "CANCELLED" -> "启动操作已取消，其他实例未被改动"; case "INSTANCE_BUSY" -> "实例正在被其他任务使用，请稍后重试"; default -> "游戏准备或进程创建失败，请重新检查文件与运行环境"; };
    }
    private static void release(Run run) {
        for (var lock : new LauncherStorage.Lease[]{run.installLock, run.runningLock}) if (lock != null) try { lock.close(); } catch (IOException ignored) { /* 已关闭的句柄不产生进程所有权。 */ }
        run.installLock = null; run.runningLock = null;
    }
    @Override public synchronized void close() {
        if (closed) return; closed = true;
        for (var check : checks.values()) check.token.cancel();
        for (var run : runs.values()) {
            run.token.cancel();
            if (!Set.of(EXITED, FAILED, CANCELLED).contains(run.snapshot.state())) try {
                // 与创建进程共用同步保护：只有确定未创建的任务可以直接取消。
                if (run.process == null) update(run, CANCELLED, null, "CANCELLED", "本机后端关闭，尚未创建游戏进程的启动已取消");
                else update(run, UNCERTAIN, null, "PROCESS_STATE_UNKNOWN", "本机后端关闭，游戏状态待确认，不再承诺继续采集日志");
            } catch (IOException ignored) { run.snapshot = snapshot(run, UNCERTAIN, null, "RECORD_WRITE_FAILED", "后端关闭时记录写入失败，请检查游戏窗口"); }
            release(run);
        }
        workers.shutdown();
    }
}
