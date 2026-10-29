package cn.dreamingfish.launcher.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.List;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

/** Launcher-owned paths only. Locks coordinate cooperating processes, not hostile OS users. */
public final class LauncherStorage {
    private final Path root;
    public LauncherStorage(Path absoluteRoot) {
        if (absoluteRoot == null || !absoluteRoot.isAbsolute()) throw new IllegalArgumentException("数据根必须是绝对目录");
        root = absoluteRoot.normalize();
    }
    public Path root() { return root; }
    public java.nio.file.attribute.BasicFileAttributes attributes(Path relative) throws IOException {
        return Files.readAttributes(checked(relative),java.nio.file.attribute.BasicFileAttributes.class,NOFOLLOW_LINKS);
    }
    /** 仅删除受管普通文件；调用方先持有业务锁并保存可验证的备份。 */
    public void deleteFile(Path relative) throws IOException {
        if(!attributes(relative).isRegularFile())throw new IOException("仅能移除普通文件");
        Files.delete(checked(relative));
    }
    public java.io.InputStream openRead(Path relative) throws IOException {
        Path file=checked(relative);
        if(!Files.isRegularFile(file,NOFOLLOW_LINKS))throw new IOException("需要普通受管文件");
        return Files.newInputStream(checked(relative),READ,NOFOLLOW_LINKS);
    }
    public long size(Path relative) throws IOException {Path file=checked(relative);if(!Files.isRegularFile(file,NOFOLLOW_LINKS))throw new IOException("需要普通受管文件");return Files.size(file);}
    public StagedWrite beginStagedWrite(Path relativeDirectory) throws IOException {
        ensureDirectory(relativeDirectory);
        Path temporary=relativeDirectory.resolve(".download-"+java.util.UUID.randomUUID()+".tmp");
        return new StagedWrite(temporary,FileChannel.open(checked(temporary),CREATE_NEW,WRITE,NOFOLLOW_LINKS));
    }
    public final class StagedWrite implements AutoCloseable {
        private final Path relative;private final FileChannel channel;private boolean closed;
        private StagedWrite(Path relative,FileChannel channel){this.relative=relative;this.channel=channel;}
        public void write(ByteBuffer bytes)throws IOException{if(closed)throw new IOException("暂存已关闭");while(bytes.hasRemaining())channel.write(bytes);}
        /** 用于发布前检查容器；暂存路径不会通过本机API返回。 */
        public Path path()throws IOException{if(closed)throw new IOException("暂存已关闭");return checked(relative);}
        public void publishNew(Path destination)throws IOException{
            if(closed)throw new IOException("暂存已关闭");
            Path target=checked(destination);ensureDirectory(root.relativize(target.getParent()));channel.force(true);
            // An atomic new hard link cannot replace an existing destination, unlike ATOMIC_MOVE.
            Files.createLink(checked(destination),checked(relative));
            checked(destination);
        }
        @Override public void close()throws IOException{if(closed)return;closed=true;try{channel.close();}finally{Files.deleteIfExists(checked(relative));}}
    }
    public void copyNew(Path source,Path destination)throws IOException{
        copyNew(source,destination,new cn.dreamingfish.launcher.downloads.CancellationToken());
    }
    public void copyNew(Path source,Path destination,cn.dreamingfish.launcher.downloads.CancellationToken token)throws IOException{
        token.check();try(var in=openRead(source)){copyNew(in,destination,token);}
    }
    void copyNew(java.io.InputStream in,Path destination,cn.dreamingfish.launcher.downloads.CancellationToken token)throws IOException{
        token.check();
        Path target=checked(destination);Path parent=root.relativize(target.getParent());
        try(var stage=beginStagedWrite(parent)){
            byte[] buffer=new byte[65536];int count;
            while(true){token.check();count=in.read(buffer);token.check();if(count==-1)break;stage.write(ByteBuffer.wrap(buffer,0,count));}
            token.check();stage.publishNew(destination);
        }
    }

