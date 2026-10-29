package cn.dreamingfish.updater.player.launcher.web;

import java.util.Map;

public record ApiResponse(int status, Object body) {
    public static ApiResponse error(int status,String code,String message) {
        return new ApiResponse(status,Map.of("error",Map.of("code",code,"message",message)));
    }
}
