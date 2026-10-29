package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.identity.OfflineIdentitySession;
import cn.dreamingfish.launcher.instances.FileInstanceCatalog;
import cn.dreamingfish.launcher.options.GameOptionsStore;
import cn.dreamingfish.launcher.runtime.JavaRuntimeService;
import cn.dreamingfish.launcher.storage.*;
import cn.dreamingfish.launcher.vanilla.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.jar.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class GameProcessServiceTest {
    @TempDir Path root;
    final Clock clock = Clock.systemUTC();
    final OfflineIdentitySession identities = new OfflineIdentitySession();
    final JavaRuntimeService runtimes = new JavaRuntimeService();
    VanillaLaunchPlannerTest.Installed installed(String version, boolean wait) throws Exception {
        var owner = new VanillaLaunchPlannerTest(); owner.temporary = root;
        var f = owner.new Installed(root, version, Runtime.version().feature(), null);
        var bytes = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes)) {
            String name = ProcessFixtureMain.class.getName().replace('.', '/') + ".class";
            jar.putNextEntry(new JarEntry(name)); try (var input = ProcessFixtureMain.class.getClassLoader().getResourceAsStream(name)) { input.transferTo(jar); } jar.closeEntry();
        }
        f.put("versions/client.jar", bytes.toByteArray());
        f.metadata.put("downloads", Map.of("client", Map.of("size", bytes.size(), "sha1", VanillaLaunchPlannerTest.hash("SHA-1", bytes.toByteArray()))));
        f.metadata.put("libraries", List.of()); f.metadata.remove("logging"); f.metadata.put("mainClass", ProcessFixtureMain.class.getName());
        var game = new ArrayList<>(List.of("--gameDir", "${game_directory}", "--username", "${auth_player_name}", "--fixture-value", "中文 空格;&")); if (wait) game.add("--fixture-wait");
        f.metadata.put("arguments", Map.of("jvm", List.of("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-cp", "${classpath}"), "game", game)); f.finish();
        var storage = new LauncherStorage(root);
        storage.writeAtomically(Path.of("instances", f.id, "instance.json"), LauncherJson.write(Map.of("schemaVersion", 1, "id", f.id, "name", "进程 测试", "gameVersion", version, "loader", "VANILLA", "createdAt", clock.instant().toString(), "state", "PREPARED")));
        storage.writeAtomically(root.relativize(f.directory.resolve("installation.json")), f.receipt.bytes());
        storage.writeAtomically(Path.of("instances", f.id, "active-installation.json"), LauncherJson.write(Map.of("schemaVersion", 1, "instanceId", f.id, "installationId", f.installId, "versionId", version, "receiptSha256", VanillaLaunchPlannerTest.hash("SHA-256", f.receipt.bytes()))));
        Files.writeString(f.instance.directory().resolve("options.txt"), "配置哨兵"); Files.createDirectories(f.instance.directory().resolve("saves")); Files.writeString(f.instance.directory().resolve("saves/sentinel"), "存档哨兵");
        return f;
    }
    GameProcessService service() throws Exception {
        runtimes.registerHome(Path.of(System.getProperty("java.home")), new CancellationToken());
        return new GameProcessService(root, new FileInstanceCatalog(root, clock), new VanillaInstaller(root, new FileInstanceCatalog(root, clock), null, null, clock), runtimes, identities, new LaunchSettingsStore(root), clock);
    }
    GameProcessService.CheckSnapshot ready(GameProcessService service, String id) throws Exception {
        var check = service.check(id, identities.create("Alice").id(), new CancellationToken());
        for (int i = 0; i < 400 && check.state() == GameProcessService.CheckState.CHECKING; i++) { Thread.sleep(25); check = service.checkStatus(check.id()); }
        assertEquals(GameProcessService.CheckState.READY, check.state(), check.message()); return check;
    }
    LaunchSnapshot until(GameProcessService service, String instance, LaunchSnapshot.State state) throws Exception {
        LaunchSnapshot snapshot = null;
        for (int i = 0; i < 800; i++) { snapshot = service.list(instance).getLast(); if (snapshot.state() == state) return snapshot; if (snapshot.state() == LaunchSnapshot.State.FAILED) fail(snapshot.message()); Thread.sleep(25); }
        fail("进程状态未到达 " + state + ": " + snapshot); return null;
    }
    @Test void directTargetTravelsThroughCheckAndRealChildWithoutShell() throws Exception {
        var f=installed("1.20.6",false);
        try(var service=service()){
            var target=new cn.dreamingfish.launcher.servers.ServerAddress("play.example.org",25565);
            var check=service.check(f.id,identities.create("Alice").id(),new CancellationToken(),target);
            for(int i=0;i<400&&check.state()==GameProcessService.CheckState.CHECKING;i++){Thread.sleep(25);check=service.checkStatus(check.id());}
            assertEquals(GameProcessService.CheckState.READY,check.state(),check.message());service.start(check.id());var done=until(service,f.id,LaunchSnapshot.State.EXITED);assertEquals(7,done.exitCode());
            assertEquals(target.authority(),Files.readString(f.instance.directory().resolve("fixture-direct-target.txt")));
        }
    }
    @Test void realChildHasOwnedArgumentsAndBoundedLogs() throws Exception {
        var f = installed("test-one", false);
        try (var service = service()) {
            var check = ready(service, f.id); identities.select(identities.create("Bob").id()); var launch = service.start(check.id()); assertEquals(LaunchSnapshot.State.PREPARING, launch.state());
            var done = until(service, f.id, LaunchSnapshot.State.EXITED); assertEquals(7, done.exitCode());
            var text = new StringBuilder(); String cursor = null;
            for (int i = 0; i < 100; i++) { var page = service.logs(done.id(), cursor, 500); page.lines().forEach(line -> text.append(line).append('\n')); if (Objects.equals(cursor, page.nextCursor())) break; cursor = page.nextCursor(); }
            String output = text.toString(); assertTrue(output.contains("参数=中文 空格;&")); assertTrue(output.contains("工作目录=" + f.instance.directory())); assertTrue(output.contains("分块中文完整")); assertTrue(output.contains("末尾中文完整"));
            for (String key : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH")) assertTrue(output.contains(key + "=null"));
            assertTrue(output.contains("身份=Alice")); assertTrue(output.contains("注入属性=null"));
            String record = Files.readString(root.resolve("instances/" + f.id + "/launches/" + done.id() + "/launch.json")); assertFalse(record.contains("Alice")); assertFalse(record.contains("Bob"));
            assertEquals("配置哨兵", Files.readString(f.instance.directory().resolve("options.txt"))); assertEquals("存档哨兵", Files.readString(f.instance.directory().resolve("saves/sentinel")));
        }
    }
    @Test void rejectsHeapLargerThanAvailablePhysicalMemory() throws Exception {
        var settings = new LaunchSettings(null, 256, 512, 960, 540, List.of(), false);
        assertEquals("MEMORY_INSUFFICIENT", assertThrows(LaunchFailure.class, () -> GameProcessService.checkMemory(settings, 511L * 1024 * 1024)).code());
        assertDoesNotThrow(() -> GameProcessService.checkMemory(settings, 512L * 1024 * 1024));
    }
    @Test void changedGlobalOptionsInvalidateReadyCheckEvenWhenDisabled() throws Exception {
        var f=installed("test-options",false);
        try(var service=service()) {
            var check=ready(service,f.id);var options=new GameOptionsStore(root);
            options.save(options.read().revision(),false,Map.of("fov",90));
            assertEquals("OPTIONS_CHANGED",assertThrows(LaunchFailure.class,()->service.start(check.id())).code());
            assertFalse(Files.exists(f.instance.directory().resolve("fixture-started.txt")));
            assertEquals("配置哨兵",Files.readString(f.instance.directory().resolve("options.txt")));
        }
    }
    @Test void changedModsInvalidateReadyCheckAndCorruptJournalBlocksCheck()throws Exception {
        var f=installed("test-mods",false);
        try(var service=service()){
            var check=ready(service,f.id);Files.createDirectories(f.instance.directory().resolve("mods"));Files.writeString(f.instance.directory().resolve("mods/manual.jar"),"changed");
            assertEquals("MOD_FILES_CHANGED",assertThrows(LaunchFailure.class,()->service.start(check.id())).code());assertFalse(Files.exists(f.instance.directory().resolve("fixture-started.txt")));
            Files.writeString(root.resolve("instances/"+f.id+"/mods-transaction.json"),"{\"phase\":\"unknown\"}");var next=service.check(f.id,identities.create("Bob").id(),new CancellationToken());
            for(int i=0;i<400&&next.state()==GameProcessService.CheckState.CHECKING;i++){Thread.sleep(25);next=service.checkStatus(next.id());}
            assertEquals(GameProcessService.CheckState.FAILED,next.state());assertEquals("MOD_RECOVERY_REQUIRED",next.errorCode());
        }
    }
    @Test void appliesGlobalOptionsBeforeProcessOnlyToCurrentInstance() throws Exception {
        var f=installed("1.20.6",false);var other=installed("test-other-options",false);
        String original="version:3839\nfov:0.0\nlastServer:keep:25565\n";Files.writeString(f.instance.directory().resolve("options.txt"),original);
        var options=new GameOptionsStore(root);options.save(options.read().revision(),true,Map.of("fov",90));
        try(var service=service()) {
            var check=ready(service,f.id);service.start(check.id());var done=until(service,f.id,LaunchSnapshot.State.EXITED);
            assertEquals(7,done.exitCode());assertEquals("version:3839\nfov:0.5\nlastServer:keep:25565\n",Files.readString(f.instance.directory().resolve("options.txt")));
            assertEquals(original,Files.readString(root.resolve("instances/"+f.id+"/options-backups/"+done.id()+"-options.txt")));
            assertEquals("配置哨兵",Files.readString(other.instance.directory().resolve("options.txt")));
            assertEquals("存档哨兵",Files.readString(f.instance.directory().resolve("saves/sentinel")));
        }
    }
    @Test void staleCheckAndConcurrentStartNeverLaunchTwice() throws Exception {
        var first = installed("test-wait", true); var second = installed("test-other", true);
        try (var service = service()) {
            var stale = ready(service, first.id); var settings = new LaunchSettingsStore(root); var previous = settings.read(first.id); settings.save(first.id, previous.revision(), previous.settings());
            assertEquals("SETTINGS_CHANGED", assertThrows(LaunchFailure.class, () -> service.start(stale.id())).code()); assertFalse(Files.exists(first.instance.directory().resolve("fixture-started.txt")));
            var current = ready(service, first.id); var other = ready(service, second.id);
            var gate = new CountDownLatch(1);
            var attempts = new ArrayList<CompletableFuture<LaunchSnapshot>>();
            for (int i = 0; i < 2; i++) attempts.add(CompletableFuture.supplyAsync(() -> { try { gate.await(); return service.start(current.id()); } catch (Exception rejected) { return null; } }));
            gate.countDown(); var outcomes = attempts.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList(); assertEquals(1, outcomes.size());
            var one = outcomes.getFirst(); var two = service.start(other.id());
            until(service, first.id, LaunchSnapshot.State.RUNNING); until(service, second.id, LaunchSnapshot.State.RUNNING);
            assertEquals("CONFIRMATION_REQUIRED", assertThrows(LaunchFailure.class, () -> service.stop(one.id(), false)).code());
            service.stop(one.id(), true); until(service, first.id, LaunchSnapshot.State.EXITED);
            assertEquals(LaunchSnapshot.State.RUNNING, service.list(second.id).getLast().state());
            Files.writeString(second.instance.directory().resolve("fixture-exit.txt"), "exit"); assertEquals(7, until(service, second.id, LaunchSnapshot.State.EXITED).exitCode());
        }
    }
    @Test void changedInstallationAndCancellationCannotStartFromOldCheck() throws Exception {
        var f = installed("test-stale-install", false);
        try (var service = service()) {
            var old = ready(service, f.id); var storage = new LauncherStorage(root);
            var receipt = f.receipt;
            String changedId = UUID.randomUUID().toString();
            var changed = new InstallationReceipt(f.id, changedId, receipt.versionId(), receipt.metadataSha1(), clock.instant(), receipt.platform(), receipt.requiredJavaMajor(), receipt.files());
            storage.writeAtomically(Path.of("instances", f.id, "installations", changedId, "installation.json"), changed.bytes());
            storage.writeAtomically(Path.of("instances", f.id, "active-installation.json"), LauncherJson.write(Map.of("schemaVersion", 1, "instanceId", f.id, "installationId", changedId, "versionId", receipt.versionId(), "receiptSha256", VanillaLaunchPlannerTest.hash("SHA-256", changed.bytes()))));
            assertEquals("INSTALLATION_CHANGED", assertThrows(LaunchFailure.class, () -> service.start(old.id())).code());
            assertFalse(Files.exists(f.instance.directory().resolve("fixture-started.txt")));
            var token = new CancellationToken(); token.cancel(); assertThrows(InterruptedIOException.class, () -> service.check(f.id, identities.create("Bob").id(), token));
        }
    }
    @Test void logWriteFailureDoesNotHideExitOrBlockChild() throws Exception {
        var f = installed("test-log-error", false);
        @SuppressWarnings("unchecked") var args = (List<String>) ((Map<String, Object>) f.metadata.get("arguments")).get("game"); args.add("--fixture-output-gate"); f.finish();
        var storage = new LauncherStorage(root); storage.writeAtomically(root.relativize(f.directory.resolve("installation.json")), f.receipt.bytes());
        storage.writeAtomically(Path.of("instances", f.id, "active-installation.json"), LauncherJson.write(Map.of("schemaVersion", 1, "instanceId", f.id, "installationId", f.installId, "versionId", f.receipt.versionId(), "receiptSha256", VanillaLaunchPlannerTest.hash("SHA-256", f.receipt.bytes()))));
        try (var service = service()) {
            var launch = service.start(ready(service, f.id).id()); until(service, f.id, LaunchSnapshot.State.RUNNING);
            Path logs = root.resolve("instances/" + f.id + "/launches/" + launch.id() + "/logs"); Files.createDirectories(logs.resolve("000000000000000001.log"));
            Files.writeString(f.instance.directory().resolve("fixture-output.txt"), "go");
            var done = until(service, f.id, LaunchSnapshot.State.EXITED); assertEquals(7, done.exitCode()); assertEquals("LOG_WRITE_FAILED", done.errorCode());
        }
    }
    @Test void closeBeforeProcessCreationAllowsSameInstanceAfterRestart() throws Exception {
        var f = installed("test-close-preparing", false);
        UUID launchId;
        try (var service = service()) {
            var check = ready(service, f.id);
            // 持有创建进程共用的监视器，确定关闭发生在真实子进程产生之前。
            synchronized (service) {
                launchId = service.start(check.id()).id();
                service.close();
            }
        }
        assertFalse(Files.exists(f.instance.directory().resolve("fixture-started.txt")));
        try (var restarted = service()) {
            var cancelled = restarted.list(f.id).getLast();
            assertEquals(launchId, cancelled.id());
            assertEquals(LaunchSnapshot.State.CANCELLED, cancelled.state());
            assertNotNull(cancelled.endedAt());
            var next = restarted.start(ready(restarted, f.id).id());
            assertNotEquals(launchId, next.id());
            assertEquals(7, until(restarted, f.id, LaunchSnapshot.State.EXITED).exitCode());
        }
    }
    @Test void restartDoesNotAdoptOrKillReusedPid() throws Exception {
        var f = installed("test-restart", true); var service = service();
        var launch = service.start(ready(service, f.id).id()); until(service, f.id, LaunchSnapshot.State.RUNNING); service.close();
        try (var restarted = service()) {
            assertEquals(LaunchSnapshot.State.UNCERTAIN, restarted.list(f.id).getLast().state());
            assertFalse(restarted.canClose());
            assertEquals("PROCESS_NOT_OWNED", assertThrows(LaunchFailure.class, () -> restarted.stop(launch.id(), true)).code());
            assertEquals("INSTANCE_BUSY", assertThrows(LaunchFailure.class, () -> restarted.start(ready(restarted, f.id).id())).code());
            Path record = root.resolve("instances/" + f.id + "/launches/" + launch.id() + "/launch.json");
            var node = (com.fasterxml.jackson.databind.node.ObjectNode) LauncherJson.read(Files.readAllBytes(record), 1024 * 1024);
            var oldProcess = ProcessHandle.of(node.path("pid").longValue()).orElseThrow();
            node.put("processStartedAt", "1970-01-01T00:00:00Z"); Files.write(record, LauncherJson.write(node));
            // 模拟 PID 已重用：不得认领/终止旧进程，也不能错误地阻止下一次启动。
            try {
                for (int i = 0; i < 100 && !Files.exists(f.instance.directory().resolve("fixture-started.txt")); i++) Thread.sleep(20);
                assertTrue(oldProcess.isAlive());
                assertTrue(restarted.canClose(), "已证实PID被复用的历史记录不能永久阻止关闭");
                var next = restarted.start(ready(restarted, f.id).id()); assertNotEquals(launch.id(), next.id());
                until(restarted, f.id, LaunchSnapshot.State.EXITED); assertTrue(oldProcess.isAlive());
            } finally {
                Files.writeString(f.instance.directory().resolve("fixture-exit.txt"), "exit"); oldProcess.onExit().get(5, TimeUnit.SECONDS);
            }
        }
    }
    @Test void liveUncertainProcessBlocksCloseUntilItReallyExits() throws Exception {
        var f = installed("test-uncertain-close", true);
        try (var service = service()) {
            service.start(ready(service, f.id).id()); until(service, f.id, LaunchSnapshot.State.RUNNING);
            for (int i = 0; i < 100 && !Files.exists(f.instance.directory().resolve("fixture-started.txt")); i++) Thread.sleep(20);
            assertTrue(Files.exists(f.instance.directory().resolve("fixture-started.txt")));
            var field = GameProcessService.class.getDeclaredField("workers"); field.setAccessible(true);
            ((ExecutorService) field.get(service)).shutdownNow();
            var run = until(service, f.id, LaunchSnapshot.State.UNCERTAIN);
            var record = LauncherJson.read(Files.readAllBytes(root.resolve("instances/" + f.id + "/launches/" + run.id() + "/launch.json")), 1024 * 1024);
            var process = ProcessHandle.of(record.path("pid").longValue()).orElseThrow();
            try { assertTrue(process.isAlive()); assertFalse(service.canClose()); }
            finally { Files.writeString(f.instance.directory().resolve("fixture-exit.txt"), "exit"); process.onExit().get(5, TimeUnit.SECONDS); }
            assertTrue(service.canClose(), "本次UNCERTAIN进程已退出后允许关闭");
        }
    }
}
