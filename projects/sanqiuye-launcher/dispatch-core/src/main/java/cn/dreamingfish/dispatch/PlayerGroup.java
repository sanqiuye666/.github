package cn.dreamingfish.dispatch;

public record PlayerGroup(String id, String name, boolean defaultGroup) {
    public PlayerGroup {
        if (id == null || id.isBlank() || name == null || name.isBlank()
                || id.codePoints().anyMatch(Character::isISOControl)
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid player group");
        }
    }
}
