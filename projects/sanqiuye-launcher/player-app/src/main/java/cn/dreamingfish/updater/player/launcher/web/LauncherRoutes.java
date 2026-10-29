package cn.dreamingfish.updater.player.launcher.web;

import java.io.IOException;
import java.util.Optional;

@FunctionalInterface
public interface LauncherRoutes {
    Optional<ApiResponse> handle(ApiRequest request) throws IOException;
}
