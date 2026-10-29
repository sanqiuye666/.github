package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.GameInstance.LoaderKind;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.vanilla.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public final class QuiltProvider implements LoaderProvider {
 private final VanillaPlanner vanilla;private final DownloadStore store;private final RemoteClient remote;
 public QuiltProvider(VanillaPlanner vanilla,DownloadStore store,RemoteClient remote){this.vanilla=vanilla;this.store=store;this.remote=remote;}
 @Override public LoaderKind kind(){return LoaderKind.QUILT;}
 private static String segment(String value){LoaderIdentity.validateVersion(value);return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
 private static URI uri(String game,String loader){return URI.create("https://meta.quiltmc.org/v3/versions/loader/"+segment(game)+(loader==null?"":"/"+segment(loader)+"/profile/json"));}
 @Override public List<LoaderVersions.Version> versions(String game,CancellationToken token)throws IOException{
  byte[] bytes=remote.bytes(new RemoteRequest(RemoteService.QUILT_META,uri(game,null),Map.of()),2*1024*1024,token);try{var n=LauncherJson.readValue(bytes,2*1024*1024);if(!n.isArray()||n.size()>2000)throw new IOException();var values=new HashSet<String>();for(var entry:n){token.check();String v=LauncherJson.text(entry.path("loader"),"version",128);LoaderIdentity.validateVersion(v);if(!game.equals(entry.path("hashed").path("version").asText())||!values.add(v))throw new IOException();}return values.stream().sorted(LoaderVersions::newestFirst).map(v->new LoaderVersions.Version(v,!v.contains("-"))).toList();}
  catch(InterruptedIOException cancelled){throw cancelled;}catch(IOException|IllegalArgumentException invalid){throw new TaskFailure("SOURCE_INVALID_RESPONSE","Quilt官方兼容目录格式无效");}
 }
 private static DownloadSpec source(String game,String version,String hash){return new DownloadSpec(RemoteService.QUILT_META,uri(game,version),-1,2*1024*1024,new FileDigest(FileDigest.Algorithm.SHA256,hash),"Quilt客户端描述");}
 @Override public LoaderPlan plan(String game,String loader,CancellationToken token)throws IOException{
  if(versions(game,token).stream().noneMatch(v->v.version().equals(loader)))throw new TaskFailure("UNSUPPORTED_LOADER","官方未列出此游戏与Quilt版本组合");byte[] bytes=remote.bytes(new RemoteRequest(RemoteService.QUILT_META,uri(game,loader),Map.of()),2*1024*1024,token);QuiltProfile.parse(bytes,game,loader);String hash=LoaderPlan.hash(bytes);var spec=source(game,loader,hash);store.rememberMetadata(spec,bytes,token);return build(vanilla.plan(game,token),loader,spec,bytes,token);
 }
 @Override public LoaderPlan pinned(String game,String metadataSha1,String loader,String sourceSha256,String fingerprint,CancellationToken token)throws IOException{
  var spec=source(game,loader,sourceSha256);var object=store.fetch(spec,token,n->{});return build(vanilla.planPinned(game,metadataSha1,token),loader,spec,store.read(object,2*1024*1024,token),token).requireFingerprint(fingerprint);
 }
 private LoaderPlan build(VanillaPlan base,String loader,DownloadSpec source,byte[] bytes,CancellationToken token)throws IOException{
  var identity=new LoaderIdentity(kind(),loader);var profile=QuiltProfile.parse(bytes,base.versionId(),loader);var files=new LinkedHashMap<Path,InstallFile>();for(var file:base.files())files.put(file.relativePath(),file);
  files.put(Path.of(identity.profilePath()),new InstallFile(Path.of(identity.profilePath()),new DownloadSpec(source.service(),source.uri(),bytes.length,source.maxBytes(),source.digest(),source.displayName())));var resolver=new LoaderFiles(store,remote);for(var library:profile.libraries()){token.check();LoaderFiles.add(files,resolver.resolve(library,token));}
  return LoaderPlan.fixed(new VanillaPlan(base.versionId(),base.metadataSha1(),base.platform(),base.requiredJavaMajor(),new ArrayList<>(files.values()),0,null,identity),source.digest().hex());
 }
}
