package cn.dreamingfish.launcher.mods;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.vanilla.SafeInstallPath;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.util.*;

public record ManagedModReceipt(String instanceId,String gameVersion,String loader,List<Entry> entries) {
 static final String NAME=".sqy-managed.json";
 public record Entry(ModSource source,String projectId,String versionId,String fileKey,String displayName,String fileName,long size,String sha256,List<ModDependency> dependencies){
  public Entry{new ModFileRef(source,projectId,versionId,fileKey);if(SafeInstallPath.parse(fileName).getNameCount()!=1||!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")||size<0||size>2L*1024*1024*1024||!sha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("受管模组字段无效");dependencies=List.copyOf(dependencies);if(dependencies.size()>10000)throw new IllegalArgumentException("依赖数量超限");for(var dep:dependencies)if((dep.kind()==ModDependency.Kind.REQUIRED||dep.kind()==ModDependency.Kind.INCOMPATIBLE)&&dep.projectId()==null&&dep.versionId()==null)throw new IllegalArgumentException("依赖缺少项目或版本");}
  String key(){return source+":"+projectId;}
 }
 public ManagedModReceipt{TaskRequest.uuid(instanceId);if(gameVersion==null||gameVersion.isBlank()||gameVersion.length()>128||!Set.of("fabric","quilt","forge","neoforge").contains(loader))throw new IllegalArgumentException("受管模组绑定无效");entries=List.copyOf(entries);if(entries.size()>1024)throw new IllegalArgumentException("受管模组超过1024项");SafeInstallPath.checkDistinct(entries.stream().map(e->SafeInstallPath.parse(e.fileName())).toList());var ids=new HashSet<String>();for(var e:entries)if(!ids.add(e.key()))throw new IllegalArgumentException("受管项目重复");}
 byte[] bytes()throws IOException{byte[] bytes=LauncherJson.write(Map.of("schemaVersion",1,"instanceId",instanceId,"gameVersion",gameVersion,"loader",loader,"entries",entries));if(bytes.length>2*1024*1024)throw new TaskFailure("RECORD_TOO_LARGE","受管模组收据超过2MiB");return bytes;}
 static void keys(JsonNode n,Set<String> expected)throws IOException{var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!n.isObject()||!actual.equals(expected))throw new IOException("受管记录字段无效");}
 static ManagedModReceipt parse(byte[] bytes)throws IOException{
  try{var n=LauncherJson.read(bytes,2*1024*1024);keys(n,Set.of("schemaVersion","instanceId","gameVersion","loader","entries"));if(!n.path("schemaVersion").isInt()||n.path("schemaVersion").intValue()!=1||!n.path("entries").isArray()||n.path("entries").size()>1024)throw new IOException("受管收据格式无效");var entries=new ArrayList<Entry>();
   for(var e:n.path("entries")){keys(e,Set.of("source","projectId","versionId","fileKey","displayName","fileName","size","sha256","dependencies"));if(!e.path("dependencies").isArray()||e.path("dependencies").size()>10000)throw new IOException("受管依赖格式无效");var deps=new ArrayList<ModDependency>();for(var d:e.path("dependencies")){keys(d,Set.of("kind","projectId","versionId","fileName"));deps.add(new ModDependency(ModDependency.Kind.valueOf(LauncherJson.text(d,"kind",32)),ModProtocol.text(d,"projectId",128),ModProtocol.text(d,"versionId",128),ModProtocol.text(d,"fileName",256)));}
    entries.add(new Entry(ModSource.valueOf(LauncherJson.text(e,"source",16)),LauncherJson.text(e,"projectId",128),LauncherJson.text(e,"versionId",128),LauncherJson.text(e,"fileKey",64),ModProtocol.text(e,"displayName",512),LauncherJson.text(e,"fileName",256),ModProtocol.requiredNumber(e,"size"),LauncherJson.text(e,"sha256",64),deps));}
   return new ManagedModReceipt(LauncherJson.text(n,"instanceId",36),LauncherJson.text(n,"gameVersion",128),LauncherJson.text(n,"loader",32),entries);
  }catch(RuntimeException invalid){throw new TaskFailure("MOD_RECORD_INVALID","受管模组收据损坏，原文件已保留");}
 }
}
