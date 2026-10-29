package cn.dreamingfish.updater.player.launcher.web;

import java.io.IOException;
import cn.dreamingfish.launcher.launch.LaunchFailure;
import java.util.*;

public final class LauncherApi {
    private final List<LauncherRoutes> routes;
    private final Map<String,String> capabilities;
    public LauncherApi(List<LauncherRoutes> routes) {
        this(routes,Map.of("catalog","AVAILABLE","instances","AVAILABLE","vanilla","PENDING","mods","PENDING"));
    }
    public LauncherApi(List<LauncherRoutes> routes,Map<String,String> capabilities) {
        this.routes=List.copyOf(routes); this.capabilities=Map.copyOf(capabilities);
    }
    public Map<String,String> capabilities() { return capabilities; }
    public ApiResponse handle(ApiRequest request) throws IOException {
        for (var route : routes) {
            Optional<ApiResponse> response;
            try { response=route.handle(request); }
            catch (LaunchFailure failure) {
                int status = failure.code().endsWith("_NOT_FOUND") ? 404 : Set.of("INVALID_ARGUMENT", "CONFIRMATION_REQUIRED").contains(failure.code()) ? 400 : Set.of("CHECK_LIMIT", "SERVICE_CLOSED").contains(failure.code()) ? 503 : 409;
                throw new LauncherApiException(status, failure.code(), failure.getMessage());
            }
            if (response.isPresent()) return response.get();
        }
        return ApiResponse.error(404,"NOT_FOUND","没有此本机操作");
    }
}
