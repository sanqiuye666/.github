package cn.dreamingfish.dispatch;

import java.util.UUID;

/** Non-secret identity metadata. Services must authenticate and authorize separately. */
public record PanelIdentity(UUID id, String username, Role role, String groupId, Status status) {
    public enum Role { PLAYER, ADMIN }
    public enum Status { ACTIVE, DISABLED }

    public PanelIdentity {
        if (id == null || role == null || status == null || username == null || username.isBlank()
                || groupId == null || groupId.isBlank()
                || username.codePoints().anyMatch(Character::isISOControl)
                || groupId.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid panel identity metadata");
        }
    }
}
