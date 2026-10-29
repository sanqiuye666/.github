package cn.dreamingfish.launcher.resources;
import cn.dreamingfish.launcher.downloads.*;import cn.dreamingfish.launcher.instances.FileInstanceCatalog;import cn.dreamingfish.launcher.launch.*;import cn.dreamingfish.launcher.storage.*;import cn.dreamingfish.launcher.vanilla.SafeInstallPath;
import java.io.*;import java.nio.*;import java.nio.file.*;import java.security.*;import java.util.*;

/** 每次只操作一个实例和一种资源，源文件只读，所有移除先保存可验证副本。 */
public final class ResourceStore {
 public static final long MAX_BYTES=512L*1024*1024;
 public record Entry(String name,long size,String modifiedAt,String revision){}
 public record Backup(String id,String name,String kind,long size,String sha256,String createdAt){}
 public record Problem(String name,String message){}
 public record Listing(String instanceId,String kind,String directory,List<Entry> entries,List<Backup> backups,List<Problem> problems){}
 private final LauncherStorage storage;private final FileInstanceCatalog instances;
 public ResourceStore(Path root){storage=new LauncherStorage(root);instances=new FileInstanceCatalog(root,java.time.Clock.systemUTC());}
 private Path base(String id)throws IOException{TaskRequest.uuid(id);if(instances.listing().instances().stream().noneMatch(i->i.instance().id().equals(id)))throw fail("INSTANCE_NOT_FOUND","找不到有效实例");return Path.of("instances",id);}
 private Path directory(String id,ResourceKind kind)throws IOException{return base(id).resolve(".minecraft").resolve(kind.directory());}
 private static String name(String name,ResourceKind kind){if(name==null||name.length()>128||name.startsWith(".")||SafeInstallPath.parse(name).getNameCount()!=1||!kind.accepts(name))throw new IllegalArgumentException("请选择此类型的普通资源文件名");return name;}
 private Entry entry(Path relative)throws IOException{var a=storage.attributes(relative);if(!a.isRegularFile())throw new IOException("不是普通文件");String name=relative.getFileName().toString(),modified=a.lastModifiedTime().toInstant().toString();return new Entry(name,a.size(),modified,hex((name+"\n"+a.size()+"\n"+a.lastModifiedTime()+"\n"+a.fileKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 public Listing listing(String id,ResourceKind kind)throws IOException{
  Path dir=directory(id,kind);var entries=new ArrayList<Entry>();var problems=new ArrayList<Problem>();var children=storage.children(dir,1001);if(children.size()>1000)throw fail("RESOURCE_LIMIT","资源目录超过1000条，请先在文件管理器整理");
  for(Path file:children){String n=file.getFileName().toString();try{name(n,kind);entries.add(entry(file));}catch(IOException|IllegalArgumentException invalid){problems.add(new Problem(n,"未识别或无法读取，原文件保留"));}}
  var backups=new ArrayList<Backup>();var folders=storage.children(base(id).resolve("resource-backups"),1001);if(folders.size()>1000)throw fail("RESOURCE_LIMIT","备份超过1000条，请先归档");
  for(Path folder:folders){try{Backup b=backupRecord(id,folder.getFileName().toString());if(b.kind().equals(kind.name()))backups.add(b);}catch(IOException|IllegalArgumentException invalid){problems.add(new Problem("备份 "+folder.getFileName(),"备份记录不完整，原文件保留"));}}
  return new Listing(id,kind.name(),storage.root().resolve(dir).toString(),List.copyOf(entries),List.copyOf(backups),List.copyOf(problems));
 }
 private void ensureRoom(Path dir)throws IOException{if(storage.children(dir,1000).size()>=1000)throw fail("RESOURCE_LIMIT","资源数量已达1000条上限，未添加文件");}
 private void available(Path dir,String target)throws IOException{for(Path child:storage.children(dir,1001))if(SafeInstallPath.key(child.getFileName()).equals(SafeInstallPath.key(Path.of(target))))throw fail("RESOURCE_CONFLICT","目标同名或名称冲突，未覆盖原文件");}
 private interface Action{void run()throws IOException;}
 private Listing mutate(String id,ResourceKind kind,CancellationToken token,Action action)throws IOException{
  Path b=base(id);token.check();try(var running=storage.lock(b.resolve(".running.lock"))){HistoricalProcessGuard.ensureIdle(storage,id);try(var installing=storage.lock(b.resolve("installations/.install.lock"))){token.check();action.run();return listing(id,kind);}}
 }
 public Listing importFile(String id,ResourceKind kind,Path source,CancellationToken token)throws IOException{
  token.check();if(source==null||!source.isAbsolute()||source.getRoot()==null)throw new IllegalArgumentException("请选择本机绝对文件路径");String fileName=name(source.getFileName().toString(),kind);
  var external=new LauncherStorage(source.getRoot());Path relative=source.getRoot().relativize(source);var before=external.attributes(relative);if(!before.isRegularFile()||before.size()<1||before.size()>MAX_BYTES)throw fail("RESOURCE_INVALID","请选择512MiB以内的普通资源文件");
  return mutate(id,kind,token,()->{Path dir=directory(id,kind);available(dir,fileName);if(storage.children(dir,1001).size()>=1000)throw fail("RESOURCE_LIMIT","资源数量已达上限");
   try(var stage=storage.beginStagedWrite(base(id).resolve("resource-staging"));var in=external.openRead(relative)){byte[] bytes=new byte[65536];long copied=0;int n;while((n=in.read(bytes))!=-1){token.check();if((copied+=n)>before.size())throw fail("RESOURCE_CHANGED","源文件在读取时变化，请重试");stage.write(ByteBuffer.wrap(bytes,0,n));}
    var after=external.attributes(relative);if(copied!=before.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!Objects.equals(before.fileKey(),after.fileKey()))throw fail("RESOURCE_CHANGED","源文件在读取时变化，请重试");
    try{ResourceFormat.validate(stage.path(),kind);}catch(IOException invalid){throw fail("RESOURCE_INVALID","资源容器或必要元数据无效，未发布到游戏目录");}token.check();available(dir,fileName);stage.publishNew(dir.resolve(fileName));
   }
  });
 }
 public Listing change(String id,ResourceKind kind,String original,String revision,String operation,String target,CancellationToken token)throws IOException{
  name(original,kind);if(!Set.of("COPY","RENAME","REMOVE").contains(operation))throw new IllegalArgumentException("资源操作无效");if(!operation.equals("REMOVE"))name(target,kind);
  return mutate(id,kind,token,()->{Path dir=directory(id,kind),file=dir.resolve(original);Entry current=entry(file);if(!current.revision().equals(revision))throw fail("RESOURCE_CHANGED","资源在列表读取后变化，请刷新");if(current.size()>MAX_BYTES)throw fail("RESOURCE_LIMIT","文件超过512MiB，未修改");
   if(!operation.equals("REMOVE"))available(dir,target);
   if(operation.equals("COPY")){ensureRoom(dir);copyVerified(file,dir.resolve(target),digest(file,token),token);return;}
   Backup backup=backup(id,kind,file,current,token);Path saved=base(id).resolve("resource-backups").resolve(backup.id()).resolve("original");
   if(!entry(file).revision().equals(revision)||!digest(file,token).equals(backup.sha256()))throw fail("RESOURCE_CHANGED","备份后原文件变化，原件和备份已保留");
   if(operation.equals("RENAME"))copyVerified(saved,dir.resolve(target),backup.sha256(),token);
   storage.deleteFile(file);
  });
 }
 private Backup backup(String id,ResourceKind kind,Path source,Entry entry,CancellationToken token)throws IOException{
  if(storage.children(base(id).resolve("resource-backups"),1001).size()>=1000)throw fail("RESOURCE_LIMIT","备份已达1000条，请先归档");
  String key=UUID.randomUUID().toString();Path b=base(id).resolve("resource-backups").resolve(key);String digest=digest(source,token);copyVerified(source,b.resolve("original"),digest,token);
  var backup=new Backup(key,entry.name(),kind.name(),entry.size(),digest,java.time.Instant.now().toString());storage.writeAtomically(b.resolve("backup.json"),LauncherJson.write(backup));return backup;
 }
 private Backup backupRecord(String id,String backupId)throws IOException{
  TaskRequest.uuid(backupId);var n=LauncherJson.read(storage.read(base(id).resolve("resource-backups").resolve(backupId).resolve("backup.json"),65536),65536);var keys=new HashSet<String>();n.fieldNames().forEachRemaining(keys::add);if(!keys.equals(Set.of("id","name","kind","size","sha256","createdAt")))throw new IOException("备份记录字段无效");
  if(!n.path("size").isIntegralNumber()||!n.path("size").canConvertToLong())throw new IOException("备份大小无效");
  var b=new Backup(LauncherJson.text(n,"id",36),LauncherJson.text(n,"name",128),LauncherJson.text(n,"kind",32),n.path("size").longValue(),LauncherJson.text(n,"sha256",64),LauncherJson.text(n,"createdAt",64));if(!b.id().equals(backupId)||b.size()<0||b.size()>MAX_BYTES||!b.sha256().matches("[a-f0-9]{64}"))throw new IOException("备份记录无效");name(b.name(),ResourceKind.valueOf(b.kind()));try{java.time.Instant.parse(b.createdAt());}catch(java.time.DateTimeException invalid){throw new IOException("备份时间无效",invalid);}return b;
 }
 public Listing restore(String id,ResourceKind kind,String backupId,CancellationToken token)throws IOException{
  return mutate(id,kind,token,()->{Backup backup=backupRecord(id,backupId);if(!backup.kind().equals(kind.name()))throw new IllegalArgumentException("备份类型不匹配");Path file=base(id).resolve("resource-backups").resolve(backupId).resolve("original");if(storage.size(file)!=backup.size())throw fail("RESOURCE_CHANGED","备份内容变化，未恢复");Path dir=directory(id,kind);available(dir,backup.name());ensureRoom(dir);copyVerified(file,dir.resolve(backup.name()),backup.sha256(),token);});
 }
 private void copyVerified(Path source,Path destination,String expected,CancellationToken token)throws IOException{
  try{var hash=MessageDigest.getInstance("SHA-256");try(var in=storage.openRead(source);var stage=storage.beginStagedWrite(destination.getParent())){byte[] bytes=new byte[65536];long size=0;int n;while((n=in.read(bytes))!=-1){token.check();if((size+=n)>MAX_BYTES)throw fail("RESOURCE_LIMIT","文件超过512MiB");hash.update(bytes,0,n);stage.write(ByteBuffer.wrap(bytes,0,n));}if(!HexFormat.of().formatHex(hash.digest()).equals(expected))throw fail("RESOURCE_CHANGED","复制时文件变化，未发布目标");token.check();stage.publishNew(destination);}}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
 }
 private String digest(Path path,CancellationToken token)throws IOException{try{var hash=MessageDigest.getInstance("SHA-256");long read=0;try(var in=storage.openRead(path)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){token.check();if((read+=n)>MAX_BYTES)throw fail("RESOURCE_LIMIT","文件超过512MiB");hash.update(bytes,0,n);}}return HexFormat.of().formatHex(hash.digest());}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
 private static String hex(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
 private static LaunchFailure fail(String code,String message){return new LaunchFailure(code,message);}
}
