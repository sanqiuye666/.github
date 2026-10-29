package cn.dreamingfish.launcher.launch;

import java.time.Instant;
import java.util.UUID;

/** 对外运行快照；不包含命令、路径、PID 或账号凭据。 */
public record LaunchSnapshot(UUID id, String instanceId, String installationId, State state,
                             Instant createdAt, Instant startedAt, Instant endedAt, Integer exitCode,
                             String errorCode, String message) {
    public enum State { PREPARING, STARTING, RUNNING, STOPPING, EXITED, FAILED, CANCELLED, UNCERTAIN }
}
