package cn.dreamingfish.launcher.mods;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.vanilla.SafeInstallPath;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** 只包含平台已固定的文件图；不表示文件已经装入任何实例。 */
public record ModInstallPlan(String gameVersion,String loader,List<ModFile> files,List<String> warnings) {
 public ModInstallPlan {
  Objects.requireNonNull(gameVersion);Objects.requireNonNull(loader);files=List.copyOf(files);warnings=List.copyOf(warnings);
  if(files.isEmpty()||files.size()>64)throw new IllegalArgumentException("模组计划数量无效");
 }
 public long totalBytes(){long bytes=0;for(var file:files)bytes=Math.addExact(bytes,file.size());return bytes;}
 public String fingerprint()throws IOException{
  var pins=new ArrayList<Object>();
  for(var f:files.stream().sorted(Comparator.comparing((ModFile f)->f.ref().projectId()).thenComparing(f->f.ref().versionId())).toList()){
   var dependencies=f.dependencies().stream().map(d->d.kind()+":"+Objects.toString(d.projectId(),"")+":"+Objects.toString(d.versionId(),"")+":"+Objects.toString(d.fileName(),"")).sorted().toList();
   pins.add(Map.of("source",f.ref().source().name(),"project",f.ref().projectId(),"version",f.ref().versionId(),"key",f.ref().fileKey(),"name",f.fileName(),"size",f.size(),"digest",f.sourceDigest().algorithm()+":"+f.sourceDigest().hex(),"dependencies",dependencies));
  }
  return HexFormat.of().formatHex(new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest().digest(LauncherJson.write(Map.of("gameVersion",gameVersion,"loader",loader,"files",pins))));
 }
 static void validateFiles(Collection<ModFile> files)throws IOException{
  try{
   var paths=new ArrayList<Path>();
   for(var f:files){Path path=SafeInstallPath.parse(f.fileName());if(path.getNameCount()!=1||!f.fileName().toLowerCase(Locale.ROOT).endsWith(".jar"))throw new IllegalArgumentException("实例模组需要普通JAR文件名");paths.add(path);}
   SafeInstallPath.checkDistinct(paths);
  }catch(IllegalArgumentException invalid){throw new TaskFailure("MOD_NAME_CONFLICT","模组文件名不安全或相互冲突，未改动实例");}
 }
}
