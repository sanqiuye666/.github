package cn.dreamingfish.dispatch.server.model;

import java.util.*;

/** 仅面板公开资料和模拟包元信息；这里没有游戏密码、令牌、私钥或本地存档路径。 */
public final class ServerModels {
    private ServerModels(){}
    public record Actor(String role,String playerId){
        public static Actor admin(){return new Actor("admin",null);}
        public static Actor player(String id){UUID.fromString(id);return new Actor("player",id);}
    }
    public record Status(boolean enabled,String source,String role,String scenario,String message){}
    public record Group(String id,String name){}
    public record Report(String packId,String releaseId,String status,String updatedAt,String source){}
    public record Profile(String id,String username,String displayName,String groupId,long revision,Report report){}
    public record Account(String id,String username,String displayName,String groupId,String createdAt,String lastSeenAt){}
    public record AccountDirectory(List<Account> accounts,long total,int limit,String checkedAt){public AccountDirectory{accounts=List.copyOf(accounts);}}
    public record Upload(String name,String fileName,long sizeBytes,String gameVersion,String loader){}
    public record Assignment(String type,String targetId){}
    public record Pack(String id,String name,String fileName,long sizeBytes,String gameVersion,String loader,String status,long revision,String releaseId,List<Assignment> assignments,String deletedAt,String source){
        public Pack {assignments=List.copyOf(assignments);}
    }
    public record Manifest(String packId,String releaseId,String source,boolean simulatedSignature,String message){}
    public record Operation(String id,String status,String source,String message){}
    public record RemoteResult<T>(int schemaVersion,String requestId,T data,Error error,long durationMs,int attempts,String source){}
    public record Error(String code,String message){}
}
