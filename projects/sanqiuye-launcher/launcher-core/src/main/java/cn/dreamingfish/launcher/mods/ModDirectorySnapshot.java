package cn.dreamingfish.launcher.mods;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.*;
import cn.dreamingfish.launcher.vanilla.SafeInstallPath;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 对mods内容取有界快照；未知普通文件、嵌套及空目录都保留。 */
record ModDirectorySnapshot(boolean existed,List<Entry> files,List<String> directories) {
 record Entry(String path,long size,String sha256){}
 static final long MAX_BYTES=16L*1024*1024*1024;
 ModDirectorySnapshot{files=List.copyOf(files);directories=List.copyOf(directories);}
 static ModDirectorySnapshot capture(LauncherStorage storage,Path directory,CancellationToken token)throws IOException{
  token.check();if(!storage.exists(directory))return new ModDirectorySnapshot(false,List.of(),List.of());storage.requireDirectory(directory);
  var files=new ArrayList<Entry>();var directories=new ArrayList<String>();scan(storage,directory,directory,token,files,directories,0,new long[]{0});
  SafeInstallPath.checkDistinct(files.stream().map(e->Path.of(e.path())).toList());
  var names=new HashSet<String>();for(var file:files)names.add(SafeInstallPath.key(Path.of(file.path())));
  for(String dir:directories){String key=SafeInstallPath.key(Path.of(dir));if(!names.add(key))throw new TaskFailure("MOD_NAME_CONFLICT","模组目录有大小写或文件/目录冲突");}
  return new ModDirectorySnapshot(true,files,directories);
 }
 private static void scan(LauncherStorage storage,Path base,Path current,CancellationToken token,List<Entry> files,List<String> dirs,int depth,long[] total)throws IOException{
  if(depth>16)throw new TaskFailure("MOD_DIRECTORY_LIMIT","模组目录嵌套超过16层");
  for(var child:storage.children(current)){
   token.check();if(files.size()+dirs.size()>=8192)throw new TaskFailure("MOD_DIRECTORY_LIMIT","模组目录超过8192项");String relative=base.relativize(child).toString().replace('\\','/');SafeInstallPath.parse(relative);
   if(Files.isDirectory(storage.root().resolve(child),LinkOption.NOFOLLOW_LINKS)){storage.requireDirectory(child);dirs.add(relative);scan(storage,base,child,token,files,dirs,depth+1,total);}
   else{long size=storage.size(child);if(size>2L*1024*1024*1024||(total[0]+=size)>MAX_BYTES)throw new TaskFailure("MOD_DIRECTORY_LIMIT","模组目录超过文件或16GiB总上限");files.add(new Entry(relative,size,hash(storage,child,size,token)));}
  }
 }
 static String hash(LauncherStorage storage,Path file,long size,CancellationToken token)throws IOException{
  var digest=new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest();long read=0;
  try(var in=storage.openRead(file)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){token.check();read+=n;if(read>size)throw new TaskFailure("MOD_FILES_CHANGED","模组目录正在变化，请重新预览");digest.update(bytes,0,n);}}
  if(read!=size||storage.size(file)!=size)throw new TaskFailure("MOD_FILES_CHANGED","模组文件长度已变化，请重新预览");return HexFormat.of().formatHex(digest.digest());
 }
 String fingerprint()throws IOException{return hex(LauncherJson.write(Map.of("existed",existed,"files",files,"directories",directories)));}
 long totalBytes(){return files.stream().mapToLong(Entry::size).sum();}
 static String hex(byte[] bytes){return HexFormat.of().formatHex(new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest().digest(bytes));}
}
