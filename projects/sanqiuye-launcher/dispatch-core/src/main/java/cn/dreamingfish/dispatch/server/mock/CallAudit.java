package cn.dreamingfish.dispatch.server.mock;

import cn.dreamingfish.dispatch.server.model.*;
import cn.dreamingfish.updater.protocol.JsonCodec;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Map;
import static java.nio.file.StandardOpenOption.*;

/** 白名单日志；最多两份各1MiB，不打印对象、请求体、异常原因链或用户输入。 */
final class CallAudit {
    private final Path root;private final JsonCodec json=new JsonCodec();
    CallAudit(Path root){this.root=root;}
    void record(CallContext call,String action,long started,String code){
        try{
            MockStateStore.safeRoot(root);var file=root.resolve("server-calls.jsonl");var lockFile=root.resolve("audit.lock");
            if(Files.isSymbolicLink(file)||Files.isSymbolicLink(lockFile))throw RemoteFailure.of("INVALID_DATA");
            try(var channel=FileChannel.open(lockFile,CREATE,WRITE);var lock=MockStateStore.acquire(channel,CallContext.create(java.time.Duration.ofSeconds(2)))){
                Path previous=root.resolve("server-calls.previous.jsonl");if(Files.isSymbolicLink(previous))throw RemoteFailure.of("INVALID_DATA");
                if(Files.exists(file)&&Files.size(file)>1024*1024)Files.move(file,previous,StandardCopyOption.REPLACE_EXISTING);
                var line=json.writeString(Map.of("requestId",call.requestId(),"target","mock","action",action,"durationMs",Math.max(0,(System.nanoTime()-started)/1000000),"resultCode",code,"attempts",1,"source","MOCK"));
                Files.writeString(file,line+"\n",CREATE,APPEND);
            }
        }catch(RemoteFailure e){throw e;}catch(Exception e){throw RemoteFailure.of("SERVER_ERROR");}
    }
}
