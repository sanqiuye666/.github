package cn.dreamingfish.updater.player.launcher.web;

import com.sun.net.httpserver.HttpExchange;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** One process, one exact loopback origin. The token is never an account identity. */
final class LocalRequestGuard {
    private final String host;
    private final String origin;
    private final String token;
    LocalRequestGuard(int port) {
        host="127.0.0.1:"+port; origin="http://"+host;
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        token=HexFormat.of().formatHex(bytes);
    }
    String token() { return token; }
    void origin(HttpExchange exchange) {
        var headers=exchange.getRequestHeaders();
        var hosts=headers.get("Host");
        if (hosts!=null && hosts.size()!=1) throw ApiRequest.invalid("Host 请求头重复");
        if (hosts==null || !host.equals(hosts.getFirst())) deny();
        var origins=headers.get("Origin");
        if (origins!=null && (origins.size()!=1 || !origin.equals(origins.getFirst()))) deny();
        String site=headers.getFirst("Sec-Fetch-Site");
        if (site!=null && !Set.of("same-origin","none").contains(site)) deny();
    }
    void api(HttpExchange exchange,boolean bootstrap) {
        var headers=exchange.getRequestHeaders();
        if (!single(headers.get("X-SQY-Client"),"launcher")) deny();
        if ("iframe".equals(headers.getFirst("Sec-Fetch-Dest"))) deny();
        if (!bootstrap) {
            var values=headers.get("X-SQY-Session");
            if (values==null || values.size()!=1 || values.getFirst().length()!=token.length()
                    || !MessageDigest.isEqual(token.getBytes(StandardCharsets.US_ASCII),values.getFirst().getBytes(StandardCharsets.UTF_8))) deny();
        }
    }
    private static boolean single(List<String> values,String expected) { return values!=null && values.size()==1 && expected.equals(values.getFirst()); }
    private static void deny() { throw new LauncherApiException(403,"SESSION_REQUIRED","请从本机玩家面板重新连接"); }
}
