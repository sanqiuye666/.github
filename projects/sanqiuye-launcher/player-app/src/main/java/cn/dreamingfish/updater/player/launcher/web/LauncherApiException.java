package cn.dreamingfish.updater.player.launcher.web;

public final class LauncherApiException extends RuntimeException {
    private final int status;
    private final String code;
    public LauncherApiException(int status,String code,String message) { super(message); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
}
