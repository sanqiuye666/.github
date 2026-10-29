package cn.dreamingfish.launcher.vanilla;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.instances.*;
import cn.dreamingfish.launcher.storage.*;
import java.io.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
public final class VanillaInstaller {
 private final LauncherStorage storage;private final FileInstanceCatalog instances;private final DownloadStore store;private final DownloadBatch batch;private final Clock clock;
 public VanillaInstaller(Path root,FileInstanceCatalog instances,DownloadStore store,DownloadBatch batch,Clock clock){storage=new LauncherStorage(root);this.instances=instances;this.store=store;this.batch=batch;this.clock=clock;}
 private PreparedInstance instance(String id)throws IOException{TaskRequest.uuid(id);return instances.listing().instances().stream().filter(i->i.instance().id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("隔离实例不存在或记录损坏"));}
 private static Path base(String id){TaskRequest.uuid(id);return Path.of("instances/"+id);}
 public InstallationReceipt install(String instanceId,VanillaPlan plan,TaskContext context)throws IOException{
  return install(instanceId,plan,context,(stage,token)->List.of());
 }
 public InstallationReceipt install(String instanceId,VanillaPlan plan,TaskContext context,InstallationTransformer transformer)throws IOException{
  var prepared=instance(instanceId);if(!prepared.instance().gameVersion().equals(plan.versionId()))throw new IllegalArgumentException("安装计划与实例版本不匹配");context.token().check();Path base=base(instanceId),directory=base.resolve("installations");
  try(var running=storage.lock(base.resolve(".running.lock"));var lock=storage.lock(directory.resolve(".install.lock"))){
   cn.dreamingfish.launcher.launch.HistoricalProcessGuard.ensureIdle(storage,instanceId);
   String installationId=UUID.randomUUID().toString();var installedAt=clock.instant();
   // 有生成步骤时，完整收据在转换后检查；原下载流程仍提前检查固定收据大小。
   if(plan.component()==null||plan.component().kind()==cn.dreamingfish.launcher.GameInstance.LoaderKind.QUILT)new InstallationReceipt(instanceId,installationId,plan.versionId(),plan.metadataSha1(),installedAt,plan.platform(),plan.requiredJavaMajor(),plan.files().stream().map(f->new InstallationReceipt.FileEntry(f.relativePath().toString().replace('\\','/'),f.download().expectedSize(),"0".repeat(64))).toList(),plan.fabricVersion(),plan.component()).bytes();
   var unique=new LinkedHashMap<String,DownloadSpec>();for(var file:plan.files())unique.putIfAbsent(key(file.download()),file.download());
   var downloaded=new AtomicLong();long count=plan.files().size();context.progress(TaskState.DOWNLOADING,0,plan.totalBytes(),0,count);
   var verified=batch.fetch(new ArrayList<>(unique.values()),context.token(),bytes->{synchronized(downloaded){long total=downloaded.addAndGet(bytes);try{context.progress(TaskState.DOWNLOADING,total,plan.totalBytes(),0,count);}catch(IOException e){throw new UncheckedIOException(e);}}});
   var objects=new HashMap<String,VerifiedDownload>();int index=0;for(String key:unique.keySet())objects.put(key,verified.get(index++));
   Path pending=directory.resolve(".pending-"+installationId),destination=directory.resolve(installationId);storage.ensureDirectory(pending);
   var files=new ArrayList<InstallationReceipt.FileEntry>();context.progress(TaskState.VERIFYING,downloaded.get(),plan.totalBytes(),0,count);
   for(var file:plan.files()){context.token().check();var object=objects.get(key(file.download()));Path target=pending.resolve(file.relativePath());storage.copyNew(object.relativeObject(),target,context.token());verifyFile(target,object.size(),object.sha256(),context.token());files.add(new InstallationReceipt.FileEntry(file.relativePath().toString().replace('\\','/'),object.size(),object.sha256()));context.progress(TaskState.VERIFYING,downloaded.get(),plan.totalBytes(),files.size(),count);}
   var generated=transformer.apply(storage.root().resolve(pending),context.token());context.token().check();if(generated.size()>512)throw new TaskFailure("RECORD_TOO_LARGE","生成的核心文件数量过多");for(var file:files)verifyFile(pending.resolve(SafeInstallPath.parse(file.path())),file.size(),file.sha256(),context.token());
   long generatedTotal=0;for(Path file:generated){Path relative=SafeInstallPath.parse(file.toString().replace('\\','/'));Path target=pending.resolve(relative);long size=storage.size(target);if(size>2L*1024*1024*1024||(generatedTotal+=size)>4L*1024*1024*1024)throw new TaskFailure("RECORD_TOO_LARGE","生成的核心文件过大");var digest=new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest();long read=0;try(var in=storage.openRead(target)){byte[] block=new byte[65536];int n;while((n=in.read(block))!=-1){context.token().check();if((read+=n)>size)throw new TaskFailure("FILE_CHANGED","生成文件仍在变化");digest.update(block,0,n);}}if(read!=size||storage.size(target)!=size)throw new TaskFailure("FILE_CHANGED","生成文件仍在变化");files.add(new InstallationReceipt.FileEntry(relative.toString().replace('\\','/'),size,HexFormat.of().formatHex(digest.digest())));}
   var receipt=new InstallationReceipt(instanceId,installationId,plan.versionId(),plan.metadataSha1(),installedAt,plan.platform(),plan.requiredJavaMajor(),files,plan.fabricVersion(),plan.component());byte[] bytes=receipt.bytes();storage.writeAtomically(pending.resolve("installation.json"),bytes);String receiptHash=sha256(bytes);
   context.publish(()->{storage.publishDirectory(pending,destination);storage.writeAtomically(base.resolve("active-installation.json"),LauncherJson.write(Map.of("schemaVersion",1,"instanceId",instanceId,"installationId",installationId,"versionId",plan.versionId(),"receiptSha256",receiptHash)));});return receipt;
  }
 }
 private static String key(DownloadSpec spec){return spec.digest().algorithm()+":"+spec.digest().hex();}
 public InstallationListing listing(String instanceId)throws IOException{
  var instance=instance(instanceId);Path base=base(instanceId),directory=base.resolve("installations");var receipts=new HashMap<String,InstallationReceipt>();var hashes=new HashMap<String,String>();var problems=new ArrayList<InstallationListing.Problem>();
  for(var folder:storage.children(directory)){String id=folder.getFileName().toString();if(id.equals(".install.lock")||id.startsWith(".pending-"))continue;try{TaskRequest.uuid(id);storage.requireDirectory(folder);byte[] bytes=storage.read(folder.resolve("installation.json"),32*1024*1024);var receipt=InstallationReceipt.parse(bytes);if(!receipt.installationId().equals(id)||!receipt.instanceId().equals(instanceId)||!receipt.versionId().equals(instance.instance().gameVersion()))throw new IOException("收据与实例不匹配");receipts.put(id,receipt);hashes.put(id,sha256(bytes));}catch(IOException|RuntimeException error){problems.add(new InstallationListing.Problem(id,"安装记录损坏，原文件已保留"));}}
  var active=new ArrayList<InstallationReceipt>();Path pointer=base.resolve("active-installation.json");if(storage.exists(pointer))try{var n=LauncherJson.read(storage.read(pointer,4096),4096);InstallationReceipt.keys(n,Set.of("schemaVersion","instanceId","installationId","versionId","receiptSha256"));String id=LauncherJson.text(n,"installationId",36);TaskRequest.uuid(id);if(!n.path("schemaVersion").isInt()||n.path("schemaVersion").intValue()!=1||!instanceId.equals(n.path("instanceId").asText())||!instance.instance().gameVersion().equals(n.path("versionId").asText())||!receipts.containsKey(id)||!hashes.get(id).equals(n.path("receiptSha256").asText()))throw new IOException("活动引用与收据不匹配");active.add(receipts.get(id));}catch(IOException|RuntimeException error){problems.add(new InstallationListing.Problem("active","活动安装引用损坏或缺失，未自动修复"));}
  return new InstallationListing(active,problems);
 }
 public InstallationReceipt verifyActive(String instanceId,CancellationToken token)throws IOException{
  token.check();var listing=listing(instanceId);if(listing.installations().size()!=1)throw new TaskFailure("RECORD_INVALID","没有有效活动安装记录，已有文件未被改动");var receipt=listing.installations().getFirst();Path root=base(instanceId).resolve("installations").resolve(receipt.installationId());for(var f:receipt.files())verifyFile(root.resolve(SafeInstallPath.parse(f.path())),f.size(),f.sha256(),token);token.check();return receipt;
 }
 private void verifyFile(Path file,long size,String expected,CancellationToken token)throws IOException{
  token.check();if(!storage.exists(file))throw new TaskFailure("FILE_MISSING","安装文件缺失，已有文件未被改动");if(storage.size(file)!=size)throw new TaskFailure("FILE_CHANGED","安装文件长度已变化，原文件已保留");var digest=new FileDigest(FileDigest.Algorithm.SHA256,expected).newDigest();long count=0;try(var in=storage.openRead(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){token.check();count+=n;if(count>size)throw new TaskFailure("FILE_CHANGED","安装文件长度已变化，原文件已保留");digest.update(b,0,n);}}if(count!=size||!HexFormat.of().formatHex(digest.digest()).equals(expected))throw new TaskFailure("FILE_CHANGED","安装文件摘要已变化，原文件已保留");token.check();
 }
 private static String sha256(byte[] bytes){return HexFormat.of().formatHex(new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest().digest(bytes));}
}
