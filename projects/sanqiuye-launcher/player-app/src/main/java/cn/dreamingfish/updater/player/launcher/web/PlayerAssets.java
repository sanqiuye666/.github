package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.storage.LauncherJson;
import java.io.*;
import java.util.*;

final class PlayerAssets {
    private static final String PREFIX="cn/dreamingfish/updater/player/launcher/web/ui/";
    private final ClassLoader resources;
    private final Set<String> files;
    PlayerAssets(ClassLoader resources) throws IOException {
        this.resources=resources;
        var marker=LauncherJson.read(read("build-info.json",1024*1024),1024*1024);
        var outputs=marker.path("outputs");
        if (!outputs.isObject() || outputs.size()>4096 || !outputs.has("index.html")) throw new IOException("玩家界面资源不完整，请重新构建");
        var names=new HashSet<String>();
        outputs.fieldNames().forEachRemaining(name -> { if (safe(name)) names.add(name); });
        files=Set.copyOf(names);
    }
    private static boolean safe(String path) {
        return !path.startsWith("/") && !path.contains("\\") && !path.contains(":") && !path.contains("%")
                && Arrays.stream(path.split("/",-1)).noneMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."));
    }
    byte[] asset(String rawPath) throws IOException {
        String path=rawPath.equals("/")?"index.html":rawPath.substring(1);
        if (!safe(path) || !files.contains(path)) throw new LauncherApiException(404,"NOT_FOUND","没有此界面资源");
        return read(path,16*1024*1024);
    }
    private byte[] read(String path,int maximum) throws IOException {
        try (InputStream stream=resources.getResourceAsStream(PREFIX+path)) {
            if (stream==null) throw new IOException("玩家界面未打包，请先构建前端");
            byte[] bytes=stream.readNBytes(maximum+1);
            if (bytes.length>maximum) throw new IOException("玩家界面资源过大");
            return bytes;
        }
    }
    static String contentType(String path) {
        if (path.equals("/") || path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
}
