package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.launcher.catalog.*;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.instances.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.vanilla.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
final class LauncherInstallFixture implements AutoCloseable {
 final Path root;final Clock clock=Clock.systemUTC();final FileInstanceCatalog instances;final DownloadStore store;final DownloadBatch batch;final VanillaPlanner planner;final VanillaInstaller installer;final String id;
 LauncherInstallFixture(Path root)throws Exception{
  this.root=root;byte[] binary="client".getBytes(),index=LauncherJson.write(Map.of("objects",Map.of()));
  byte[] metadata=LauncherJson.write(Map.of("id","fixture","libraries",List.of(),"downloads",Map.of("client",Map.of("url","https://piston-data.mojang.com/client.jar","sha1",sha(binary),"size",binary.length)),"assetIndex",Map.of("id","fixture","url","https://piston-meta.mojang.com/index.json","sha1",sha(index),"size",index.length)));
  byte[] manifest=LauncherJson.write(Map.of("latest",Map.of("release","fixture","snapshot","fixture"),"versions",List.of(Map.of("id","fixture","type","release","sha1",sha(metadata),"url","https://piston-meta.mojang.com/game.json","releaseTime","2024-01-01T00:00:00Z","time","2024-01-01T00:00:00Z"))));
  var catalog=new MojangVersionCatalog(root,clock,()->manifest);instances=new FileInstanceCatalog(root,clock);id=instances.create("中文测试","fixture",catalog.load(false).manifest()).instance().id();
  store=new DownloadStore(root,(r,l,t,s)->{t.check();s.accept(ByteBuffer.wrap(r.uri().getPath().equals("/game.json")?metadata:r.uri().getPath().equals("/index.json")?index:binary));});batch=new DownloadBatch(store,4);planner=new VanillaPlanner(catalog,store,Platform.current());installer=new VanillaInstaller(root,instances,store,batch,clock);
 }
 static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));}
 public void close(){batch.close();}
}
