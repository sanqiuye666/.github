package cn.dreamingfish.dispatch.server.real;
import cn.dreamingfish.dispatch.server.model.RemoteFailure;
import java.nio.file.*;import java.nio.file.attribute.PosixFilePermission;import java.util.*;import java.util.function.Consumer;
/** 只读取配置指定的秘密文件，拒绝链接与超长内容；从不在异常中输出路径或值。 */
public final class ServerSecrets {
 private ServerSecrets(){}
 public static void safePath(Path path){if(!path.isAbsolute())throw RemoteFailure.of("CONFIG_INVALID");for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw RemoteFailure.of("CONFIG_INVALID");}
 public static String read(Path path,Consumer<String> warning){
  try{safePath(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>512)throw new RemoteFailure("CONFIG_MISSING","指定的秘密文件缺失或不可读");
   try{var p=Files.getPosixFilePermissions(path);if(p.stream().anyMatch(x->Set.of(PosixFilePermission.GROUP_READ,PosixFilePermission.GROUP_WRITE,PosixFilePermission.OTHERS_READ,PosixFilePermission.OTHERS_WRITE).contains(x)))warning.accept("KEY_PERMISSIONS_WARNING");}catch(UnsupportedOperationException e){warning.accept("KEY_PERMISSIONS_UNVERIFIED");}
   String value=Files.readString(path).strip();if(!value.matches("[A-Za-z0-9_-]{43,128}"))throw new RemoteFailure("CONFIG_INVALID","秘密文件格式无效");return value;
  }catch(RemoteFailure e){throw e;}catch(Exception e){throw new RemoteFailure("CONFIG_INVALID","秘密文件无法安全读取");}
 }
}
