package cn.dreamingfish.launcher.runtime;

import cn.dreamingfish.launcher.downloads.CancellationToken;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/** 受限短进程探测：双流同时消费，超时、超量和取消均终止本次探测。 */
public final class JavaProbe {
    @FunctionalInterface interface ProcessStarter { Process start(List<String> command) throws IOException; }
    private final Duration timeout;
    private final ProcessStarter starter;

    public JavaProbe() {
        this(Duration.ofSeconds(5), command -> {
            var builder = new ProcessBuilder(command);
            for (String name : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH")) {
                builder.environment().remove(name);
            }
            return builder.start();
        });
    }
    JavaProbe(Duration timeout, ProcessStarter starter) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("探测超时无效");
        this.timeout = timeout; this.starter = Objects.requireNonNull(starter);
    }

    public JavaRuntime probe(Path executable, CancellationToken token) throws IOException {
        token.check();
        Path actual = nativeExecutable(executable);
        long deadline = System.nanoTime() + timeout.toNanos();
        Process process = starter.start(List.of(actual.toString(), "-XshowSettings:properties", "-version"));
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        var byteCount = new AtomicInteger();
        var failure = new AtomicReference<IOException>();
        Thread stdout = read(process.getInputStream(), output, byteCount, failure, process);
        Thread stderr = read(process.getErrorStream(), errors, byteCount, failure, process);
        try (var registration = token.onCancel(process::destroyForcibly)) {
            process.getOutputStream().close();
            while (process.isAlive()) {
                token.check();
                if (failure.get() != null) throw failure.get();
                if (System.nanoTime() >= deadline) throw new IOException("Java 探测超时，未登记该环境");
                process.waitFor(30, TimeUnit.MILLISECONDS);
            }
            token.check();
            for (Thread reader : List.of(stdout, stderr)) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0 || !reader.join(Duration.ofNanos(remaining))) throw new IOException("Java 输出读取超时");
            }
            if (failure.get() != null) throw failure.get();
            if (process.exitValue() != 0) throw new IOException("Java 探测进程异常退出");
            return parse(actual, output.toString(StandardCharsets.UTF_8) + "\n" + errors.toString(StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new InterruptedIOException("Java 探测已取消");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            close(process.getInputStream()); close(process.getErrorStream()); close(process.getOutputStream());
        }
    }

    private static Thread read(InputStream input, ByteArrayOutputStream output, AtomicInteger byteCount,
                               AtomicReference<IOException> failure, Process process) {
        return Thread.ofVirtual().start(() -> {
            try (input) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (byteCount.addAndGet(count) > 65536) throw new IOException("Java 探测输出超过 64 KiB 上限");
                    output.write(buffer, 0, count);
                }
            } catch (IOException error) {
                failure.compareAndSet(null, new IOException("Java 探测输出无效或超过上限"));
                process.destroyForcibly();
            }
        });
    }
    private static Path nativeExecutable(Path path) throws IOException {
        if (path == null || !path.isAbsolute()) throw new IOException("请选择 Java 安装目录中的可执行文件");
        Path actual = path.toRealPath();
        String name = actual.getFileName().toString();
        if (!(name.equals("java") || name.equalsIgnoreCase("java.exe")) || !Files.isRegularFile(actual)) {
            throw new IOException("只接受规范 Java 可执行文件");
        }
        byte[] header;
        try (var input = Files.newInputStream(actual)) { header = input.readNBytes(4); }
        if (header.length != 4) throw new IOException("Java 可执行文件无效");
        int magic = ByteBuffer.wrap(header).getInt();
        boolean nativeFile = header[0] == 'M' && header[1] == 'Z' || magic == 0x7f454c46
                || magic == 0xfeedface || magic == 0xfeedfacf || magic == 0xcefaedfe
                || magic == 0xcffaedfe || magic == 0xcafebabe || magic == 0xbebafeca;
        if (!nativeFile) throw new IOException("不执行 Java 脚本或命令包装器");
        return actual;
    }
    private static JavaRuntime parse(Path actual, String text) throws IOException {
        var properties = new HashMap<String, String>();
        for (String line : text.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                String key = line.substring(0, separator).trim();
                if (Set.of("java.version", "sun.arch.data.model", "java.vendor").contains(key)
                        && properties.putIfAbsent(key, line.substring(separator + 1).trim()) != null) {
                    throw new IOException("Java 探测属性重复");
                }
            }
        }
        try {
            String version = properties.getOrDefault("java.version", "");
            if (version.startsWith("1.")) version = version.substring(2);
            int major = Integer.parseInt(version.split("[._+\\-]", 2)[0]);
            int bits = Integer.parseInt(properties.getOrDefault("sun.arch.data.model", ""));
            String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual.toString().getBytes(StandardCharsets.UTF_8)));
            return new JavaRuntime(id, actual, major, bits, properties.get("java.vendor"));
        } catch (IllegalArgumentException | NoSuchAlgorithmException invalid) {
            throw new IOException("无法确认 Java 版本、位数或发行者");
        }
    }
    private static void close(Closeable value) { try { value.close(); } catch (IOException ignored) { } }
}
