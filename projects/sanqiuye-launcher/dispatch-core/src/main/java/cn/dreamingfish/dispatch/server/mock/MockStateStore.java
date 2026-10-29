package cn.dreamingfish.dispatch.server.mock;

import cn.dreamingfish.dispatch.server.model.*;
import cn.dreamingfish.dispatch.server.model.ServerModels.*;
import cn.dreamingfish.updater.protocol.JsonCodec;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import static java.nio.file.StandardOpenOption.*;

/** 两个独立JVM共享演练状态；锁覆盖读取、修订检查和原子替换。 */
final class MockStateStore {
    static final int MAX_BYTES=4*1024*1024;
    private final Path root;private final JsonCodec json=new JsonCodec();
    MockStateStore(Path root){this.root=root;}
    record Receipt(String fingerprint,String kind,String data,String createdAt){}
    record State(int schemaVersion,Map<String,Profile> players,Map<String,Pack> packs,Map<String,Receipt> operations){
        State(){this(1,new TreeMap<>(),new TreeMap<>(),new TreeMap<>());}
    }
    <T> T transaction(CallContext call,boolean write,Function<State,T> work){
        call.check();
        try{
            safeRoot(root);Path lockFile=root.resolve("state.lock");if(Files.isSymbolicLink(lockFile))throw RemoteFailure.of("INVALID_DATA");
            try(var channel=FileChannel.open(lockFile,CREATE,WRITE);var lock=acquire(channel,call)){
                Path file=root.resolve("state.json");State state;
                if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)){
                    if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||Files.size(file)>MAX_BYTES)throw RemoteFailure.of("INVALID_DATA");
                    try{state=json.read(Files.readAllBytes(file),State.class);validate(state);}catch(RuntimeException e){throw RemoteFailure.of("INVALID_DATA");}
                }else state=new State();
                T result=work.apply(state);call.check();
                if(write){
                    validate(state);byte[] bytes=json.writePretty(state);if(bytes.length>MAX_BYTES)throw RemoteFailure.of("SERVER_ERROR");
                    Path temp=Files.createTempFile(root,"state-",".tmp");Files.write(temp,bytes);
                    Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                }
                return result;
            }
        }catch(RemoteFailure e){throw e;}catch(Exception e){throw RemoteFailure.of("SERVER_ERROR");}
    }
    static FileLock acquire(FileChannel channel,CallContext call) throws Exception{
        while(true){call.check();try{var lock=channel.tryLock();if(lock!=null)return lock;}catch(OverlappingFileLockException ignored){}Thread.sleep(10);}
    }
    static void safeRoot(Path root) throws java.io.IOException{
        Path current=root.toAbsolutePath().normalize();
        for(Path path=current;path!=null;path=path.getParent())if(Files.isSymbolicLink(path))throw RemoteFailure.of("INVALID_DATA");
        Files.createDirectories(current);
    }
    private static void validate(State state){
        if(state==null||state.schemaVersion()!=1||state.players()==null||state.packs()==null||state.operations()==null||state.players().size()>512||state.packs().size()>512||state.operations().size()>2048)throw RemoteFailure.of("INVALID_DATA");
        state.players().forEach((id,p)->{if(p==null||!id.equals(p.id())||p.revision()<1||p.username()==null||p.displayName()==null||p.groupId()==null)throw RemoteFailure.of("INVALID_DATA");UUID.fromString(id);});
        state.packs().forEach((id,p)->{if(p==null||!id.equals(p.id())||p.revision()<1||p.assignments()==null||p.assignments().size()>512||!"MOCK".equals(p.source())||!Set.of("PREVIEW","PUBLISHED","UNLISTED","DELETED").contains(p.status()))throw RemoteFailure.of("INVALID_DATA");UUID.fromString(id);});
        state.operations().forEach((id,r)->{UUID.fromString(id);if(r==null||r.fingerprint()==null||r.data()==null)throw RemoteFailure.of("INVALID_DATA");Instant.parse(r.createdAt());});
    }
}
