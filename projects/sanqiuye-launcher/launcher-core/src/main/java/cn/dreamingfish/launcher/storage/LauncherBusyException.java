package cn.dreamingfish.launcher.storage;

import java.io.IOException;

/** A cooperating process or operation currently owns this storage lease. */
public final class LauncherBusyException extends IOException {
    public LauncherBusyException(String message) { super(message); }
    public LauncherBusyException(String message,Throwable cause) { super(message,cause); }
}
