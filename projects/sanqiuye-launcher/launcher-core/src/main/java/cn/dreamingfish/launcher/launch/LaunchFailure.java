package cn.dreamingfish.launcher.launch;

import java.io.IOException;

/** 可安全显示的启动原因；原始命令、环境和异常不放入消息。 */
public final class LaunchFailure extends IOException {
    private final String code;
    public LaunchFailure(String code, String message) {
        super(message);
        if (code == null || !code.matches("[A-Z_]{1,64}") || message == null || message.isBlank() || message.length() > 512) {
            throw new IllegalArgumentException("启动错误说明无效");
        }
        this.code = code;
    }
    public String code() { return code; }
}
