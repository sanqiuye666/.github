package cn.dreamingfish.launcher.loaders.fabric;

import cn.dreamingfish.launcher.GameInstance;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.launch.*;
import cn.dreamingfish.launcher.storage.LauncherStorage;
import cn.dreamingfish.launcher.vanilla.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** 仅从固定收据叠加客户端依赖；没有网络、安装器或任意命令执行。 */
public final class FabricLaunchSupport {
 private final LauncherStorage storage;
 public FabricLaunchSupport(Path root){storage=new LauncherStorage(root);}
 public LaunchPlan apply(LaunchPlan base,CancellationToken token)throws IOException{
  if(base.installation().fabricVersion()==null)return base;
  Path relative=Path.of("instances",base.instanceId(),"installations",base.installationId());if(!base.installationDirectory().equals(storage.root().resolve(relative)))throw new LaunchFailure("INSTALLATION_INVALID","Fabric安装目录不匹配");
  var files=new HashMap<String,InstallationReceipt.FileEntry>();for(var file:base.installation().files())files.put(file.path(),file);
  byte[] bytes=read(relative,files,FabricProfile.PATH,2*1024*1024,token);var profile=FabricProfile.parse(bytes,base.instance().gameVersion(),base.installation().fabricVersion());
  var classpath=new LinkedHashSet<Path>();for(var library:profile.libraries()){
   String path="libraries/"+library.path();var record=files.get(path);if(record==null)throw new LaunchFailure("INSTALLATION_INVALID","Fabric依赖缺少固定文件记录");
   verify(relative.resolve(path),record,token);if(library.size()!=null&&library.size()!=record.size())throw new LaunchFailure("INSTALLATION_INVALID","Fabric依赖长度与元数据不一致");
   if(library.digest()!=null){var digest=library.digest().newDigest();try(var input=storage.openRead(relative.resolve(path))){byte[] block=new byte[65536];int n;while((n=input.read(block))!=-1){token.check();digest.update(block,0,n);}}if(!HexFormat.of().formatHex(digest.digest()).equals(library.digest().hex()))throw new LaunchFailure("FILE_CHANGED","Fabric来源摘要已变化");}
   classpath.add(storage.root().resolve(relative).resolve(path));
  }
  classpath.addAll(base.classpath());String separator=base.installation().platform().osName().equals("windows")?";":":";if(classpath.stream().anyMatch(p->p.toString().contains(separator)))throw new LaunchFailure("INVALID_ARGUMENT","数据目录包含Java类路径分隔符");
  var arguments=new ArrayList<>(base.jvmTokens());var text=base.jvmArguments();int cp=text.indexOf("-cp");if(Collections.frequency(text,"-cp")!=1||cp+1>=text.size())throw new LaunchFailure("UNSUPPORTED_METADATA","原版类路径参数无效");arguments.set(cp+1,LaunchArguments.literal(String.join(separator,classpath.stream().map(Path::toString).toList())));for(String arg:profile.jvmArguments())arguments.add(LaunchArguments.literal(arg));
  var i=base.instance();return new LaunchPlan(new GameInstance(i.id(),i.name(),i.gameVersion(),GameInstance.LoaderKind.FABRIC,i.directory()),base.installation(),base.javaRuntime(),base.identity(),base.settingsRevision(),base.installationDirectory(),new ArrayList<>(classpath),arguments,profile.mainClass(),base.gameTokens(),base.assetIndexId(),base.virtualAssets(),base.mapToResources(),base.natives());
 }
 private byte[] read(Path base,Map<String,InstallationReceipt.FileEntry> files,String path,int limit,CancellationToken token)throws IOException{
  var file=files.get(path);if(file==null||file.size()>limit)throw new LaunchFailure("INSTALLATION_INVALID","Fabric元数据缺失或超过上限");verify(base.resolve(path),file,token);byte[] bytes=storage.read(base.resolve(path),limit);if(!HexFormat.of().formatHex(new FileDigest(FileDigest.Algorithm.SHA256,file.sha256()).newDigest().digest(bytes)).equals(file.sha256()))throw new LaunchFailure("FILE_CHANGED","Fabric元数据已变化");return bytes;
 }
 private void verify(Path path,InstallationReceipt.FileEntry file,CancellationToken token)throws IOException{
  token.check();if(!storage.exists(path)||storage.size(path)!=file.size())throw new LaunchFailure("FILE_CHANGED","Fabric依赖缺失或长度变化");var digest=new FileDigest(FileDigest.Algorithm.SHA256,file.sha256()).newDigest();long total=0;try(var input=storage.openRead(path)){byte[] block=new byte[65536];int n;while((n=input.read(block))!=-1){token.check();total+=n;if(total>file.size())throw new LaunchFailure("FILE_CHANGED","Fabric依赖长度变化");digest.update(block,0,n);}}if(total!=file.size()||!HexFormat.of().formatHex(digest.digest()).equals(file.sha256()))throw new LaunchFailure("FILE_CHANGED","Fabric依赖摘要变化");
 }
}
