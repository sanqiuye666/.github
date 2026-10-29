package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.servers.*;import cn.dreamingfish.launcher.storage.LauncherBusyException;
import java.io.*;import java.util.*;
public final class PlayerServerRoutes implements LauncherRoutes,AutoCloseable {
 private final ServerStore store;private final ServerProbe probe;
 public PlayerServerRoutes(ServerStore store,ServerProbe probe){this.store=store;this.probe=probe;}
 @Override public Optional<ApiResponse> handle(ApiRequest r)throws IOException{
  if(!Set.of("/player-servers","/player-servers/save","/player-servers/remove","/player-servers/probe").contains(r.path()))return Optional.empty();r.onlyQuery();
  try{if(r.path().equals("/player-servers")){r.requireMethod("GET");return Optional.of(new ApiResponse(200,store.read()));}r.requireMethod("POST");
   if(r.path().equals("/player-servers/probe")){r.onlyFields(Set.of("id"));return Optional.of(new ApiResponse(200,probe.probe(store.require(r.text("id",36)).address())));}
   if(r.path().equals("/player-servers/remove")){r.onlyFields(Set.of("revision","id"));return Optional.of(new ApiResponse(200,store.remove(r.text("revision",36),r.text("id",36))));}
   r.onlyFields(Set.of("revision","id","name","host","port"));if(!r.body().path("port").isInt())throw ApiRequest.invalid("端口必须为整数");return Optional.of(new ApiResponse(200,store.save(r.text("revision",36),r.body().path("id").isNull()?null:r.text("id",36),r.text("name",64),new ServerAddress(r.text("host",253),r.body().path("port").intValue()))));
  }catch(LauncherBusyException busy){throw new LauncherApiException(409,"SERVER_BUSY","收藏正在保存，请稍后重试");}
 }
 @Override public void close(){probe.close();}
}
