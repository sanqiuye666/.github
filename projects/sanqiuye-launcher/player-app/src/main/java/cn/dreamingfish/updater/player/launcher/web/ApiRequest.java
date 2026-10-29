package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.storage.LauncherJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public record ApiRequest(String method, String path, Map<String,String> query, JsonNode body) {
    public ApiRequest { query = Map.copyOf(query); }
    public void onlyFields(Set<String> allowed) {
        if (body != null) body.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key)) throw invalid("请求包含未知字段");
        });
    }
    public void onlyQuery(String... allowed) {
        if (!Set.of(allowed).containsAll(query.keySet())) throw invalid("请求包含未知查询参数");
    }
    public void requireMethod(String expected) {
        if (!method.equals(expected)) throw new LauncherApiException(405,"METHOD_NOT_ALLOWED","此操作不支持该请求方法");
    }
    public String text(String key, int maximum) { return LauncherJson.text(body,key,maximum); }
    public static LauncherApiException invalid(String message) { return new LauncherApiException(400,"INVALID_INPUT",message); }
}
