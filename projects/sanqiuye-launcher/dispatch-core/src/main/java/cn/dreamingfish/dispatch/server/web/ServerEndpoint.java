package cn.dreamingfish.dispatch.server.web;

import cn.dreamingfish.dispatch.server.config.ServerConfig;
import cn.dreamingfish.dispatch.server.model.*;
import cn.dreamingfish.dispatch.server.model.ServerModels.*;
import cn.dreamingfish.dispatch.server.ports.ServerPorts;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.*;

/** 受保护HTTP边界共用的小控制器。角色由启动入口注入，玩家身份只来自本次演练会话。 */
public final class ServerEndpoint {
    private final ServerConfig config;private final ServerPorts ports;private final String role;
    private volatile Actor currentPlayer;
    public ServerEndpoint(ServerConfig config,ServerPorts ports,String role){
        this.config=config;this.ports=ports;this.role=role;
        if(config.enabled()&&!role.equals(config.role()))throw new RemoteFailure("CONFIG_INVALID","配置无效：SQY_ROLE");
    }
    public RemoteResult<Object> handle(String method,String path,JsonNode body){
        var call=CallContext.create(Duration.ofMillis(config.number("TIMEOUT_DIRECTORY_MS")));long start=System.nanoTime();
        try{return result(call,execute(call,method,path,body),null,start);}catch(RemoteFailure e){return result(call,null,new ServerModels.Error(e.code(),e.getMessage()),start);}catch(RuntimeException e){return result(call,null,new ServerModels.Error("INVALID_DATA","数据格式不符或无法验证"),start);}
    }
    private RemoteResult<Object> result(CallContext call,Object data,ServerModels.Error error,long start){return new RemoteResult<>(1,call.requestId(),data,error,Math.max(0,(System.nanoTime()-start)/1000000),1,ports.status().source());}
    private Object execute(CallContext c,String method,String path,JsonNode b){
        if(method.equals("GET")&&path.equals("/status"))return ports.status();
        if(method.equals("GET")&&path.equals("/account")){if(!role.equals("player"))throw RemoteFailure.of("FORBIDDEN");return ports.panelAccounts().current(c);}
        if(method.equals("GET")&&path.equals("/accounts")){if(!role.equals("admin"))throw RemoteFailure.of("FORBIDDEN");return ports.panelAccounts().list(c);}
        if(method.equals("POST")&&path.startsWith("/account/")){
            if(!role.equals("player"))throw RemoteFailure.of("FORBIDDEN");
            return switch(path){
                case "/account/register"->{fields(b,"operationId","username","displayName","password");yield ports.panelAccounts().register(c,text(b,"operationId",36),text(b,"username",32),text(b,"displayName",64),text(b,"password",256));}
                case "/account/login"->{fields(b,"username","password");yield ports.panelAccounts().login(c,text(b,"username",32),text(b,"password",256));}
                case "/account/logout"->{fields(b);yield ports.panelAccounts().logout(c);}
                default->throw RemoteFailure.of("FORBIDDEN");};
        }
        if(method.equals("GET")&&path.equals("/view")){
            if(role.equals("admin")){var a=Actor.admin();return Map.of("status",ports.status(),"players",ports.directory().listPlayers(c,a),"groups",ports.directory().listGroups(c,a),"packs",ports.packAdmin().list(c,a));}
            var a=actor();return Map.of("status",ports.status(),"profile",ports.profiles().readMe(c,a),"packs",ports.packs().list(c,a));
        }
        if(!method.equals("POST"))throw RemoteFailure.of("INVALID_DATA");
        if(role.equals("player"))return switch(path){
            case "/register"->{fields(b,"operationId","username","displayName");var p=ports.sessions().register(c,text(b,"operationId",36),text(b,"username",32),text(b,"displayName",64));currentPlayer=Actor.player(p.id());yield p;}
            case "/login"->{fields(b,"username");var p=ports.sessions().login(c,text(b,"username",32));currentPlayer=Actor.player(p.id());yield p;}
            case "/logout"->{fields(b);var a=actor();currentPlayer=null;yield ports.sessions().logout(c,a);}
            case "/report"->{fields(b,"operationId","packId","status");yield ports.profiles().reportManagedInstance(c,actor(),text(b,"operationId",36),text(b,"packId",36),text(b,"status",32));}
            case "/manifest"->{fields(b,"packId");yield ports.content().readManifest(c,actor(),text(b,"packId",36));}
            case "/profile"->{fields(b,"operationId","revision","displayName");yield ports.profiles().updateMe(c,actor(),text(b,"operationId",36),revision(b),text(b,"displayName",64));}
            default->throw RemoteFailure.of("FORBIDDEN");
        };
        var a=Actor.admin();return switch(path){
            case "/upload"->{fields(b,"operationId","name","fileName","sizeBytes","gameVersion","loader");if(!b.path("sizeBytes").isIntegralNumber()||!b.path("sizeBytes").canConvertToLong())throw RemoteFailure.of("INVALID_DATA");yield ports.packAdmin().beginUpload(c,a,text(b,"operationId",36),new Upload(text(b,"name",100),text(b,"fileName",128),b.path("sizeBytes").longValue(),text(b,"gameVersion",64),text(b,"loader",32)));}
            case "/publish"->{fields(b,"operationId","packId","revision");yield ports.publish().activate(c,a,text(b,"operationId",36),text(b,"packId",36),revision(b));}
            case "/assign"->{fields(b,"operationId","packId","revision","targetType","targetId");yield ports.packAdmin().assign(c,a,text(b,"operationId",36),text(b,"packId",36),revision(b),text(b,"targetType",16),text(b,"targetId",64));}
            case "/unlist"->{fields(b,"operationId","packId","revision");yield ports.packAdmin().unlist(c,a,text(b,"operationId",36),text(b,"packId",36),revision(b));}
            case "/delete"->{fields(b,"operationId","packId","revision","confirmation");yield ports.packAdmin().delete(c,a,text(b,"operationId",36),text(b,"packId",36),revision(b),text(b,"confirmation",100));}
            case "/restore"->{fields(b,"operationId","packId","revision");yield ports.packAdmin().restore(c,a,text(b,"operationId",36),text(b,"packId",36),revision(b));}
            case "/set-group"->{fields(b,"operationId","playerId","revision","groupId");yield ports.directory().setGroup(c,a,text(b,"operationId",36),text(b,"playerId",36),revision(b),text(b,"groupId",64));}
            case "/inspect"->{fields(b);yield ports.runtime().inspect(c,a);}
            case "/backup"->{fields(b,"operationId");yield ports.release().backup(c,a,text(b,"operationId",36));}
            default->throw RemoteFailure.of("FORBIDDEN");
        };
    }
    private Actor actor(){var a=currentPlayer;if(a==null)throw RemoteFailure.of("AUTH_FAILED");return a;}
    private static void fields(JsonNode body,String... names){
        if(body==null||!body.isObject())throw RemoteFailure.of("INVALID_DATA");var allowed=Set.of(names);body.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw RemoteFailure.of("INVALID_DATA");});
    }
    private static String text(JsonNode b,String key,int max){var node=b.path(key);if(!node.isTextual()||node.textValue().isBlank()||node.textValue().length()>max||node.textValue().chars().anyMatch(c->c<32))throw RemoteFailure.of("INVALID_DATA");return node.textValue();}
    private static long revision(JsonNode b){var value=b.path("revision");if(!value.isIntegralNumber()||!value.canConvertToLong()||value.longValue()<1)throw RemoteFailure.of("INVALID_DATA");return value.longValue();}
}
