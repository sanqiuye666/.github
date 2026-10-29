package cn.dreamingfish.launcher.launch;

import java.util.*;

/** 实例独立的非账号设置；启动器控制目录、身份、类路径与内存参数。 */
public record LaunchSettings(String javaId, int initialMemoryMiB, int maximumMemoryMiB,
                             int width, int height, List<String> extraJvmArgs, boolean acceptUnknownJava) {
    public LaunchSettings {
        if (javaId != null && !javaId.matches("[0-9a-f]{64}") || initialMemoryMiB < 256
                || maximumMemoryMiB > 32768 || initialMemoryMiB > maximumMemoryMiB
                || width < 640 || width > 7680 || height < 480 || height > 4320
                || extraJvmArgs == null || extraJvmArgs.size() > 32) {
            throw new IllegalArgumentException("Java、内存、窗口或参数设置超出允许范围");
        }
        extraJvmArgs = List.copyOf(extraJvmArgs);
        int length = 0;
        for (String value : extraJvmArgs) {
            if (value.isBlank() || value.length() > 4096 || value.chars().anyMatch(Character::isISOControl)
                    || !value.startsWith("-") || reserved(value)) {
                throw new IllegalArgumentException("附加 JVM 参数无效或覆盖了启动器保留设置");
            }
            length += value.length();
        }
        if (length > 16384) throw new IllegalArgumentException("附加 JVM 参数合计超过 16384 字符");
    }
    public static LaunchSettings defaults() { return new LaunchSettings(null, 256, 2048, 960, 540, List.of(), false); }
    private static boolean reserved(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return List.of("-cp", "-classpath", "--class-path", "--module", "--patch-module", "--upgrade-module-path",
                "-jar", "--source", "-m", "-p", "-javaagent", "-agent", "-xrun", "-xbootclasspath", "-xms", "-xmx",
                "-djava.class.path", "-djava.library.path", "-djava.system.class.loader", "-duser.dir", "-dorg.lwjgl.librarypath",
                "-dlog4j.configuration", "--gamedir", "--assetsdir", "--username", "--uuid", "--accesstoken",
                "-xx:maxheapsize", "-xx:initialheapsize", "-xx:maxram", "-xx:initialram", "-xx:minram",
                "-xx:onerror", "-xx:onoutofmemoryerror", "-xx:vmoptionsfile", "-xx:flags").stream().anyMatch(lower::startsWith);
    }
}
