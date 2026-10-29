package cn.dreamingfish.updater.management.cli.cloud;
import cn.dreamingfish.dispatch.server.config.ServerConfig;import cn.dreamingfish.dispatch.server.wiring.ServerPortsFactory;import cn.dreamingfish.dispatch.server.model.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.net.*;import java.time.*;import java.util.*;import java.security.*;
import static org.junit.jupiter.api.Assertions.*;
class CloudAccountsTest {
 @TempDir Path root;
 private String secret() {byte[] data=new byte[32];new SecureRandom().nextBytes(data);return Base64.getUrlEncoder().withoutPadding().encodeToString(data);}
 private Map<String,String> config(String role)throws Exception {Path token=root.resolve("admin.token"),key=root.resolve("sessions.key");if(!Files.exists(token)){Files.writeString(token,secret());Files.writeString(key,secret());}var e=new HashMap<String,String>();e.put("SQY_SERVER_ENABLED","true");e.put("SQY_SERVER_MODE","real");e.put("SQY_ROLE",role);e.put("SQY_TARGET_NAME","local-test");e.put("SQY_API_BASE_URL","http://127.0.0.1:1/api/v1/");e.put("SQY_ALLOW_LOOPBACK_HTTP","true");e.put("SQY_LOG_DIR",root.resolve("logs-"+role).toString());e.put("SQY_ACCOUNTS_ENABLED","true");e.put("SQY_REGISTRATION_ENABLED","true");e.put("SQY_CLOUD_ADMIN_TOKEN_FILE",token.toString());e.put("SQY_SESSION_SECRET_FILE",key.toString());e.put("SQY_ACCOUNT_DB_PATH",root.resolve("database/accounts.sqlite").toString());e.put("SQY_HTTP_BIND_HOST","127.0.0.1");e.put("SQY_HTTP_BIND_PORT","1");return e;}
 private CallContext call(){return CallContext.create(Duration.ofSeconds(10));}
 @Test void realRegistrationIsVisibleToSeparateAdminAndSurvivesRestartWithoutPasswordsInLogs() throws Exception {
  var env=config("server");String password="integration-only passphrase";String id;
  try(var server=new CloudAccountServer(ServerConfig.load(null,env),new InetSocketAddress(InetAddress.getLoopbackAddress(),0))){server.start();String uri="http://127.0.0.1:"+server.port()+"/api/v1/";var pe=config("player");pe.put("SQY_API_BASE_URL",uri);var ae=config("admin");ae.put("SQY_API_BASE_URL",uri);var player=ServerPortsFactory.create(ServerConfig.load(null,pe));var admin=ServerPortsFactory.create(ServerConfig.load(null,ae));
   var a=player.panelAccounts().register(call(),UUID.randomUUID().toString(),"autumn_player","秋日玩家",password);id=a.id();assertEquals("default",a.groupId());assertEquals(id,player.panelAccounts().current(call()).id());assertEquals(id,admin.panelAccounts().list(call()).accounts().getFirst().id());
   assertEquals("FORBIDDEN",assertThrows(RemoteFailure.class,()->player.panelAccounts().list(call())).code());
   assertEquals("CONFLICT",assertThrows(RemoteFailure.class,()->player.panelAccounts().register(call(),UUID.randomUUID().toString(),"autumn_player","重复",password)).code());
   assertEquals("AUTH_FAILED",assertThrows(RemoteFailure.class,()->player.panelAccounts().login(call(),"autumn_player","wrong test passphrase")).code());
   player.panelAccounts().logout(call());assertEquals("AUTH_FAILED",assertThrows(RemoteFailure.class,()->player.panelAccounts().current(call())).code());
  }
  try(var server=new CloudAccountServer(ServerConfig.load(null,env),new InetSocketAddress(InetAddress.getLoopbackAddress(),0))){server.start();var pe=config("player");pe.put("SQY_API_BASE_URL","http://127.0.0.1:"+server.port()+"/api/v1/");var player=ServerPortsFactory.create(ServerConfig.load(null,pe));assertEquals(id,player.panelAccounts().login(call(),"autumn_player",password).id());}
  try(var files=Files.walk(root)){for(var file:files.filter(Files::isRegularFile).toList()){byte[] bytes=Files.readAllBytes(file);assertFalse(new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1).contains(password));if(file.toString().endsWith(".jsonl"))assertFalse(Files.readString(file).contains(Files.readString(root.resolve("admin.token"))));}}
 }

 @Test void publicRegistrationCannotChooseAdminOrGroupAndSecretsNeverAppearInPublicList() throws Exception {
  var env=config("server");try(var server=new CloudAccountServer(ServerConfig.load(null,env),new InetSocketAddress(InetAddress.getLoopbackAddress(),0))){server.start();var http=java.net.http.HttpClient.newHttpClient();String base="http://127.0.0.1:"+server.port()+"/api/v1/";
   String malicious="{\"operationId\":\""+UUID.randomUUID()+"\",\"username\":\"evil_player\",\"displayName\":\"name\",\"password\":\"integration only password\",\"role\":\"admin\",\"groupId\":\"admins\"}";
   var invalid=java.net.http.HttpRequest.newBuilder(URI.create(base+"account/register")).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(malicious)).build();assertEquals(400,http.send(invalid,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
   var admin=java.net.http.HttpRequest.newBuilder(URI.create(base+"admin/accounts")).GET().build();assertEquals(401,http.send(admin,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
   // 两名玩家争抢同名账号，使用独立会话；同一客户端的旧认证会被新请求取消。
   var pe=config("player");pe.put("SQY_API_BASE_URL",base);var firstPlayer=ServerPortsFactory.create(ServerConfig.load(null,pe));var secondPlayer=ServerPortsFactory.create(ServerConfig.load(null,pe));var pool=java.util.concurrent.Executors.newFixedThreadPool(2);try{var results=pool.invokeAll(List.of((java.util.concurrent.Callable<String>)()->{try{firstPlayer.panelAccounts().register(call(),UUID.randomUUID().toString(),"same_user","一","integration only password");return "OK";}catch(RemoteFailure e){return e.code();}},()->{try{secondPlayer.panelAccounts().register(call(),UUID.randomUUID().toString(),"same_user","二","integration only password");return "OK";}catch(RemoteFailure e){return e.code();}}));var codes=results.stream().map(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).sorted().toList();assertEquals(List.of("CONFLICT","OK"),codes);}finally{pool.shutdownNow();}
   var request=java.net.http.HttpRequest.newBuilder(URI.create(base+"admin/accounts")).header("Authorization","Bearer "+Files.readString(root.resolve("admin.token"))).GET().build();String response=http.send(request,java.net.http.HttpResponse.BodyHandlers.ofString()).body();assertFalse(response.contains("password"));assertFalse(response.contains("token"));assertFalse(response.contains("salt"));assertFalse(response.contains("hash"));assertTrue(response.contains("same_user"));
  }
 }

 @Test void refusesAnUnrelatedExistingSqliteDatabaseWithoutChangingIt()throws Exception{
  var env=config("server");Path file=Path.of(env.get("SQY_ACCOUNT_DB_PATH"));Files.createDirectories(file.getParent());try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+file);var q=c.createStatement()){q.execute("CREATE TABLE unrelated(value TEXT)");q.execute("INSERT INTO unrelated VALUES('keep my data')");}byte[] before=Files.readAllBytes(file);
  assertThrows(RemoteFailure.class,()->{try(var ignored=new CloudAccountServer(ServerConfig.load(null,env),new InetSocketAddress(InetAddress.getLoopbackAddress(),0))) {}});assertArrayEquals(before,Files.readAllBytes(file));
 }
}
