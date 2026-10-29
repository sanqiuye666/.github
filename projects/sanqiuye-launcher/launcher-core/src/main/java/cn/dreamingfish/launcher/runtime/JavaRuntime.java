package cn.dreamingfish.launcher.runtime;

import java.nio.file.Path;

/** 经过真实进程探测的 Java，不包含启动命令或认证信息。 */
public record JavaRuntime(String id, Path executable, int majorVersion, int bits, String vendor) {
    public JavaRuntime {
        if (id == null || !id.matches("[0-9a-f]{64}") || executable == null || !executable.isAbsolute()
                || majorVersion < 1 || majorVersion > 100 || bits != 32 && bits != 64
                || vendor == null || vendor.isBlank() || vendor.length() > 256
                || vendor.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Java 运行环境记录无效");
        }
    }
}
