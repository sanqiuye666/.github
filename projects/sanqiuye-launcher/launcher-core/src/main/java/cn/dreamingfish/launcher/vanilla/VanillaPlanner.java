package cn.dreamingfish.launcher.vanilla;
import cn.dreamingfish.launcher.catalog.*;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.URI;
import java.util.*;
public final class VanillaPlanner {
 private final MojangVersionCatalog catalog;private final DownloadStore store;private final Platform platform;
 public VanillaPlanner(MojangVersionCatalog catalog,DownloadStore store,Platform platform){this.catalog=catalog;this.store=store;this.platform=platform;}
 public VanillaPlan planPinned(String versionId,String metadataSha1,CancellationToken token)throws IOException{
  var plan=plan(versionId,token);if(!plan.metadataSha1().equals(metadataSha1))throw new TaskFailure("PLAN_CHANGED","官方版本元数据已变更，请重新查看安装计划");return plan;
 }
 public VanillaPlan plan(String versionId,CancellationToken token)throws IOException{
  token.check();var entry=catalog.load(false).manifest().versions().stream().filter(v->v.id().equals(versionId)).findFirst().orElseThrow(()->new IllegalArgumentException("官方目录中没有此版本"));token.check();
  var metadataSpec=new DownloadSpec(RemoteService.MOJANG_META,entry.metadataUrl(),-1,4*1024*1024,new FileDigest(FileDigest.Algorithm.SHA1,entry.sha1()),"版本元数据");
  var object=store.fetch(metadataSpec,token,n->{});var n=LauncherJson.read(store.read(object,4*1024*1024,token),4*1024*1024);
  try{if(!entry.id().equals(LauncherJson.text(n,"id",128)))throw new IllegalArgumentException("版本元数据 ID 不匹配");var files=new LinkedHashMap<String,InstallFile>();
   add(files,"versions/game.json",knownSize(metadataSpec,object.size()));add(files,"versions/client.jar",download(n.path("downloads").path("client"),RemoteService.MOJANG_FILES,"客户端",2L*1024*1024*1024));
   if(!n.path("libraries").isArray())throw new IllegalArgumentException("缺少官方依赖列表");
   for(var lib:n.path("libraries")){token.check();if(!LibraryRules.allowed(lib.get("rules"),platform,Map.of()))continue;var downloads=lib.path("downloads");boolean artifact=downloads.has("artifact");if(artifact)addLibrary(files,downloads.path("artifact"));
    if(lib.has("natives")){var nativeName=lib.path("natives").get(platform.osName());if(nativeName!=null){if(!nativeName.isTextual())throw new IllegalArgumentException("原生库分类无效");String classifier=nativeName.textValue().replace("${arch}",Integer.toString(platform.bits()));if(classifier.contains("${"))throw new IllegalArgumentException("未知原生库占位符");var file=downloads.path("classifiers").get(classifier);if(file==null)throw new IllegalArgumentException("官方元数据缺少适用原生库");addLibrary(files,file);}}
    else if(!artifact)throw new IllegalArgumentException("依赖缺少可校验的官方文件信息");
   }
   var ai=n.path("assetIndex");String indexId=LauncherJson.text(ai,"id",128);SafeInstallPath.parse(indexId);if(indexId.contains("/"))throw new IllegalArgumentException("资源索引 ID 无效");var indexSpec=download(ai,RemoteService.MOJANG_META,"资源索引",32L*1024*1024);var indexObject=store.fetch(indexSpec,token,b->{});add(files,"assets/indexes/game.json",indexSpec);var assets=AssetIndex.parse(store.read(indexObject,32*1024*1024,token));
   for(var asset:assets.objects().entrySet()){token.check();var value=asset.getValue();String objectPath=value.hash().substring(0,2)+"/"+value.hash();var spec=new DownloadSpec(RemoteService.MOJANG_ASSETS,URI.create("https://resources.download.minecraft.net/"+objectPath),value.size(),2L*1024*1024*1024,new FileDigest(FileDigest.Algorithm.SHA1,value.hash()),"游戏资源");add(files,"assets/objects/"+objectPath,spec);if(assets.virtual())add(files,"assets/virtual/"+indexId+"/"+asset.getKey(),spec);if(assets.mapToResources())add(files,"resources/"+asset.getKey(),spec);}
   if(n.has("logging")){var file=n.path("logging").path("client").path("file");if(!file.isMissingNode())add(files,"logging/client.xml",download(file,RemoteService.MOJANG_FILES,"日志配置",4*1024*1024));}
   Integer java=null;if(n.has("javaVersion")){var major=n.path("javaVersion").path("majorVersion");if(!major.isInt())throw new IllegalArgumentException("Java 要求无效");java=major.intValue();}
   return new VanillaPlan(entry.id(),entry.sha1(),platform,java,new ArrayList<>(files.values()),0);
  }catch(IllegalArgumentException error){throw new IOException("官方原版计划无法生成："+error.getMessage(),error);}
 }
 private static DownloadSpec knownSize(DownloadSpec spec,long size){return new DownloadSpec(spec.service(),spec.uri(),size,spec.maxBytes(),spec.digest(),spec.displayName());}
 private static DownloadSpec download(JsonNode n,RemoteService service,String name,long max){String hash=LauncherJson.text(n,"sha1",40);if(!n.path("size").isIntegralNumber()||!n.path("size").canConvertToLong())throw new IllegalArgumentException("官方文件缺少长度");var uri=URI.create(LauncherJson.text(n,"url",4096));return new DownloadSpec(service,uri,n.path("size").longValue(),max,new FileDigest(FileDigest.Algorithm.SHA1,hash),name);}
 private static void addLibrary(Map<String,InstallFile> files,JsonNode n){String path=LauncherJson.text(n,"path",1024);add(files,"libraries/"+path,download(n,RemoteService.MOJANG_LIBRARIES,"游戏依赖",2L*1024*1024*1024));}
 private static void add(Map<String,InstallFile> files,String name,DownloadSpec spec){var path=SafeInstallPath.parse(name);var previous=files.putIfAbsent(name,new InstallFile(path,spec));if(previous!=null&&!previous.download().equals(spec))throw new IllegalArgumentException("目标文件信息冲突");if(files.size()>100000)throw new IllegalArgumentException("安装目标超过 100000");}
}
