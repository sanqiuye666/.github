package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.storage.LauncherJson;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class LauncherWebServer implements AutoCloseable {
    public static final String API="/api/launcher/v1";
    private final HttpServer server;
    private final ThreadPoolExecutor workers;
    private final ExecutorService rejections=Executors.newVirtualThreadPerTaskExecutor();
    private final ThreadLocal<Boolean> overloaded=new ThreadLocal<>();
    private final LauncherApi api;
    private final LocalRequestGuard guard;
    private final PlayerAssets assets;
    private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
    private LauncherWebServer(int port,LauncherApi api,ClassLoader resources) throws IOException {
        if (port<0 || port>65535) throw new IllegalArgumentException("端口范围为 0–65535");
        this.api=Objects.requireNonNull(api);
        assets=new PlayerAssets(resources);
        configure();
        server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),32);
        guard=new LocalRequestGuard(server.getAddress().getPort());
        workers=new ThreadPoolExecutor(8,8,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),
                Thread.ofPlatform().daemon(true).name("sqy-player-http-",0).factory(),(task,executor)->
                rejections.execute(()->{overloaded.set(true);try{task.run();}finally{overloaded.remove();}}));
        server.setExecutor(workers);
        server.createContext("/",this::handle);
        server.start();
    }
    public static LauncherWebServer start(int port,LauncherApi api,ClassLoader resources) throws IOException {
        return new LauncherWebServer(port,api,resources);
    }
    public URI baseUri() { return URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"); }
    private static void configure() {
        Map.of("jdk.httpserver.maxConnections","64","sun.net.httpserver.maxIdleConnections","16",
                "sun.net.httpserver.maxReqTime","30","sun.net.httpserver.maxRspTime","60",
                "sun.net.httpserver.maxReqHeaders","64","sun.net.httpserver.maxReqHeaderSize","16384",
                "sun.net.httpserver.drainAmount","0","sun.net.httpserver.idleInterval","15")
                .forEach((key,value)->{if(System.getProperty(key)==null)System.setProperty(key,value);});
    }
    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Connection","close");
            exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
            exchange.getResponseHeaders().set("X-Frame-Options","DENY");
            exchange.getResponseHeaders().set("Cross-Origin-Resource-Policy","same-origin");
            exchange.getResponseHeaders().set("Referrer-Policy","no-referrer");
            try {
                if (Boolean.TRUE.equals(overloaded.get())) throw new LauncherApiException(503,"BUSY","本机操作繁忙，请稍后重试");
                guard.origin(exchange);
                if (exchange.getRequestURI().toASCIIString().length()>4096) throw new LauncherApiException(414,"INVALID_INPUT","请求地址过长");
                String path=exchange.getRequestURI().getRawPath();
                if (path.startsWith(API+"/") || path.equals(API)) handleApi(exchange,path.substring(API.length()));
                else {
                    if (!Set.of("GET","HEAD").contains(exchange.getRequestMethod())) throw new LauncherApiException(405,"METHOD_NOT_ALLOWED","资源仅支持读取");
                    exchange.getResponseHeaders().set("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'none'; form-action 'self'");
                    send(exchange,200,assets.asset(path),PlayerAssets.contentType(path));
                }
            } catch (LauncherApiException error) { json(exchange,ApiResponse.error(error.status(),error.code(),error.getMessage())); }
            catch (IllegalArgumentException error) { json(exchange,ApiResponse.error(400,"INVALID_INPUT","请求参数或 JSON 格式无效")); }
            catch (IOException error) { json(exchange,ApiResponse.error(503,"IO_ERROR","文件或网络操作未完成，请检查连接和目录权限")); }
            catch (RuntimeException error) { json(exchange,ApiResponse.error(500,"INTERNAL_ERROR","本机操作未完成，请查看运行记录后重试")); }
        }
    }
    private void handleApi(HttpExchange exchange,String path) throws IOException {
        boolean bootstrap=path.equals("/session");
        guard.api(exchange,bootstrap);
        String method=exchange.getRequestMethod();
        if (!Set.of("GET","POST").contains(method)) throw new LauncherApiException(405,"METHOD_NOT_ALLOWED","本机接口只支持 GET 或 POST");
        var query=query(exchange.getRequestURI().getRawQuery());
        long length=0;
        try { length=Long.parseLong(exchange.getRequestHeaders().getFirst("Content-Length")==null?"0":exchange.getRequestHeaders().getFirst("Content-Length")); }
        catch (NumberFormatException invalid) { throw ApiRequest.invalid("请求长度无效"); }
        int bodyLimit = method.equals("POST") && path.matches("/instances/[0-9a-f-]{36}/launch-settings") ? 131072 : 16384;
        if (length<0) throw ApiRequest.invalid("请求长度无效");
        if (length>bodyLimit) {
            // 已通过本机会话门；有界消费超限哨兵，避免关闭未读请求导致错误正文被连接重置丢弃。
            if (method.equals("POST")) exchange.getRequestBody().readNBytes(bodyLimit+1);
            throw new LauncherApiException(413,"REQUEST_TOO_LARGE","请求正文超过本操作的大小上限");
        }
        var body=(com.fasterxml.jackson.databind.JsonNode)null;
        if (method.equals("POST")) {
            String contentType=exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType==null || !contentType.split(";",2)[0].trim().equalsIgnoreCase("application/json")) throw ApiRequest.invalid("请求正文必须为 JSON");
            byte[] bytes=exchange.getRequestBody().readNBytes(bodyLimit+1);
            if (bytes.length>bodyLimit) throw new LauncherApiException(413,"REQUEST_TOO_LARGE","请求正文超过本操作的大小上限");
            try { body=LauncherJson.read(bytes,bodyLimit); }
            catch (IOException invalid) { throw ApiRequest.invalid("JSON 为空、重复或格式不正确"); }
        } else if (length>0 || exchange.getRequestHeaders().containsKey("Transfer-Encoding")) throw ApiRequest.invalid("读取接口不接受正文");
        if (bootstrap) {
            if (!method.equals("GET")) throw new LauncherApiException(405,"METHOD_NOT_ALLOWED","会话仅支持读取");
            if (!query.isEmpty()) throw ApiRequest.invalid("会话不接受查询参数");
            json(exchange,new ApiResponse(200,Map.of("schemaVersion",1,"mode","LOCAL","sessionToken",guard.token(),"capabilities",api.capabilities())));
        } else json(exchange,api.handle(new ApiRequest(method,path,query,body)));
    }
    private static Map<String,String> query(String raw) {
        var result=new LinkedHashMap<String,String>();
        if (raw!=null && !raw.isEmpty()) for (String pair:raw.split("&",-1)) {
            String[] parts=pair.split("=",2);
            String key=URLDecoder.decode(parts[0],StandardCharsets.UTF_8);
            String value=parts.length==2?URLDecoder.decode(parts[1],StandardCharsets.UTF_8):"";
            if (key.isEmpty() || result.putIfAbsent(key,value)!=null) throw ApiRequest.invalid("查询参数重复或无效");
        }
        return result;
    }
    private static void json(HttpExchange exchange,ApiResponse response) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        if(response.status()==429&&response.body() instanceof Map<?,?> body&&body.get("error") instanceof Map<?,?> error&&error.get("retryAfterSeconds") instanceof Number seconds){
            exchange.getResponseHeaders().set("Retry-After",Long.toString(Math.max(1,Math.min(3600,seconds.longValue()))));
        }
        send(exchange,response.status(),LauncherJson.write(response.body()),"application/json; charset=utf-8");
    }
    private static void send(HttpExchange exchange,int status,byte[] body,String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type",contentType);
        if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.getResponseHeaders().set("Content-Length",Integer.toString(body.length));
            exchange.sendResponseHeaders(status,-1);
        } else {
            exchange.sendResponseHeaders(status,body.length);
            exchange.getResponseBody().write(body);
        }
    }
    @Override public void close() { if(!closed.compareAndSet(false,true))return;server.stop(0);workers.shutdownNow();rejections.shutdownNow(); }
}