    private Path checked(Path relative) throws IOException {
        if (relative == null || relative.isAbsolute()) throw new IllegalArgumentException("仅接受受管相对路径");
        for (Path part : relative) if (part.toString().equals("..") || part.toString().equals(".")) throw new IllegalArgumentException("不接受路径跳转");
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("路径超出数据根");
        Path current = target.getRoot();
        for (Path part : target) {
            current = current.resolve(part);
            try {
                var attributes=Files.readAttributes(current,java.nio.file.attribute.BasicFileAttributes.class,NOFOLLOW_LINKS);
                if(attributes.isSymbolicLink()||attributes.isOther())throw new IOException("受管路径包含符号链接或目录别名");
            } catch(NoSuchFileException absent) { break; }
        }
        return target;
    }
    public boolean exists(Path relative) throws IOException { return Files.exists(checked(relative), NOFOLLOW_LINKS); }
    public void requireDirectory(Path relative) throws IOException {
        if (!Files.isDirectory(checked(relative), NOFOLLOW_LINKS)) throw new IOException("实例目录缺失或不可用");
    }
    public void ensureDirectory(Path relative) throws IOException {
        Files.createDirectories(checked(relative));
        if (!Files.isDirectory(checked(relative), NOFOLLOW_LINKS)) throw new IOException("受管路径不是目录");
    }
    public byte[] read(Path relative, int limit) throws IOException {
        if (limit < 1 || limit == Integer.MAX_VALUE) throw new IllegalArgumentException("无效读取上限");
        Path file = checked(relative);
        if (!Files.isRegularFile(file, NOFOLLOW_LINKS) || Files.size(file) > limit) throw new IOException("记录不存在、不是普通文件或超过大小限制");
        try (var stream = Files.newInputStream(checked(relative), READ, NOFOLLOW_LINKS)) {
            byte[] bytes = stream.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("记录超过大小限制");
            checked(relative);
            return bytes;
        }
    }
    public void writeAtomically(Path relative, byte[] bytes) throws IOException {
        Path target = checked(relative);
        ensureDirectory(root.relativize(target.getParent()));
        Path temporary = Files.createTempFile(checked(root.relativize(target.getParent())), ".write-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, WRITE, NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(checked(root.relativize(temporary)), checked(relative), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            checked(relative);
        } finally { Files.deleteIfExists(temporary); }
    }
    public List<Path> children(Path relative) throws IOException {
        return children(relative, Integer.MAX_VALUE);
    }
    /** 有界枚举，在排序前截断，避免不受控目录耗尽内存。 */
    public List<Path> children(Path relative, int limit) throws IOException {
        if (limit < 1) throw new IllegalArgumentException("目录读取上限无效");
        Path directory = checked(relative);
        if (!Files.exists(directory, NOFOLLOW_LINKS)) return List.of();
        try (var stream = Files.list(directory)) { return stream.limit(limit).map(root::relativize).sorted().toList(); }
    }
    public void publishDirectory(Path pending, Path destination) throws IOException {
        Path source = checked(pending), target = checked(destination);
        if (!Files.isDirectory(source, NOFOLLOW_LINKS) || Files.exists(target, NOFOLLOW_LINKS)) throw new IOException("实例目标已存在或暂存目录无效");
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        checked(destination);
    }
    public Lease lock(Path relative) throws IOException {
        Path file = checked(relative);
        ensureDirectory(root.relativize(file.getParent()));
        var channel = FileChannel.open(checked(relative), CREATE, WRITE, NOFOLLOW_LINKS);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new LauncherBusyException("另一个进程正在使用此目录，请稍后重试");
            checked(relative);
            return new Lease(channel, lock);
        } catch (IOException | RuntimeException error) {
            channel.close();
            if (error instanceof OverlappingFileLockException) throw new LauncherBusyException("此目录正在被使用，请稍后重试", error);
            throw error;
        }
    }
    public record Lease(FileChannel channel, FileLock fileLock) implements AutoCloseable {
        @Override public void close() throws IOException { try { fileLock.release(); } finally { channel.close(); } }
    }
}
