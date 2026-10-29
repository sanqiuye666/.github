package cn.dreamingfish.launcher.runtime;

import cn.dreamingfish.launcher.downloads.CancellationToken;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** 登记本机 Java；扫描只访问已知位置及安装根的直接子目录。 */
public final class JavaRuntimeService {
    public record Diagnostic(Path home, String message) { }
    private final Supplier<List<Path>> candidates;
    private final JavaProbe probe;
    private final Map<String, JavaRuntime> registered = new LinkedHashMap<>();
    private volatile List<Diagnostic> diagnostics = List.of();

    public JavaRuntimeService() { this(JavaRuntimeService::systemCandidates, new JavaProbe()); }
    JavaRuntimeService(Supplier<List<Path>> candidates, JavaProbe probe) {
        this.candidates = Objects.requireNonNull(candidates); this.probe = Objects.requireNonNull(probe);
    }
    public List<JavaRuntime> discover(CancellationToken token) throws IOException {
        var seen = new HashSet<Path>(); var result = new ArrayList<JavaRuntime>();
        var problems = new ArrayList<Diagnostic>();
        for (Path home : candidates.get()) {
            token.check();
            Path key;
            try { key = home.toRealPath(); } catch (IOException error) { key = home.toAbsolutePath().normalize(); }
            if (seen.contains(key)) continue;
            if (seen.size() == 64) break;
            seen.add(key);
            try { result.add(registerHome(key, token)); }
            catch (IOException error) { token.check(); problems.add(new Diagnostic(key, "未检测到可用 Java：" + safeReason(error))); }
        }
        diagnostics = List.copyOf(problems);
        return List.copyOf(result);
    }
    public JavaRuntime registerHome(Path home, CancellationToken token) throws IOException {
        if (home == null || !home.isAbsolute()) throw new IllegalArgumentException("Java 安装目录必须是绝对路径");
        token.check(); Path actual = home.toRealPath();
        if (!Files.isDirectory(actual)) throw new IOException("Java 安装目录无效");
        String file = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        JavaRuntime value = probe.probe(actual.resolve("bin").resolve(file), token);
        synchronized (registered) { registered.put(value.id(), value); }
        return value;
    }
    public JavaRuntime require(String id) throws IOException {
        synchronized (registered) {
            JavaRuntime value = registered.get(id);
            if (value == null) throw new IOException("尚未登记该 Java，请先检测或选择安装目录");
            return value;
        }
    }
    public List<JavaRuntime> runtimes() {
        synchronized (registered) { return List.copyOf(registered.values()); }
    }
    public List<Diagnostic> diagnostics() { return diagnostics; }
    public Optional<JavaRuntime> select(int requiredMajor, int bits) {
        return runtimes().stream().filter(value -> value.majorVersion() == requiredMajor && value.bits() == bits).findFirst();
    }
    private static String safeReason(IOException error) {
        if (error instanceof NoSuchFileException) return "目录或可执行文件不存在";
        String text = error.getMessage();
        return text != null && text.startsWith("Java ") && text.length() <= 128 ? text : "探测失败，未登记";
    }
    private static List<Path> systemCandidates() {
        var values = new LinkedHashSet<Path>();
        values.add(Path.of(System.getProperty("java.home")).toAbsolutePath());
        String configured = System.getenv("JAVA_HOME");
        if (configured != null && !configured.isBlank()) add(values, configured);
        String search = System.getenv("PATH");
        if (search != null) for (String item : search.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (values.size() >= 64) break;
            try { Path bin = Path.of(item.replace("\"", "")).toAbsolutePath(); if (bin.getParent() != null) values.add(bin.getParent()); }
            catch (InvalidPathException ignored) { }
        }
        var roots = new ArrayList<Path>();
        if (System.getProperty("os.name").startsWith("Windows")) {
            for (String variable : List.of("ProgramFiles", "ProgramFiles(x86)")) {
                String directory = System.getenv(variable);
                if (directory != null) for (String vendor : List.of("Java", "Eclipse Adoptium", "Microsoft", "Zulu", "Amazon Corretto", "BellSoft")) {
                    roots.add(Path.of(directory, vendor));
                }
            }
        } else { roots.add(Path.of("/usr/lib/jvm")); roots.add(Path.of("/Library/Java/JavaVirtualMachines")); }
        for (Path root : roots) {
            if (values.size() >= 64) break;
            try (var children = Files.newDirectoryStream(root)) {
                for (Path home : children) {
                    if (values.size() >= 64) break;
                    values.add(root.endsWith("JavaVirtualMachines") ? home.resolve("Contents/Home") : home);
                }
            } catch (IOException ignored) { }
        }
        return List.copyOf(values);
    }
    private static void add(Set<Path> values, String value) {
        try { values.add(Path.of(value).toAbsolutePath()); } catch (InvalidPathException ignored) { }
    }
}
