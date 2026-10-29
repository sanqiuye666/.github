package cn.dreamingfish.dispatch.server.real;
import cn.dreamingfish.dispatch.server.model.*;import cn.dreamingfish.updater.protocol.JsonCodec;
import java.nio.file.*;import java.util.*;
/** 有界白名单日志，不接收HTTP正文、用户输入或认证秘密。 */
public final class ServerCallLog {
 private final Path root;private final String target;private final JsonCodec json=new JsonCodec();
 public ServerCallLog(Path root,String target){this.root=root;this.target=target.matches("[A-Za-z0-9_-]{1,64}")?target:"configured-server";ServerSecrets.safePath(root);}
 public synchronized void record(String requestId,String action,long started,String code,int attempts){try{ServerSecrets.safePath(root);Files.createDirectories(root);Path file=root.resolve("cloud-calls.jsonl"),old=root.resolve("cloud-calls.previous.jsonl");ServerSecrets.safePath(file);ServerSecrets.safePath(old);if(Files.exists(file)&&Files.size(file)>1024*1024)Files.move(file,old,StandardCopyOption.REPLACE_EXISTING);Files.writeString(file,json.writeString(Map.of("requestId",requestId,"target",target,"action",action,"durationMs",Math.max(0,(System.nanoTime()-started)/1000000),"resultCode",code,"attempts",attempts,"source","LIVE"))+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){System.err.println("三秋叶云调用日志无法写入；未输出请求内容。");}}
}
