package cn.dreamingfish.launcher.loaders.fabric;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.vanilla.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public final class FabricPlanner {
 public record LoaderVersion(String version,boolean stable){}
 public record Plan(VanillaPlan installation,String profileSha256,String fingerprint){}
 private final VanillaPlanner vanilla;private final DownloadStore store;private final RemoteClient remote;
 public FabricPlanner(VanillaPlanner vanilla,DownloadStore store,RemoteClient remote){this.vanilla=vanilla;this.store=store;this.remote=remote;}
 private static String segment(String text){FabricProfile.version(text);return URLEncoder.encode(text,StandardCharsets.UTF_8).replace("+","%20");}
 private static URI uri(String game,String loader){return URI.create("https://meta.fabricmc.net/v2/versions/loader/"+segment(game)+(loader==null?"":"/"+segment(loader)+"/profile/json"));}
 public List<LoaderVersion> versions(String game,CancellationToken token)throws IOException{
  var bytes=remote.bytes(new RemoteRequest(RemoteService.FABRIC_META,uri(game,null),Map.of()),2*1024*1024,token);try{var root=LauncherJson.readValue(bytes,2*1024*1024);if(!root.isArray()||root.size()>1000)throw new IOException("Fabric版本目录格式或数量无效");
  var versions=new ArrayList<LoaderVersion>();var ids=new HashSet<String>();for(var value:root){token.check();String version=LauncherJson.text(value.path("loader"),"version",128);FabricProfile.version(version);if(!value.path("loader").path("stable").isBoolean()||!game.equals(value.path("intermediary").path("version").asText())||!ids.add(version))throw new IOException("Fabric兼容版本目录无效");versions.add(new LoaderVersion(version,value.path("loader").path("stable").booleanValue()));}return List.copyOf(versions);
  }catch(java.io.InterruptedIOException cancelled){throw cancelled;}catch(IllegalArgumentException|IOException invalid){throw new TaskFailure("SOURCE_INVALID_RESPONSE","Fabric官方版本目录格式无效");}
 }
 public Plan plan(String game,String loader,CancellationToken token)throws IOException{
  if(versions(game,token).stream().noneMatch(v->v.version().equals(loader)))throw new TaskFailure("UNSUPPORTED_LOADER","此游戏没有所选Fabric Loader版本");
  byte[] bytes=remote.bytes(new RemoteRequest(RemoteService.FABRIC_META,uri(game,loader),Map.of()),2*1024*1024,token);FabricProfile.parse(bytes,game,loader);
  String hash=hash(bytes);var spec=profileSpec(game,loader,hash);store.rememberMetadata(spec,bytes,token);return build(vanilla.plan(game,token),loader,spec,bytes,token);
 }
 public Plan planPinned(String game,String metadataSha1,String loader,String profileSha256,String expected,CancellationToken token)throws IOException{
  var spec=profileSpec(game,loader,profileSha256);var object=store.fetch(spec,token,n->{});var plan=build(vanilla.planPinned(game,metadataSha1,token),loader,spec,store.read(object,2*1024*1024,token),token);if(!plan.fingerprint().equals(expected))throw new TaskFailure("PLAN_CHANGED","Fabric文件计划已变化，请重新查看安装计划");return plan;
 }
 private static DownloadSpec profileSpec(String game,String loader,String hash){return new DownloadSpec(RemoteService.FABRIC_META,uri(game,loader),-1,2*1024*1024,new FileDigest(FileDigest.Algorithm.SHA256,hash),"Fabric客户端元数据");}
 private Plan build(VanillaPlan base,String loader,DownloadSpec profileSpec,byte[] bytes,CancellationToken token)throws IOException{
  var profile=FabricProfile.parse(bytes,base.versionId(),loader);var files=new LinkedHashMap<Path,InstallFile>();for(var file:base.files())files.put(file.relativePath(),file);
  files.put(Path.of(FabricProfile.PATH),new InstallFile(Path.of(FabricProfile.PATH),new DownloadSpec(profileSpec.service(),profileSpec.uri(),bytes.length,profileSpec.maxBytes(),profileSpec.digest(),profileSpec.displayName())));
  for(var library:profile.libraries()){
   token.check();var digest=library.digest();if(digest==null){byte[] checksum=remote.bytes(new RemoteRequest(RemoteService.FABRIC_LIBRARIES,URI.create(library.uri()+".sha1"),Map.of()),256,token);try{digest=new FileDigest(FileDigest.Algorithm.SHA1,new String(checksum,StandardCharsets.US_ASCII).strip());}catch(IllegalArgumentException error){throw new IOException("Fabric官方Maven摘要无效",error);}}
   long size=library.size()==null?-1:library.size();var spec=new DownloadSpec(RemoteService.FABRIC_LIBRARIES,library.uri(),size,64L*1024*1024,digest,"Fabric依赖");if(size<0){var object=store.fetch(spec,token,n->{});spec=new DownloadSpec(spec.service(),spec.uri(),object.size(),spec.maxBytes(),spec.digest(),spec.displayName());}
   Path path=Path.of("libraries/"+library.path());var old=files.putIfAbsent(path,new InstallFile(path,spec));if(old!=null&&!old.download().equals(spec))throw new TaskFailure("LIBRARY_CONFLICT","Fabric与原版依赖文件冲突，未改动实例");
  }
  var installation=new VanillaPlan(base.versionId(),base.metadataSha1(),base.platform(),base.requiredJavaMajor(),new ArrayList<>(files.values()),0,loader);
  var fingerprint=new StringBuilder(base.versionId()).append('\n').append(loader).append('\n');installation.files().stream().sorted(Comparator.comparing(f->f.relativePath().toString())).forEach(f->{var d=f.download();fingerprint.append(f.relativePath().toString().replace('\\','/')).append('\0').append(d.service()).append('\0').append(d.uri()).append('\0').append(d.expectedSize()).append('\0').append(d.digest()).append('\n');});
  return new Plan(installation,profileSpec.digest().hex(),hash(fingerprint.toString().getBytes(StandardCharsets.UTF_8)));
 }
 private static String hash(byte[] bytes){return HexFormat.of().formatHex(new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest().digest(bytes));}
}
