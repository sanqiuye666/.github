package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.storage.LauncherStorage;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

/** 单次运行四份有界日志；UTF-8 增量解码，超长行分段，按字节游标读取。 */
public final class LaunchLogStore implements AutoCloseable {
    public record LogPage(List<String> lines, String nextCursor, boolean truncated) {
        public LogPage { lines = List.copyOf(lines); }
    }
    private static final long LIMIT = 8L * 1024 * 1024;
    private static final Pattern SECRET = Pattern.compile("(?i)((?:access[_-]?token|refresh[_-]?token|authorization|password)\\s*[=:]\\s*)(?:Bearer\\s+)?[^\\s,;]+" );
    private static final Pattern SECRET_PREFIX = Pattern.compile("(?i)(?:access[_-]?token|refresh[_-]?token|authorization|password)\\s*[=:]$");
    private final LauncherStorage storage;
    private final Path directory;
    private OutputStream output;
    private ArrayList<Long> activeFiles;
    private long sequence, written;
    public LaunchLogStore(Path root, String launchDirectory) {
        storage = new LauncherStorage(root);
        Path base = Path.of(launchDirectory).normalize();
        if (base.isAbsolute() || base.getNameCount() != 4 || !base.getName(0).toString().equals("instances") || !base.getName(2).toString().equals("launches")) throw new IllegalArgumentException("运行日志目录无效");
        cn.dreamingfish.launcher.downloads.TaskRequest.uuid(base.getName(1).toString());
        cn.dreamingfish.launcher.downloads.TaskRequest.uuid(base.getName(3).toString());
        directory = base.resolve("logs");
    }
    public Path directory() { return storage.root().resolve(directory); }
    public void consume(InputStream input, String source) throws IOException {
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            char[] chars = new char[2048]; var line = new StringBuilder(); var suffix = new StringBuilder(); boolean privateTail = false; int count;
            while ((count = reader.read(chars)) != -1) for (int i = 0; i < count; i++) {
                char value = chars[i];
                if (value == '\n') { append(source, line.toString()); line.setLength(0); suffix.setLength(0); privateTail = false; }
                else if (value != '\r') {
                    if (privateTail) continue;
                    line.append(value);
                    suffix.append(value); if (suffix.length() > 64) suffix.deleteCharAt(0);
                    if ((value == '=' || value == ':') && SECRET_PREFIX.matcher(suffix).find()) { line.append("[已隐藏]"); privateTail = true; }
                    if (line.length() >= 4096 && !Character.isHighSurrogate(value)) { append(source, line + " [长行分段]"); line.setLength(0); }
                }
            }
            if (!line.isEmpty()) append(source, line.toString());
        }
    }
    public synchronized void append(String source, String text) throws IOException {
        if (!Set.of("stdout", "stderr", "launcher").contains(source)) throw new IllegalArgumentException("日志流无效");
        String safe = SECRET.matcher(text).replaceAll("$1[已隐藏]").replaceAll("[\\p{Cntrl}&&[^\\t]]", " ");
        if (safe.length() > 8192) safe = safe.substring(0, Character.isHighSurrogate(safe.charAt(8191)) ? 8191 : 8192) + " [长行分段]";
        byte[] bytes = ("[" + source + "] " + safe + "\n").getBytes(StandardCharsets.UTF_8);
        if (activeFiles == null) {
            storage.ensureDirectory(directory); activeFiles = files();
            sequence = activeFiles.isEmpty() ? 1 : activeFiles.getLast();
            written = activeFiles.isEmpty() ? 0 : storage.size(file(sequence));
        }
        if (written + bytes.length > LIMIT) { close(); sequence++; written = 0; }
        if (output == null) {
            Path file = file(sequence);
            if (!storage.exists(file)) storage.writeAtomically(file, new byte[0]);
            storage.size(file);
            output = new BufferedOutputStream(java.nio.channels.Channels.newOutputStream(FileChannel.open(storage.root().resolve(file), WRITE, APPEND, NOFOLLOW_LINKS)), 65536);
            if (!activeFiles.contains(sequence)) activeFiles.add(sequence);
            while (activeFiles.size() > 4) {
                long oldest = activeFiles.removeFirst(); storage.size(file(oldest)); Files.delete(storage.root().resolve(file(oldest)));
            }
        }
        output.write(bytes); written += bytes.length;
    }
    public synchronized LogPage page(String cursor, int limit) throws IOException {
        if (output != null) output.flush();
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("日志页大小必须为 1–500");
        long sequence = 0, offset = 0;
        if (cursor != null) {
            if (!cursor.matches("[0-9]{1,18}:[0-9]{1,8}")) throw new IllegalArgumentException("日志游标无效");
            var parts = cursor.split(":"); sequence = Long.parseLong(parts[0]); offset = Long.parseLong(parts[1]);
            if (offset > LIMIT) throw new IllegalArgumentException("日志游标无效");
        }
        var files = files(); if (files.isEmpty()) return new LogPage(List.of(), "0:0", false);
        boolean truncated = cursor != null && sequence < files.getFirst();
        if (sequence < files.getFirst()) { sequence = files.getFirst(); offset = 0; }
        if (sequence > files.getLast() || !files.contains(sequence)) throw new IllegalArgumentException("日志游标不存在");
        var lines = new ArrayList<String>();
        for (long current : files) {
            if (current < sequence) continue;
            if (current > sequence) { sequence = current; offset = 0; }
            long size = storage.size(file(current)); if (offset > size) throw new IllegalArgumentException("日志游标无效");
            try (var input = new BufferedInputStream(storage.openRead(file(current)))) {
                input.skipNBytes(offset);
                while (lines.size() < limit) {
                    var line = new ByteArrayOutputStream(); int value;
                    while ((value = input.read()) != -1) { offset++; if (value == '\n') break; if (line.size() > 32768) throw new IOException("日志行记录无效"); line.write(value); }
                    if (value == -1 && line.size() == 0) break;
                    lines.add(line.toString(StandardCharsets.UTF_8));
                }
            }
            if (lines.size() == limit) break;
        }
        return new LogPage(lines, sequence + ":" + offset, truncated);
    }
    private Path file(long sequence) { return directory.resolve(String.format(Locale.ROOT, "%018d.log", sequence)); }
    private ArrayList<Long> files() throws IOException {
        var result = new ArrayList<Long>();
        for (var file : storage.children(directory)) {
            String name = file.getFileName().toString();
            if (name.matches("[0-9]{18}\\.log")) { if (storage.size(file) > LIMIT) throw new IOException("日志文件超过上限"); result.add(Long.parseLong(name.substring(0, 18))); }
        }
        Collections.sort(result); return result;
    }
    @Override public synchronized void close() throws IOException { if (output != null) { output.close(); output = null; } }
}
