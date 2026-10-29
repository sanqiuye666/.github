package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherStorage;
import cn.dreamingfish.launcher.vanilla.SafeInstallPath;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/** 只解包到调用方私有暂存目录；校验中央目录类型和实际展开字节，不发布运行结果。 */
public class NativeExtractor {
    private static final long FILE_LIMIT = 128L * 1024 * 1024, TOTAL_LIMIT = 512L * 1024 * 1024;
    private record DirectoryInfo(int entries, long expanded) { }

    public void extract(List<LaunchPlan.NativeArchive> archives, Path privateStaging, CancellationToken token) throws IOException {
        long declared = 0; int entries = 0;
        for (var archive : archives) {
            token.check(); verifySource(archive, token);
            var info = directory(archive.archive(), token); declared += info.expanded(); entries += info.entries();
            if (declared > TOTAL_LIMIT) throw failure("原生库总展开超过 512 MiB 上限");
            if (entries > 20000) throw failure("原生库条目超过 20000 上限");
        }
        var storage = new LauncherStorage(privateStaging); storage.ensureDirectory(Path.of(""));
        var written = new HashSet<String>(); long expanded = 0;
        try {
            for (var archive : archives) {
                for (String exclusion : archive.excludes()) safe(exclusion.endsWith("/") ? exclusion.substring(0, exclusion.length() - 1) : exclusion);
                var names = new HashSet<String>();
                try (var zip = new ZipFile(archive.archive().toFile())) {
                    var iterator = zip.entries(); byte[] buffer = new byte[65536];
                    while (iterator.hasMoreElements()) {
                        token.check(); ZipEntry entry = iterator.nextElement(); String name = entry.getName();
                        Path path = safe(entry.isDirectory() ? name.substring(0, name.length() - 1) : name);
                        String key = SafeInstallPath.key(path);
                        if (!names.add(key)) throw failure("原生库存在重复或大小写冲突路径");
                        if (entry.getSize() < 0 || entry.getSize() > FILE_LIMIT) throw failure("原生库单文件超过 128 MiB 上限");
                        if (entry.isDirectory()) { if (entry.getSize() != 0) throw failure("原生库目录内容无效"); continue; }
                        boolean excluded = archive.excludes().stream().anyMatch(name::startsWith);
                        if (!excluded && !written.add(key)) throw failure("原生库目标文件路径冲突");
                        Path parent = path.getParent() == null ? Path.of("") : path.getParent();
                        var crc = new CRC32(); long count = 0;
                        try (var input = zip.getInputStream(entry); var stage = excluded ? null : storage.beginStagedWrite(parent)) {
                            int size;
                            while ((size = input.read(buffer)) != -1) {
                                token.check(); count += size; expanded += size;
                                if (count > FILE_LIMIT) throw failure("原生库单文件实际展开超过 128 MiB 上限");
                                if (expanded > TOTAL_LIMIT) throw failure("原生库实际总展开超过 512 MiB 上限");
                                crc.update(buffer, 0, size); if (stage != null) stage.write(ByteBuffer.wrap(buffer, 0, size));
                            }
                            if (count != entry.getSize() || crc.getValue() != entry.getCrc()) throw failure("原生库长度或 CRC 校验失败");
                            token.check(); if (stage != null) stage.publishNew(path);
                        }
                    }
                }
            }
        } catch (LaunchFailure | InterruptedIOException error) { throw error; }
        catch (IOException | IllegalArgumentException error) { throw failure("原生库压缩包或目标目录无效，暂存内容已保留"); }
        token.check();
    }
    private static Path safe(String name) throws LaunchFailure {
        try { return SafeInstallPath.parse(name); } catch (IllegalArgumentException invalid) { throw failure("原生库含不安全文件路径"); }
    }
    private static void verifySource(LaunchPlan.NativeArchive archive, CancellationToken token) throws IOException {
        if (archive.archive() == null || !archive.archive().isAbsolute() || archive.size() < 0 || archive.size() > 2L * 1024 * 1024 * 1024) throw failure("原生库来源记录无效");
        var storage = new LauncherStorage(archive.archive().getParent()); Path path = archive.archive().getFileName();
        if (!storage.exists(path) || storage.size(path) != archive.size()) throw new LaunchFailure("FILE_CHANGED", "原生库来源缺失或长度已变化");
        var hash = new FileDigest(FileDigest.Algorithm.SHA256, archive.sha256()).newDigest(); long count = 0;
        try (var input = storage.openRead(path)) { byte[] buffer = new byte[65536]; int size; while ((size = input.read(buffer)) != -1) { token.check(); count += size; if (count > archive.size()) throw new LaunchFailure("FILE_CHANGED", "原生库来源长度已变化"); hash.update(buffer, 0, size); } }
        if (count != archive.size() || !HexFormat.of().formatHex(hash.digest()).equals(archive.sha256())) throw new LaunchFailure("FILE_CHANGED", "原生库来源摘要已变化，原文件已保留");
    }
    private static DirectoryInfo directory(Path archive, CancellationToken token) throws IOException {
        try (var channel = FileChannel.open(archive, READ, NOFOLLOW_LINKS)) {
            long size = channel.size(); int tailSize = (int)Math.min(size, 65557); var tail = ByteBuffer.allocate(tailSize).order(ByteOrder.LITTLE_ENDIAN);
            channel.position(size - tailSize); read(channel, tail); tail.flip(); int end = -1;
            for (int i = tailSize - 22; i >= 0; i--) if (tail.getInt(i) == 0x06054b50 && i + 22 + u16(tail, i + 20) == tailSize) { end = i; break; }
            if (end < 0 || u16(tail, end + 4) != 0 || u16(tail, end + 6) != 0 || u16(tail, end + 8) != u16(tail, end + 10)) throw failure("不支持此原生库压缩格式");
            int entries = u16(tail, end + 10); if (entries > 20000) throw failure("原生库条目超过 20000 上限");
            long length = u32(tail, end + 12), offset = u32(tail, end + 16), limit = offset + length;
            if (length == 0xffffffffL || offset == 0xffffffffL || limit > size - tailSize + end) throw failure("不支持此原生库压缩格式");
            channel.position(offset); long expanded = 0; var header = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < entries; i++) {
                token.check(); header.clear(); read(channel, header); header.flip();
                if (header.getInt(0) != 0x02014b50 || (u16(header, 8) & 1) != 0) throw failure("原生库中央目录无效");
                long fileSize = u32(header, 24); if (fileSize > FILE_LIMIT) throw failure("原生库单文件超过 128 MiB 上限");
                expanded += fileSize; if (expanded > TOTAL_LIMIT) throw failure("原生库总展开超过 512 MiB 上限");
                int kind = (int)(u32(header, 38) >>> 16) & 0xf000;
                if (kind != 0 && kind != 0x8000 && kind != 0x4000) throw failure("原生库不允许链接或特殊文件");
                int name = u16(header, 28), extra = u16(header, 30), comment = u16(header, 32);
                if (name == 0 || name > 4096 || channel.position() + name + extra + comment > limit) throw failure("原生库中央目录无效");
                channel.position(channel.position() + name + extra + comment);
            }
            if (channel.position() != limit) throw failure("原生库中央目录长度无效");
            return new DirectoryInfo(entries, expanded);
        }
    }
    private static void read(FileChannel input, ByteBuffer target) throws IOException { while (target.hasRemaining()) if (input.read(target) < 0) throw failure("原生库中央目录不完整"); }
    private static int u16(ByteBuffer data, int offset) { return Short.toUnsignedInt(data.getShort(offset)); }
    private static long u32(ByteBuffer data, int offset) { return Integer.toUnsignedLong(data.getInt(offset)); }
    private static LaunchFailure failure(String message) { return new LaunchFailure("NATIVE_EXTRACTION_FAILED", message); }
}
