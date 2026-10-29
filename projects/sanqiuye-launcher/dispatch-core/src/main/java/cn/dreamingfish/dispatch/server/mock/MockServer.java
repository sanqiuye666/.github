package cn.dreamingfish.dispatch.server.mock;

import cn.dreamingfish.dispatch.server.config.ServerConfig;
import cn.dreamingfish.dispatch.server.model.*;
import cn.dreamingfish.dispatch.server.model.ServerModels.*;
import cn.dreamingfish.dispatch.server.ports.ServerPorts;
import cn.dreamingfish.updater.protocol.JsonCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.*;

/** 无网络模拟服务。所有成功结果均标MOCK，绝不执行文件传输、签名、RCON或游戏进程。 */
public final class MockServer implements ServerPorts {
    private final ServerConfig config;private final MockStateStore store;private final CallAudit audit;private final JsonCodec json=new JsonCodec();
    private static final List<Group> GROUPS=List.of(new Group("default","默认玩家组"),new Group("testers","测试玩家组"));
    public MockServer(ServerConfig config){this.config=config;store=new MockStateStore(config.stateRoot());audit=new CallAudit(config.logRoot());}
    @Override public Status status(){return new Status(config.enabled(),config.enabled()?"MOCK":"DISABLED",config.role(),config.scenario(),config.enabled()?"离线演练：不验证真实密码，不上传文件，不启动Minecraft":"服务器能力未启用");}
    private volatile Actor cloudPlayer;
    private static Account publicAccount(Profile p){return new Account(p.id(),p.username(),p.displayName(),p.groupId(),"MOCK","MOCK");}
    @Override public PanelAccountPort panelAccounts(){return new PanelAccountPort(){
        public Account register(CallContext c,String op,String user,String display,String password){var p=sessions().register(c,op,user,display);cloudPlayer=Actor.player(p.id());return publicAccount(p);}
        public Account login(CallContext c,String user,String password){var p=sessions().login(c,user);cloudPlayer=Actor.player(p.id());return publicAccount(p);}
        public Account current(CallContext c){if(cloudPlayer==null)throw RemoteFailure.of("AUTH_FAILED");return publicAccount(profiles().readMe(c,cloudPlayer));}
        public Operation logout(CallContext c){var actor=cloudPlayer;cloudPlayer=null;if(actor==null)throw RemoteFailure.of("AUTH_FAILED");return sessions().logout(c,actor);}
        public AccountDirectory list(CallContext c){var result=directory().listPlayers(c,Actor.admin()).stream().map(MockServer::publicAccount).toList();return new AccountDirectory(result,result.size(),512,Instant.now().toString());}
    };}
    private <T>T call(CallContext call,String action,String feature,boolean write,Function<MockStateStore.State,T> work){
        if(!config.enabled()||!feature.isBlank()&&!config.flag(feature))throw RemoteFailure.of("FEATURE_DISABLED");
        long started=System.nanoTime();String code="OK";
        try{call.check();String failure=switch(config.scenario()){case "connect-failed"->"CONNECT_FAILED";case "auth-failed"->"AUTH_FAILED";case "server-error"->"SERVER_ERROR";case "invalid-data"->"INVALID_DATA";case "conflict"->"CONFLICT";default->null;};
            if(failure!=null)throw RemoteFailure.of(failure);return store.transaction(call,write,work);
        }catch(RemoteFailure e){code=e.code();throw e;}catch(RuntimeException e){code="INVALID_DATA";throw RemoteFailure.of(code);}finally{audit.record(call,action,started,code);}
    }
    private void admin(Actor actor){if(actor==null||!"admin".equals(actor.role())||!Set.of("admin","server").contains(config.role()))throw RemoteFailure.of("FORBIDDEN");}
    private Profile player(MockStateStore.State state,Actor actor){
        if(actor==null||!"player".equals(actor.role())||!Set.of("player","server").contains(config.role()))throw RemoteFailure.of("FORBIDDEN");
        var profile=state.players().get(actor.playerId());if(profile==null)throw RemoteFailure.of("AUTH_FAILED");return profile;
    }
    private static String text(String value,int maximum){if(value==null||value.isBlank()||value.length()>maximum||value.chars().anyMatch(c->c<32))throw RemoteFailure.of("INVALID_DATA");return value.trim();}
    private static Pack pack(MockStateStore.State state,String id){var result=state.packs().get(id);if(result==null)throw RemoteFailure.of("NOT_FOUND");return result;}
    private static void revision(long actual,long expected){if(actual!=expected)throw RemoteFailure.of("CONFLICT");}
    private Pack allowed(MockStateStore.State state,Actor actor,String id){
        var profile=player(state,actor);var pack=pack(state,id);
        if(!"PUBLISHED".equals(pack.status())||pack.assignments().stream().noneMatch(a->"player".equals(a.type())&&profile.id().equals(a.targetId())||"group".equals(a.type())&&profile.groupId().equals(a.targetId())))throw RemoteFailure.of("FORBIDDEN");return pack;
    }
    private <T>T write(MockStateStore.State state,String action,Actor actor,String operation,Object input,Class<T> type,Supplier<T> mutation){
        try{UUID.fromString(operation);}catch(Exception e){throw RemoteFailure.of("INVALID_DATA");}
        String fingerprint;
        try{fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.write(List.of(action,actor==null?"session":actor,input))));}catch(Exception e){throw RemoteFailure.of("INVALID_DATA");}
        var previous=state.operations().get(operation);
        if(previous!=null){if(!fingerprint.equals(previous.fingerprint())||!type.getName().equals(previous.kind()))throw RemoteFailure.of("CONFLICT");return json.read(previous.data().getBytes(StandardCharsets.UTF_8),type);}
        Instant cutoff=Instant.now().minusSeconds(config.number("IDEMPOTENCY_TTL_SECONDS"));state.operations().entrySet().removeIf(e->Instant.parse(e.getValue().createdAt()).isBefore(cutoff));
        if(state.operations().size()>=2048)throw RemoteFailure.of("SERVER_ERROR");
        T result=mutation.get();state.operations().put(operation,new MockStateStore.Receipt(fingerprint,type.getName(),json.writeString(result),Instant.now().toString()));return result;
    }
    private static Pack changed(Pack pack,String status,String release,List<Assignment> assignments,String deletedAt){return new Pack(pack.id(),pack.name(),pack.fileName(),pack.sizeBytes(),pack.gameVersion(),pack.loader(),status,pack.revision()+1,release,assignments,deletedAt,"MOCK");}
    @Override public PanelSessionPort sessions(){return new PanelSessionPort(){
        public Profile register(CallContext c,String operation,String username,String displayName){return call(c,"session.register","ACCOUNTS_ENABLED",true,s->{
            if(!config.flag("REGISTRATION_ENABLED"))throw RemoteFailure.of("FEATURE_DISABLED");
            String user=text(username,32).toLowerCase(Locale.ROOT);String display=text(displayName,64);if(!user.matches("[a-z0-9_]{3,32}"))throw RemoteFailure.of("INVALID_DATA");
            return write(s,"register",null,operation,List.of(user,display),Profile.class,()->{
                if(s.players().values().stream().anyMatch(p->p.username().equals(user)))throw RemoteFailure.of("CONFLICT");
                if(s.players().size()>=512)throw RemoteFailure.of("SERVER_ERROR");
                String group=config.value("DEFAULT_GROUP_ID");if(GROUPS.stream().noneMatch(g->g.id().equals(group)))throw RemoteFailure.of("INVALID_DATA");
                var profile=new Profile(UUID.randomUUID().toString(),user,display,group,1,null);s.players().put(profile.id(),profile);return profile;
            });});}
        public Profile login(CallContext c,String username){return call(c,"session.login","ACCOUNTS_ENABLED",false,s->s.players().values().stream().filter(p->p.username().equals(text(username,32).toLowerCase(Locale.ROOT))).findFirst().orElseThrow(()->RemoteFailure.of("AUTH_FAILED")));}
        public Profile refresh(CallContext c,Actor a){return profiles().readMe(c,a);}
        public Operation logout(CallContext c,Actor a){return call(c,"session.logout","ACCOUNTS_ENABLED",false,s->{player(s,a);return operation("LOGGED_OUT");});}
    };}
    @Override public PlayerProfilePort profiles(){return new PlayerProfilePort(){
        public Profile readMe(CallContext c,Actor a){return call(c,"profile.read","ACCOUNTS_ENABLED",false,s->player(s,a));}
        public Profile updateMe(CallContext c,Actor a,String op,long expected,String display){return call(c,"profile.update","ACCOUNTS_ENABLED",true,s->{var profile=player(s,a);return write(s,"profile.update",a,op,List.of(expected,text(display,64)),Profile.class,()->{revision(profile.revision(),expected);var updated=new Profile(profile.id(),profile.username(),display,profile.groupId(),expected+1,profile.report());s.players().put(updated.id(),updated);return updated;});});}
        public Profile reportManagedInstance(CallContext c,Actor a,String op,String id,String status){return call(c,"profile.report","PLAYER_REPORTS_ENABLED",true,s->{var profile=player(s,a);var pack=allowed(s,a,id);if(!Set.of("UPDATED","FAILED","SIMULATED_LAUNCH").contains(status))throw RemoteFailure.of("INVALID_DATA");
            return write(s,"profile.report",a,op,List.of(id,status),Profile.class,()->{var updated=new Profile(profile.id(),profile.username(),profile.displayName(),profile.groupId(),profile.revision()+1,new Report(id,pack.releaseId(),status,Instant.now().toString(),"MOCK"));s.players().put(updated.id(),updated);return updated;});});}
    };}
    @Override public DispatchDirectoryPort directory(){return new DispatchDirectoryPort(){
        public List<Group> listGroups(CallContext c,Actor a){return call(c,"directory.groups","ACCOUNTS_ENABLED",false,s->{admin(a);return GROUPS;});}
        public List<Profile> listPlayers(CallContext c,Actor a){return call(c,"directory.players","ACCOUNTS_ENABLED",false,s->{admin(a);return List.copyOf(s.players().values());});}
        public Profile setGroup(CallContext c,Actor a,String op,String id,long expected,String group){return call(c,"directory.setGroup","ACCOUNTS_ENABLED",true,s->{admin(a);if(GROUPS.stream().noneMatch(g->g.id().equals(group)))throw RemoteFailure.of("INVALID_DATA");var profile=s.players().get(id);if(profile==null)throw RemoteFailure.of("NOT_FOUND");
            return write(s,"directory.setGroup",a,op,List.of(id,expected,group),Profile.class,()->{revision(profile.revision(),expected);var updated=new Profile(id,profile.username(),profile.displayName(),group,expected+1,profile.report());s.players().put(id,updated);return updated;});});}
    };}
    @Override public ManagedPackPort packs(){return new ManagedPackPort(){
        public List<Pack> list(CallContext c,Actor a){return call(c,"packs.list","MANAGED_PACKS_ENABLED",false,s->{player(s,a);return s.packs().values().stream().filter(p->{try{allowed(s,a,p.id());return true;}catch(RemoteFailure denied){return false;}}).toList();});}
        public Pack getRelease(CallContext c,Actor a,String id){return call(c,"packs.release","MANAGED_PACKS_ENABLED",false,s->allowed(s,a,id));}
    };}
    @Override public PackAdminPort packAdmin(){return new PackAdminPort(){
        public List<Pack> list(CallContext c,Actor a){return call(c,"packAdmin.list","MANAGED_PACKS_ENABLED",false,s->{admin(a);return List.copyOf(s.packs().values());});}
        public Pack beginUpload(CallContext c,Actor a,String op,Upload upload){return call(c,"packAdmin.preview","MANAGED_PACKS_ENABLED",true,s->{admin(a);
            text(upload.name(),100);text(upload.fileName(),128);text(upload.gameVersion(),64);text(upload.loader(),32);
            if(!upload.fileName().toLowerCase(Locale.ROOT).endsWith(".zip")||upload.fileName().contains("/")||upload.fileName().contains("\\")||upload.fileName().contains(":")||upload.sizeBytes()<1||upload.sizeBytes()>config.number("MAX_UPLOAD_BYTES"))throw RemoteFailure.of("INVALID_DATA");
            return write(s,"packAdmin.preview",a,op,upload,Pack.class,()->{if(s.packs().size()>=512)throw RemoteFailure.of("SERVER_ERROR");var pack=new Pack(UUID.randomUUID().toString(),upload.name(),upload.fileName(),upload.sizeBytes(),upload.gameVersion(),upload.loader(),"PREVIEW",1,null,List.of(),null,"MOCK");s.packs().put(pack.id(),pack);return pack;});});}
        public Pack getImport(CallContext c,Actor a,String id){return call(c,"packAdmin.import","MANAGED_PACKS_ENABLED",false,s->{admin(a);return pack(s,id);});}
        public Pack assign(CallContext c,Actor a,String op,String id,long expected,String type,String target){return call(c,"packAdmin.assign","MANAGED_PACKS_ENABLED",true,s->{admin(a);var pack=pack(s,id);
            if(!Set.of("group","player").contains(type)||"group".equals(type)&&GROUPS.stream().noneMatch(g->g.id().equals(target))||"player".equals(type)&&!s.players().containsKey(target))throw RemoteFailure.of("INVALID_DATA");
            return write(s,"packAdmin.assign",a,op,List.of(id,expected,type,target),Pack.class,()->{revision(pack.revision(),expected);if(!"PUBLISHED".equals(pack.status()))throw RemoteFailure.of("CONFLICT");var assignments=new ArrayList<>(pack.assignments());var assignment=new Assignment(type,target);if(!assignments.contains(assignment))assignments.add(assignment);var updated=changed(pack,pack.status(),pack.releaseId(),assignments,null);s.packs().put(id,updated);return updated;});});}
        public Pack unlist(CallContext c,Actor a,String op,String id,long expected){return stateChange(c,a,op,id,expected,"UNLISTED",null);}
        public Pack delete(CallContext c,Actor a,String op,String id,long expected,String confirmation){return stateChange(c,a,op,id,expected,"DELETED",confirmation);}
        public Pack restore(CallContext c,Actor a,String op,String id,long expected){return stateChange(c,a,op,id,expected,"RESTORE",null);}
    };}
    private Pack stateChange(CallContext c,Actor a,String op,String id,long expected,String target,String confirmation){return call(c,"packAdmin."+target,"MANAGED_PACKS_ENABLED",true,s->{admin(a);var pack=pack(s,id);
        if("DELETED".equals(target)&&!pack.name().equals(confirmation))throw RemoteFailure.of("CONFIRMATION_REQUIRED");
        return write(s,"packAdmin."+target,a,op,List.of(id,expected),Pack.class,()->{revision(pack.revision(),expected);
            if("RESTORE".equals(target)!= "DELETED".equals(pack.status()))throw RemoteFailure.of("CONFLICT");
            if("RESTORE".equals(target)&&Instant.parse(pack.deletedAt()).plusSeconds(config.number("PACK_DELETE_RETENTION_DAYS")*86400).isBefore(Instant.now()))throw RemoteFailure.of("CONFLICT");
            var updated=changed(pack,"RESTORE".equals(target)?pack.releaseId()==null?"PREVIEW":"UNLISTED":target,pack.releaseId(),"DELETED".equals(target)?List.of():pack.assignments(),"DELETED".equals(target)?Instant.now().toString():null);s.packs().put(id,updated);return updated;});});}
    @Override public PackPublishPort publish(){return new PackPublishPort(){
        public Pack prepare(CallContext c,Actor a,String id){return packAdmin().getImport(c,a,id);}
        public Pack status(CallContext c,Actor a,String id){return packAdmin().getImport(c,a,id);}
        public Pack activate(CallContext c,Actor a,String op,String id,long expected){return call(c,"publish.activate","MANAGED_PACKS_ENABLED",true,s->{admin(a);var pack=pack(s,id);return write(s,"publish.activate",a,op,List.of(id,expected),Pack.class,()->{revision(pack.revision(),expected);if("DELETED".equals(pack.status()))throw RemoteFailure.of("CONFLICT");var updated=changed(pack,"PUBLISHED",pack.releaseId()==null?UUID.randomUUID().toString():pack.releaseId(),pack.assignments(),null);s.packs().put(id,updated);return updated;});});}
    };}
    @Override public PackContentPort content(){return new PackContentPort(){
        public Manifest readManifest(CallContext c,Actor a,String id){return call(c,"content.manifest","MANAGED_PACKS_ENABLED",false,s->{var pack=allowed(s,a,id);return new Manifest(id,pack.releaseId(),"MOCK",true,"模拟签名目标；未提供真实签名或内容文件");});}
        public Operation downloadObject(CallContext c,Actor a,String id,String object){return call(c,"content.download","MANAGED_PACKS_ENABLED",false,s->{allowed(s,a,id);UUID.fromString(object);return operation("SIMULATED_DOWNLOAD");});}
    };}
    private Operation operation(String status){return new Operation(UUID.randomUUID().toString(),status,"MOCK","仅模拟结果，没有执行真实远端动作");}
    private Operation administrative(CallContext c,Actor a,String action,String op,Object input){return call(c,action,action.startsWith("minecraft.")?"RCON_ENABLED":action.startsWith("runtime.")?"SSH_ENABLED":"",op!=null,s->{admin(a);return op==null?operation(action):write(s,action,a,op,input,Operation.class,()->operation(action));});}
    @Override public ServerRuntimePort runtime(){return new ServerRuntimePort(){
        public Operation inspect(CallContext c,Actor a){return administrative(c,a,"runtime.inspect",null,"");}
        public Operation ensureService(CallContext c,Actor a,String op,boolean enabled){return administrative(c,a,"runtime.ensure",op,enabled);}
    };}
    @Override public ServerFilePort files(){return new ServerFilePort(){
        public Operation stat(CallContext c,Actor a,String object){UUID.fromString(object);return administrative(c,a,"file.stat",null,object);}
        public Operation upload(CallContext c,Actor a,String op,String object,long bytes){UUID.fromString(object);if(bytes<1||bytes>config.number("MAX_UPLOAD_BYTES"))throw RemoteFailure.of("INVALID_DATA");return administrative(c,a,"file.upload",op,List.of(object,bytes));}
        public Operation download(CallContext c,Actor a,String object){UUID.fromString(object);return administrative(c,a,"file.download",null,object);}
    };}
    @Override public MinecraftControlPort minecraft(){return new MinecraftControlPort(){
        public Operation status(CallContext c,Actor a){return administrative(c,a,"minecraft.status",null,"");}
        public Operation listPlayers(CallContext c,Actor a){return administrative(c,a,"minecraft.players",null,"");}
        public Operation saveWorld(CallContext c,Actor a,String op){return administrative(c,a,"minecraft.saveWorld",op,"");}
    };}
    @Override public PanelAccountRepository accounts(){return (c,a,id)->call(c,"accounts.find","ACCOUNTS_ENABLED",false,s->{if(!"server".equals(config.role()))throw RemoteFailure.of("FORBIDDEN");admin(a);var result=s.players().get(id);if(result==null)throw RemoteFailure.of("NOT_FOUND");return result;});}
    @Override public ServerReleasePort release(){return new ServerReleasePort(){
        public Operation backup(CallContext c,Actor a,String op){return administrative(c,a,"release.backup",op,"");}
        public Operation activate(CallContext c,Actor a,String op,String id){UUID.fromString(id);return administrative(c,a,"release.activate",op,id);}
        public Operation rollback(CallContext c,Actor a,String op,String id){UUID.fromString(id);return administrative(c,a,"release.rollback",op,id);}
        public Operation status(CallContext c,Actor a){return administrative(c,a,"release.status",null,"");}
    };}
}
