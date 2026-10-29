package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.CancellationToken;
import java.io.IOException;
public interface ModProvider {
 default java.util.List<cn.dreamingfish.launcher.content.ContentCategory> categories(CancellationToken token)throws IOException{token.check();return java.util.List.of();}
 ModSource source();SourceStatus status(CancellationToken token)throws IOException;
 ModPage search(ModQuery query,CancellationToken token)throws IOException;
 ModProject project(String projectId,CancellationToken token)throws IOException;
 ModFilesPage files(String projectId,ModFileQuery query,CancellationToken token)throws IOException;
 ModFile resolve(ModFileRef ref,CancellationToken token)throws IOException;
 default ModFile dependency(ModDependency dependency,ModFileQuery query,CancellationToken token)throws IOException{
  if(dependency.projectId()==null||dependency.versionId()!=null)throw new cn.dreamingfish.launcher.downloads.TaskFailure("DEPENDENCY_UNRESOLVED","此来源不能解析指定的必需版本");
  return files(dependency.projectId(),query,token).items().stream().filter(ModFile::downloadable)
   .min(java.util.Comparator.comparingInt(f->"release".equals(f.releaseChannel())?0:1))
   .orElseThrow(()->new cn.dreamingfish.launcher.downloads.TaskFailure("DEPENDENCY_UNRESOLVED","必需依赖没有符合当前条件的文件"));
 }
}
