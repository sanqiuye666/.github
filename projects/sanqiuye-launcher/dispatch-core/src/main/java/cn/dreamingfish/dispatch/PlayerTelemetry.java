package cn.dreamingfish.dispatch;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Version fields may be unknown; a client report is not proof of account authority. */
public record PlayerTelemetry(UUID playerId, String gameVersion, String packVersion, Duration playTime, Instant lastSeen) {
    public PlayerTelemetry {
        if (playerId == null || playTime == null || playTime.isNegative() || lastSeen == null) {
            throw new IllegalArgumentException("Invalid player telemetry metadata");
        }
    }
}
