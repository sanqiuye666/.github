package cn.dreamingfish.updater.management.cli;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.net.*;import java.io.*;import java.time.*;import java.util.*;import picocli.CommandLine;import static org.junit.jupiter.api.Assertions.*;
class CloudCommandTest {
 @TempDir Path dir;
 @Test void explicitOwnerPipeEofStopsServiceAndDoesNotCreateManagementWorkspace()throws Exception{
  byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);String secret=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);Files.writeString(dir.resolve("admin.token"),secret);Files.writeString(dir.resolve("session.key"),secret);int port;try(var socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
  var config=dir.resolve("server.env");Files.writeString(config,"SQY_SERVER_ENABLED=true\nSQY_SERVER_MODE=real\nSQY_ROLE=server\nSQY_ACCOUNTS_ENABLED=true\nSQY_REGISTRATION_ENABLED=true\nSQY_TARGET_NAME=pipe-test\nSQY_LOG_DIR="+dir.resolve("logs")+"\nSQY_ACCOUNT_DB_PATH="+dir.resolve("db/accounts.sqlite")+"\nSQY_SESSION_SECRET_FILE="+dir.resolve("session.key")+"\nSQY_CLOUD_ADMIN_TOKEN_FILE="+dir.resolve("admin.token")+"\nSQY_HTTP_BIND_HOST=127.0.0.1\nSQY_HTTP_BIND_PORT="+port+"\n");
  var root=new ManagementCli(dir.resolve("unrelated/management-settings.json"),new StringReader(""));var cli=new CommandLine(root);var output=new StringWriter();cli.setOut(new PrintWriter(output));cli.setErr(new PrintWriter(output));int code=assertTimeoutPreemptively(Duration.ofSeconds(5),()->cli.execute("cloud","--config",config.toString(),"--stdin-lifetime"));assertEquals(0,code,output.toString());assertTrue(Files.exists(dir.resolve("db/accounts.sqlite")));assertFalse(Files.exists(dir.resolve("unrelated")));assertFalse(output.toString().contains(secret));
 }
}
