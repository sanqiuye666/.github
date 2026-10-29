package cn.dreamingfish.launcher;

import java.util.UUID;

/** Public game identity; panel authorization and private credentials are separate. */
public record GameIdentity(UUID id, String name, Kind kind) {
    public enum Kind { OFFLINE, MICROSOFT }

    public GameIdentity {
        if (id == null || kind == null || name == null || name.isBlank()
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid game identity");
        }
    }
}
