package cn.dreamingfish.updater.player.launcher;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;

/** 原生窗口持有输入管道与排他数据根；旧Web入口不启用此约束。 */
public final class NativeLifetime implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private NativeLifetime(FileChannel channel,FileLock lock) { this.channel=channel;this.lock=lock; }
    public static NativeLifetime open(Path root,boolean enabled) throws IOException {
        if(!enabled)return new NativeLifetime(null,null);
        Files.createDirectories(root);
        Path file=root.resolve(".native-launcher.lock");
        if(Files.isSymbolicLink(root)||Files.isSymbolicLink(file)
                || (Files.exists(file,LinkOption.NOFOLLOW_LINKS)&&(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)!=0)))
            throw new IOException("原生启动器目录锁异常，原文件已保留，请检查目录");
        var channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        try {
            var lock=channel.tryLock();
            if(lock==null)throw new IOException("另一个三秋叶启动器正在使用此目录，请切回已有窗口");
            return new NativeLifetime(channel,lock);
        } catch(OverlappingFileLockException busy) {
            channel.close();throw new IOException("另一个三秋叶启动器正在使用此目录，请切回已有窗口");
        } catch(IOException|RuntimeException error) { channel.close();throw error; }
    }
    public void watch(InputStream parent,Runnable stop) {
        if(channel==null)return;
        Thread.ofPlatform().daemon(true).name("sqy-native-owner").start(()->{
            try { parent.read(); } catch(IOException closed) { /* 父进程管道关闭同样进入资源收尾。 */ }
            finally { stop.run(); }
        });
    }
    @Override public void close() throws IOException {
        try { if(lock!=null&&lock.isValid())lock.release(); }
        finally { if(channel!=null)channel.close(); }
    }
}
