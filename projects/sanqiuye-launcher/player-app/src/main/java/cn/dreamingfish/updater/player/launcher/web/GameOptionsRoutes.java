package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.downloads.TaskRequest;
import cn.dreamingfish.launcher.options.*;
import java.io.IOException;
import java.util.*;

/** 仅提供结构化默认选项，不能传入文件路径或任意游戏配置键。 */
public final class GameOptionsRoutes implements LauncherRoutes {
    private final GameOptionsStore store;
    public GameOptionsRoutes(GameOptionsStore store) {this.store=Objects.requireNonNull(store);}
    @Override public Optional<ApiResponse> handle(ApiRequest request) throws IOException {
        if(!request.path().equals("/game-options"))return Optional.empty();request.onlyQuery();
        GameOptionsStore.Snapshot value;
        if(request.method().equals("GET"))value=store.read();
        else {
            request.requireMethod("POST");request.onlyFields(Set.of("revision","enabled","values"));
            if(!request.body().path("enabled").isBoolean())throw ApiRequest.invalid("需要明确是否启用启动同步");
            value=store.save(TaskRequest.uuid(request.text("revision",36)),request.body().path("enabled").booleanValue(),GameOptionsStore.parseValues(request.body().path("values")));
        }
        return Optional.of(new ApiResponse(200,Map.of("revision",value.revision().toString(),"enabled",value.enabled(),"values",value.values(),"fields",Arrays.stream(GameOption.values()).map(GameOption::view).toList(),"supportedVersions",GameOptionsSync.SUPPORTED.keySet().stream().sorted().toList())));
    }
}
