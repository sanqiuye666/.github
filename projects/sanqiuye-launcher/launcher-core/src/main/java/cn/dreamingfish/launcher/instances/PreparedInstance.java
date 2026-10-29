package cn.dreamingfish.launcher.instances;

import cn.dreamingfish.launcher.GameInstance;
import java.time.Instant;
import java.util.Objects;

/** Directory preparation is not a claim that Minecraft has been installed. */
public record PreparedInstance(GameInstance instance, Instant createdAt, State state) {
    public enum State { PREPARED }
    public PreparedInstance { Objects.requireNonNull(instance); Objects.requireNonNull(createdAt); Objects.requireNonNull(state); }
    static String validName(String value) {
        if (value == null || value.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("实例名称不能包含控制字符");
        String name = value.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 64) throw new IllegalArgumentException("实例名称需要 1–64 个字符");
        return name;
    }
}
