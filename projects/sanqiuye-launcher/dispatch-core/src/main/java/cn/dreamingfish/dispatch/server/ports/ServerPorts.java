package cn.dreamingfish.dispatch.server.ports;

import cn.dreamingfish.dispatch.server.model.CallContext;
import cn.dreamingfish.dispatch.server.model.ServerModels.*;
import java.util.List;

/** 分能力契约：业务方只取得所需端口，禁止任意SSH命令、SQL或文件路径。 */
public interface ServerPorts {
    Status status();
    default PanelAccountPort panelAccounts(){throw cn.dreamingfish.dispatch.server.model.RemoteFailure.of("FEATURE_DISABLED");}
    interface PanelAccountPort {
        Account register(CallContext call,String operationId,String username,String displayName,String password);
        Account login(CallContext call,String username,String password);
        Account current(CallContext call);
        Operation logout(CallContext call);
        AccountDirectory list(CallContext call);
    }
    PanelSessionPort sessions();PlayerProfilePort profiles();DispatchDirectoryPort directory();
    ManagedPackPort packs();PackAdminPort packAdmin();PackPublishPort publish();PackContentPort content();
    ServerRuntimePort runtime();ServerFilePort files();MinecraftControlPort minecraft();
    PanelAccountRepository accounts();ServerReleasePort release();
    interface PanelSessionPort {
        Profile register(CallContext call,String operationId,String username,String displayName);
        Profile login(CallContext call,String username);
        Profile refresh(CallContext call,Actor actor);
        Operation logout(CallContext call,Actor actor);
    }
    interface PlayerProfilePort {
        Profile readMe(CallContext call,Actor actor);
        Profile updateMe(CallContext call,Actor actor,String operationId,long revision,String displayName);
        Profile reportManagedInstance(CallContext call,Actor actor,String operationId,String packId,String status);
    }
    interface DispatchDirectoryPort {
        List<Group> listGroups(CallContext call,Actor actor);
        List<Profile> listPlayers(CallContext call,Actor actor);
        Profile setGroup(CallContext call,Actor actor,String operationId,String playerId,long revision,String groupId);
    }
    interface ManagedPackPort {List<Pack> list(CallContext call,Actor actor);Pack getRelease(CallContext call,Actor actor,String packId);}
    interface PackAdminPort {
        List<Pack> list(CallContext call,Actor actor);
        Pack beginUpload(CallContext call,Actor actor,String operationId,Upload upload);
        Pack getImport(CallContext call,Actor actor,String packId);
        Pack assign(CallContext call,Actor actor,String operationId,String packId,long revision,String targetType,String targetId);
        Pack unlist(CallContext call,Actor actor,String operationId,String packId,long revision);
        Pack delete(CallContext call,Actor actor,String operationId,String packId,long revision,String confirmation);
        Pack restore(CallContext call,Actor actor,String operationId,String packId,long revision);
    }
    interface PackPublishPort {Pack prepare(CallContext call,Actor actor,String packId);Pack activate(CallContext call,Actor actor,String operationId,String packId,long revision);Pack status(CallContext call,Actor actor,String packId);}
    interface PackContentPort {Manifest readManifest(CallContext call,Actor actor,String packId);Operation downloadObject(CallContext call,Actor actor,String packId,String objectId);}
    interface ServerRuntimePort {Operation inspect(CallContext call,Actor actor);Operation ensureService(CallContext call,Actor actor,String operationId,boolean enabled);}
    interface ServerFilePort {Operation stat(CallContext call,Actor actor,String objectId);Operation upload(CallContext call,Actor actor,String operationId,String objectId,long sizeBytes);Operation download(CallContext call,Actor actor,String objectId);}
    interface MinecraftControlPort {Operation status(CallContext call,Actor actor);Operation listPlayers(CallContext call,Actor actor);Operation saveWorld(CallContext call,Actor actor,String operationId);}
    interface PanelAccountRepository {Profile find(CallContext call,Actor actor,String playerId);}
    interface ServerReleasePort {Operation backup(CallContext call,Actor actor,String operationId);Operation activate(CallContext call,Actor actor,String operationId,String backupId);Operation rollback(CallContext call,Actor actor,String operationId,String backupId);Operation status(CallContext call,Actor actor);}
}
