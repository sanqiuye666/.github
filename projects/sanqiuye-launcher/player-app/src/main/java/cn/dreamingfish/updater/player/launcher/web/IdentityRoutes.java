package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.downloads.TaskRequest;
import cn.dreamingfish.launcher.identity.OfflineIdentitySession;
import java.io.IOException;
import java.util.*;

/** 临时身份只留在当前后端会话；切换不写浏览器或账号文件。 */
public final class IdentityRoutes implements LauncherRoutes {
    private final OfflineIdentitySession identities;
    public IdentityRoutes(OfflineIdentitySession identities) { this.identities = Objects.requireNonNull(identities); }
    @Override public Optional<ApiResponse> handle(ApiRequest r) throws IOException {
        if (!r.path().equals("/identities") && !r.path().startsWith("/identities/")) return Optional.empty();
        r.onlyQuery(); int status = 200;
        if (r.path().equals("/identities")) {
            if (!r.method().equals("GET")) { r.requireMethod("POST"); r.onlyFields(Set.of("name")); identities.create(r.text("name", 16)); status = 201; }
        } else {
            r.requireMethod("POST"); r.onlyFields(Set.of()); var parts = r.path().split("/", -1);
            if (parts.length != 4 || !Set.of("select", "remove").contains(parts[3])) throw new LauncherApiException(404, "NOT_FOUND", "身份操作不存在");
            UUID id = TaskRequest.uuid(parts[2]); if (parts[3].equals("select")) identities.select(id); else identities.remove(id);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("identities", identities.list().stream().map(value -> Map.of("id", value.id().toString(), "name", value.name(), "kind", value.kind().name())).toList());
        result.put("selectedId", identities.current().map(value -> value.id().toString()).orElse(null)); result.put("temporary", true); result.put("message", "临时离线身份，本次会话有效；切换仅影响下次启动");
        return Optional.of(new ApiResponse(status, result));
    }
}
