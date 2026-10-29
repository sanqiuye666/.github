package cn.dreamingfish.updater.management.cli.cloud;
import cn.dreamingfish.dispatch.server.config.ServerConfig;import cn.dreamingfish.dispatch.server.model.*;import cn.dreamingfish.dispatch.server.real.*;
import cn.dreamingfish.updater.protocol.JsonCodec;import com.sun.net.httpserver.*;import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;import java.nio.file.*;import java.nio.channels.*;import java.io.*;import java.time.*;import java.security.*;import java.util.*;import java.util.concurrent.*;
/** 独立云账号入口，只绑定回环；公网TLS或SSH隧道在进程外配置。 */
public final class CloudAccountServer implements AutoCloseable {
 private final ServerConfig config;private final HttpServer server;private final ThreadPoolExecutor workers;private final ScheduledExecutorService deadline;private final CloudAccountDatabase database;private final String admin;private final JsonCodec json=new JsonCodec();private final ServerCallLog log;private final FileChannel owner;private final FileLock lock;
 private final Map<String,long[]> rates=new HashMap<>();
 public CloudAccountServer(ServerConfig config){this(config,new InetSocketAddress(config.value("HTTP_BIND_HOST"),(int)config.number("HTTP_BIND_PORT")));}
 public CloudAccountServer(ServerConfig config,InetSocketAddress address){this.config=config;if(!config.enabled()||!config.mode().equals("real")||!config.role().equals("server")||!config.flag("ACCOUNTS_ENABLED"))throw RemoteFailure.of("CONFIG_INVALID");if(address.getAddress()==null||!address.getAddress().isLoopbackAddress())throw new RemoteFailure("CONFIG_INVALID","账号服务必须绑定回环地址，通过HTTPS反代或SSH隧道访问");
  log=new ServerCallLog(config.logRoot(),config.value("TARGET_NAME"));admin=ServerSecrets.read(Path.of(config.value("CLOUD_ADMIN_TOKEN_FILE")),this::warning);String key=ServerSecrets.read(Path.of(config.value("SESSION_SECRET_FILE")),this::warning);
  FileChannel channel=null;FileLock acquired=null;try{Path file=Path.of(config.value("ACCOUNT_DB_PATH")),lockFile=file.resolveSibling(file.getFileName()+".service.lock");ServerSecrets.safePath(lockFile);Files.createDirectories(file.getParent());channel=FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE);acquired=channel.tryLock();if(acquired==null)throw new IOException();database=new CloudAccountDatabase(file,key,config.value("DEFAULT_GROUP_ID"),(int)config.number("TIMEOUT_DB_MS"));
   cn.dreamingfish.updater.management.HttpServerResources.configure();server=HttpServer.create(address,32);
  }catch(Exception e){try{if(acquired!=null)acquired.close();if(channel!=null)channel.close();}catch(IOException ignored){}if(e instanceof RemoteFailure failure)throw failure;throw new RemoteFailure("CONFIG_INVALID","账号数据库已被占用或监听端口不可用");}owner=channel;lock=acquired;
  workers=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"sqy-cloud-request");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());deadline=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"sqy-cloud-deadline");t.setDaemon(true);return t;});server.setExecutor(workers);var context=server.createContext("/api/v1/",this::handle);context.getFilters().add(cn.dreamingfish.updater.management.HttpServerResources.connectionLifecycle());
 }
 private void warning(String code){System.err.println("三秋叶账号服务："+code+"，请检查私有密钥文件权限。");}
 public int port(){return server.getAddress().getPort();}public void start(){server.start();}
 @Override public void close(){server.stop(0);workers.shutdownNow();deadline.shutdownNow();try{workers.awaitTermination(5,TimeUnit.SECONDS);lock.close();owner.close();}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(IOException ignored){}}
 private synchronized void rate(String category){long now=System.currentTimeMillis();var window=rates.computeIfAbsent(category,k->new long[]{now,0});if(now-window[0]>=60000){window[0]=now;window[1]=0;}int limit=category.equals("credentials")?30:120;if(++window[1]>limit)throw new RemoteFailure("RATE_LIMITED","请求过于频繁，请一分钟后重试");}
 private void handle(HttpExchange exchange)throws IOException {
  String id=exchange.getRequestHeaders().getFirst("X-Request-ID");try{UUID.fromString(id);}catch(Exception e){id=UUID.randomUUID().toString();}long started=System.nanoTime();String action="unknown",result="OK";var timeout=deadline.schedule(exchange::close,Math.min(30000,config.number("TIMEOUT_SESSION_MS")),TimeUnit.MILLISECONDS);
  try{String path=exchange.getRequestURI().getRawPath();if(exchange.getRequestURI().getRawQuery()!=null||exchange.getRequestHeaders().containsKey("Origin")||exchange.getRequestHeaders().containsKey("Sec-Fetch-Site"))throw RemoteFailure.of("FORBIDDEN");
   if(!Set.of("/api/v1/account/register","/api/v1/account/login","/api/v1/account/me","/api/v1/account/logout","/api/v1/admin/accounts","/api/v1/health").contains(path))throw RemoteFailure.of("NOT_FOUND");action=path.substring(8);boolean credentials=path.endsWith("/register")||path.endsWith("/login");rate(credentials?"credentials":"reads");Object response;
   if(path.equals("/api/v1/health")&&exchange.getRequestMethod().equals("GET"))response=Map.of("status","ready","schemaVersion",1);
   else if(path.equals("/api/v1/admin/accounts")&&exchange.getRequestMethod().equals("GET")){String token=token(exchange);if(!MessageDigest.isEqual(admin.getBytes(java.nio.charset.StandardCharsets.US_ASCII),token.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw RemoteFailure.of("AUTH_FAILED");response=database.list();}
   else if(path.equals("/api/v1/account/me")&&exchange.getRequestMethod().equals("GET"))response=Map.of("account",database.current(token(exchange)));
   else if(exchange.getRequestMethod().equals("POST")){
    JsonNode body=body(exchange);
    response=switch(path){
     case "/api/v1/account/register"->{if(!config.flag("REGISTRATION_ENABLED"))throw RemoteFailure.of("FEATURE_DISABLED");fields(body,"operationId","username","displayName","password");yield database.register(text(body,"operationId",36),text(body,"username",32),text(body,"displayName",64),text(body,"password",256));}
     case "/api/v1/account/login"->{fields(body,"username","password");yield database.login(text(body,"username",32),text(body,"password",256));}
     case "/api/v1/account/logout"->{fields(body);database.logout(token(exchange));yield Map.of("loggedOut",true);}
     default->throw RemoteFailure.of("FORBIDDEN");};
   }else throw RemoteFailure.of("FORBIDDEN");send(exchange,200,response,id);
  }catch(RemoteFailure e){result=e.code();send(exchange,e.code().equals("RATE_LIMITED")?429:e.httpStatus(),Map.of("error",Map.of("code",e.code(),"message",e.getMessage())),id);}
  catch(RuntimeException e){result="INVALID_DATA";send(exchange,400,Map.of("error",Map.of("code",result,"message","请求格式不符")),id);}
  catch(IOException e){result="CONNECT_FAILED";throw e;}
  finally{timeout.cancel(false);log.record(id,action,started,result,1);exchange.close();}
 }
 private static String token(HttpExchange e){var values=e.getRequestHeaders().get("Authorization");if(values==null||values.size()!=1||!values.getFirst().matches("Bearer [A-Za-z0-9_-]{43,128}"))throw RemoteFailure.of("AUTH_FAILED");return values.getFirst().substring(7);}
 private JsonNode body(HttpExchange e)throws IOException{if(!e.getRequestHeaders().getFirst("Content-Type").equalsIgnoreCase("application/json"))throw RemoteFailure.of("INVALID_DATA");byte[] data=e.getRequestBody().readNBytes(8193);if(data.length>8192)throw RemoteFailure.of("INVALID_DATA");try{var n=json.read(data,JsonNode.class);if(!n.isObject())throw RemoteFailure.of("INVALID_DATA");return n;}catch(RuntimeException x){throw RemoteFailure.of("INVALID_DATA");}}
 private static void fields(JsonNode b,String...allowed){var names=Set.of(allowed);if(b.size()!=names.size())throw RemoteFailure.of("INVALID_DATA");b.fieldNames().forEachRemaining(k->{if(!names.contains(k))throw RemoteFailure.of("INVALID_DATA");});}
 private static String text(JsonNode b,String name,int max){var v=b.path(name);if(!v.isTextual()||v.asText().length()>max)throw RemoteFailure.of("INVALID_DATA");return v.asText();}
 private void send(HttpExchange e,int status,Object value,String requestId)throws IOException{byte[] data=json.write(value);e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");e.getResponseHeaders().set("Cache-Control","no-store");e.getResponseHeaders().set("X-Content-Type-Options","nosniff");e.getResponseHeaders().set("X-Request-ID",requestId);e.sendResponseHeaders(status,data.length);e.getResponseBody().write(data);}
}
