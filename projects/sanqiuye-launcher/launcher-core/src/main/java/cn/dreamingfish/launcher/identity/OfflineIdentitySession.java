package cn.dreamingfish.launcher.identity;

import cn.dreamingfish.launcher.GameIdentity;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 当前后端会话内的离线身份；不读取或保存账号文件。 */
public final class OfflineIdentitySession {
    private final Map<UUID, GameIdentity> identities = new LinkedHashMap<>();
    private UUID selected;

    public synchronized GameIdentity create(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("离线名字须为 3–16 位字母、数字或下划线");
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        if (identities.containsKey(id)) return identities.get(id);
        if (identities.size() >= 20) throw new IllegalArgumentException("本次会话最多保留 20 个离线身份");
        var identity = new GameIdentity(id, name, GameIdentity.Kind.OFFLINE);
        identities.put(id, identity);
        if (selected == null) selected = id;
        return identity;
    }
    public synchronized List<GameIdentity> list() { return List.copyOf(identities.values()); }
    public synchronized GameIdentity select(UUID id) { GameIdentity value = require(id); selected = id; return value; }
    public synchronized GameIdentity require(UUID id) {
        GameIdentity value = identities.get(id);
        if (value == null) throw new IllegalArgumentException("请选择本次会话内已有的离线身份");
        return value;
    }
    public synchronized void remove(UUID id) { require(id); identities.remove(id); if (id.equals(selected)) selected = null; }
    public synchronized Optional<GameIdentity> current() { return Optional.ofNullable(identities.get(selected)); }
}
