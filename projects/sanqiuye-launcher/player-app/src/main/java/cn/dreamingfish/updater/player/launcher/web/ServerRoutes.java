package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.dispatch.server.web.ServerEndpoint;
import java.util.*;

/** 原本机会话和来源检查通过后才进入。业务错误保留结构化调用信封。 */
public final class ServerRoutes implements LauncherRoutes {
    private final ServerEndpoint endpoint;
    public ServerRoutes(ServerEndpoint endpoint){this.endpoint=Objects.requireNonNull(endpoint);}
    @Override public Optional<ApiResponse> handle(ApiRequest request){
        if(!request.path().startsWith("/server/"))return Optional.empty();request.onlyQuery();
        return Optional.of(new ApiResponse(200,endpoint.handle(request.method(),request.path().substring(7),request.body())));
    }
}
