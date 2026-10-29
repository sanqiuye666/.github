package cn.dreamingfish.launcher.mods;

import cn.dreamingfish.launcher.downloads.*;
import java.io.*;
import java.util.*;

/** 有界解析必需文件；可选或内嵌关系不隐式下载。 */
public final class ModDependencyResolver {
 private final ModService service;
 public ModDependencyResolver(ModService service){this.service=Objects.requireNonNull(service);}
 public ModInstallPlan plan(ModFileRef root,String gameVersion,String loader,CancellationToken token)throws IOException{
  token.check();var selected=new LinkedHashMap<String,ModFile>();var visiting=new HashSet<String>();var warnings=new TreeSet<String>();
  visit(service.installFile(root,gameVersion,loader,token),gameVersion,loader,token,selected,visiting,warnings,0);
  for(var f:selected.values())for(var d:f.dependencies())if(d.kind()==ModDependency.Kind.INCOMPATIBLE){
   if(selected.values().stream().anyMatch(other->(d.projectId()==null||d.projectId().equals(other.ref().projectId()))&&(d.versionId()==null||d.versionId().equals(other.ref().versionId()))&&(d.projectId()!=null||d.versionId()!=null)))throw new TaskFailure("DEPENDENCY_CONFLICT","所选文件图包含平台声明的不兼容项目");
  }
  ModInstallPlan.validateFiles(selected.values());
  return new ModInstallPlan(gameVersion,loader,List.copyOf(selected.values()),List.copyOf(warnings));
 }
 private void visit(ModFile file,String game,String loader,CancellationToken token,Map<String,ModFile> selected,Set<String> visiting,Set<String> warnings,int depth)throws IOException{
  token.check();if(depth>=16)throw new TaskFailure("DEPENDENCY_LIMIT","必需依赖超过16层，未改动实例");String project=file.ref().projectId();
  if(visiting.contains(project))throw new TaskFailure("DEPENDENCY_CYCLE","必需依赖形成循环，未改动实例");
  var previous=selected.get(project);if(previous!=null){if(!previous.ref().equals(file.ref())||!previous.sourceDigest().equals(file.sourceDigest())||!previous.size().equals(file.size()))throw new TaskFailure("DEPENDENCY_CONFLICT","同一项目要求不同的版本或文件，请调整所选模组");return;}
  if(selected.size()>=64)throw new TaskFailure("DEPENDENCY_LIMIT","必需依赖超过64个项目，未改动实例");
  selected.put(project,file);visiting.add(project);
  for(var dependency:file.dependencies()){
   token.check();
   if(dependency.kind()==ModDependency.Kind.REQUIRED){if(dependency.projectId()==null&&dependency.versionId()==null)throw new TaskFailure("DEPENDENCY_UNRESOLVED","必需依赖缺少项目/版本引用，不能自动安装");visit(service.dependency(file.ref().source(),dependency,game,loader,token),game,loader,token,selected,visiting,warnings,depth+1);}
   else if(dependency.kind()==ModDependency.Kind.UNKNOWN)throw new TaskFailure("DEPENDENCY_UNRESOLVED","平台包含未识别的依赖关系，不能自动省略");
   else if(dependency.kind()!=ModDependency.Kind.INCOMPATIBLE)warnings.add("可选、内嵌或工具依赖仅作说明，不会重复装入实例。");
  }
  visiting.remove(project);
 }
}
