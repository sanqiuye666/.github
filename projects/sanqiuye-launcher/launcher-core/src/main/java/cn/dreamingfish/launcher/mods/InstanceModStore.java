package cn.dreamingfish.launcher.mods;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.launch.HistoricalProcessGuard;
import cn.dreamingfish.launcher.storage.*;
import cn.dreamingfish.launcher.vanilla.*;
import java.io.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipFile;

/** 每个实例独立的模组事务。调用方不能提供路径或任意文件内容。 */
public final class InstanceModStore {
 public record Plan(String instanceId,InstallationReceipt installation,String beforeSha256,ModInstallPlan graph,boolean removal){
  public String fingerprint()throws IOException{return ModDirectorySnapshot.hex(LauncherJson.write(Map.of("instanceId",instanceId,"installation",ModDirectorySnapshot.hex(installation.bytes()),"before",beforeSha256,"graph",removal?"REMOVE":graph.fingerprint())));}
 }
 public record Listing(String instanceId,String gameVersion,String loader,String managedLoader,List<ManagedModReceipt.Entry> entries,int manualFiles,String directory,String fingerprint,boolean compatible){}
 private final LauncherStorage storage;private final VanillaInstaller installer;private final DownloadStore downloads;private final DownloadBatch batch;private final Clock clock;
 public InstanceModStore(Path root,VanillaInstaller installer,DownloadStore downloads,DownloadBatch batch,Clock clock){storage=new LauncherStorage(root);this.installer=installer;this.downloads=downloads;this.batch=batch;this.clock=clock;}
 /** 供启动保护使用；此构造不提供安装下载能力。 */
 public InstanceModStore(Path root,VanillaInstaller installer,Clock clock){this(root,installer,null,null,clock);}
 private static Path base(String id){TaskRequest.uuid(id);return Path.of("instances",id);}
 private static Path directory(String id){return base(id).resolve(".minecraft/mods");}
 private InstallationReceipt active(String id)throws IOException{var list=installer.listing(id);if(list.installations().size()!=1)throw new TaskFailure("INSTALLATION_INVALID","请先完成实例的有效加载器安装");return list.installations().getFirst();}
 private static String loader(InstallationReceipt receipt){return receipt.loaderKind().name().toLowerCase(Locale.ROOT);}
 private ManagedModReceipt owned(String id,InstallationReceipt core)throws IOException{
  Path path=directory(id).resolve(ManagedModReceipt.NAME);if(!storage.exists(path))return new ManagedModReceipt(id,core.versionId(),Set.of("fabric","quilt","forge","neoforge").contains(loader(core))?loader(core):"fabric",List.of());
  var receipt=ManagedModReceipt.parse(storage.read(path,2*1024*1024));if(!receipt.instanceId().equals(id))throw new TaskFailure("MOD_RECORD_INVALID","模组收据不属于本实例");return receipt;
 }
 private void verifyOwned(ManagedModReceipt receipt,ModDirectorySnapshot snapshot)throws IOException{
  var files=new HashMap<String,ModDirectorySnapshot.Entry>();for(var entry:snapshot.files())files.put(SafeInstallPath.key(Path.of(entry.path())),entry);
  for(var entry:receipt.entries()){var file=files.get(SafeInstallPath.key(Path.of(entry.fileName())));if(file==null||file.size()!=entry.size()||!file.sha256().equals(entry.sha256()))throw new TaskFailure("MOD_FILES_CHANGED","受管模组已被修改或移走；原件保留，请先检查实例模组目录");}
 }
 private static boolean compatible(ManagedModReceipt mods,InstallationReceipt core){return mods.entries().isEmpty()||mods.gameVersion().equals(core.versionId())&&mods.loader().equals(loader(core));}
 public Plan prepare(String id,ModInstallPlan graph,CancellationToken token)throws IOException{return plan(id,graph,false,token);}
 public Plan removal(String id,CancellationToken token)throws IOException{return plan(id,null,true,token);}
 private Plan plan(String id,ModInstallPlan graph,boolean removal,CancellationToken token)throws IOException{
  try(var run=storage.lock(base(id).resolve(".running.lock"));var lock=storage.lock(base(id).resolve("installations/.install.lock"))){HistoricalProcessGuard.ensureIdle(storage,id);recoverLocked(id,token);return planLocked(id,graph,removal,token);}
 }
 private Plan planLocked(String id,ModInstallPlan graph,boolean removal,CancellationToken token)throws IOException{
  token.check();var core=active(id);var snapshot=ModDirectorySnapshot.capture(storage,directory(id),token);var owned=owned(id,core);verifyOwned(owned,snapshot);
  if(!removal){
   if(!Set.of("fabric","quilt","forge","neoforge").contains(loader(core)))throw new TaskFailure("MOD_LOADER_UNSUPPORTED","请先为目标实例安装受支持的加载器");
   if(!graph.gameVersion().equals(core.versionId())||!graph.loader().equals(loader(core)))throw new TaskFailure("INCOMPATIBLE","模组计划与实例实际游戏版本或加载器不同");
   if(!compatible(owned,core))throw new TaskFailure("MOD_LOADER_CHANGED","实例核心已切换，请先移除原核心的受管模组");
   ModInstallPlan.validateFiles(graph.files());var retained=new ArrayList<>(merged(owned,graph));for(var f:graph.files())retained.add(new ManagedModReceipt.Entry(f.ref().source(),f.ref().projectId(),f.ref().versionId(),f.ref().fileKey(),f.displayName(),f.fileName(),f.size(),"0".repeat(64),f.dependencies()));checkDependencies(retained);
   var removedNames=new HashSet<String>();for(var entry:owned.entries())if(graph.files().stream().anyMatch(f->entry.key().equals(f.ref().source()+":"+f.ref().projectId())))removedNames.add(SafeInstallPath.key(Path.of(entry.fileName())));
   for(var file:graph.files())for(var existing:snapshot.files())if(SafeInstallPath.key(Path.of(existing.path())).equals(SafeInstallPath.key(Path.of(file.fileName())))&&!removedNames.contains(SafeInstallPath.key(Path.of(existing.path()))))throw new TaskFailure("MOD_NAME_CONFLICT","同名文件未由三秋叶管理，不能覆盖："+file.fileName());
   if(snapshot.totalBytes()+graph.totalBytes()>ModDirectorySnapshot.MAX_BYTES)throw new TaskFailure("MOD_DIRECTORY_LIMIT","暂存模组将超过16GiB上限");
  }
  return new Plan(id,core,snapshot.fingerprint(),graph,removal);
 }
 private static List<ManagedModReceipt.Entry> merged(ManagedModReceipt old,ModInstallPlan graph){var selected=new HashSet<String>();for(var file:graph.files())selected.add(file.ref().source()+":"+file.ref().projectId());return old.entries().stream().filter(e->!selected.contains(e.key())).toList();}
 private static void checkDependencies(List<ManagedModReceipt.Entry> entries)throws IOException{
  for(var entry:entries)for(var dep:entry.dependencies()){
   boolean found=entries.stream().anyMatch(other->other.source()==entry.source()&&(dep.projectId()==null||dep.projectId().equals(other.projectId()))&&(dep.versionId()==null||dep.versionId().equals(other.versionId())));
   if(dep.kind()==ModDependency.Kind.REQUIRED&&!found||dep.kind()==ModDependency.Kind.INCOMPATIBLE&&found)throw new TaskFailure("DEPENDENCY_CONFLICT","已有受管模组的固定依赖与本次计划冲突");
  }
 }
 public void install(Plan expected,TaskContext context)throws IOException{if(expected.removal())throw new IllegalArgumentException("需要安装计划");apply(expected,context);}
 public void remove(Plan expected,TaskContext context)throws IOException{if(!expected.removal())throw new IllegalArgumentException("需要移除计划");apply(expected,context);}
 private void apply(Plan expected,TaskContext context)throws IOException{
  String id=expected.instanceId();
  try(var running=storage.lock(base(id).resolve(".running.lock"));var lock=storage.lock(base(id).resolve("installations/.install.lock"))){
   HistoricalProcessGuard.ensureIdle(storage,id);recoverLocked(id,context.token());var current=planLocked(id,expected.graph(),expected.removal(),context.token());
   if(!current.fingerprint().equals(expected.fingerprint()))throw new TaskFailure("PLAN_CHANGED","实例安装或模组目录已变化，请重新预览");
   var graph=expected.graph();var files=expected.removal()?List.<ModFile>of():graph.files();var transferred=new AtomicLong();long total=expected.removal()?0:graph.totalBytes();context.progress(TaskState.DOWNLOADING,0,total,0,(long)files.size());
   var objects=batch.fetch(files.stream().map(ModFile::downloadSpec).toList(),context.token(),n->{synchronized(transferred){try{context.progress(TaskState.DOWNLOADING,transferred.addAndGet(n),total,0,(long)files.size());}catch(IOException failure){throw new UncheckedIOException(failure);}}});
   var before=ModDirectorySnapshot.capture(storage,directory(id),context.token());if(!before.fingerprint().equals(expected.beforeSha256()))throw new TaskFailure("PLAN_CHANGED","下载期间模组目录已变化，未替换实例");
   var old=owned(id,current.installation());var kept=expected.removal()?new ArrayList<ManagedModReceipt.Entry>():new ArrayList<>(merged(old,graph));
   var excluded=new HashSet<String>();excluded.add(SafeInstallPath.key(Path.of(ManagedModReceipt.NAME)));for(var oldFile:old.entries())if(expected.removal()||files.stream().anyMatch(f->oldFile.key().equals(f.ref().source()+":"+f.ref().projectId())))excluded.add(SafeInstallPath.key(Path.of(oldFile.fileName())));
   String operation=UUID.randomUUID().toString();Path transaction=base(id).resolve("mod-transactions").resolve(operation),next=transaction.resolve("next");storage.ensureDirectory(next);
   for(String folder:before.directories())storage.ensureDirectory(next.resolve(SafeInstallPath.parse(folder)));
   for(var file:before.files())if(!excluded.contains(SafeInstallPath.key(Path.of(file.path()))))storage.copyNew(directory(id).resolve(SafeInstallPath.parse(file.path())),next.resolve(SafeInstallPath.parse(file.path())),context.token());
   for(int i=0;i<files.size();i++){
    var file=files.get(i);var object=objects.get(i);downloads.verify(object,context.token());Path staged=next.resolve(SafeInstallPath.parse(file.fileName()));storage.copyNew(object.relativeObject(),staged,context.token());
    if(!ModDirectorySnapshot.hash(storage,staged,object.size(),context.token()).equals(object.sha256()))throw new TaskFailure("FILE_CHANGED","暂存模组摘要不匹配");validateJar(staged,graph.loader());
    kept.add(new ManagedModReceipt.Entry(file.ref().source(),file.ref().projectId(),file.ref().versionId(),file.ref().fileKey(),file.displayName(),file.fileName(),object.size(),object.sha256(),file.dependencies()));context.progress(TaskState.VERIFYING,transferred.get(),total,i+1,(long)files.size());
   }
   checkDependencies(kept);storage.writeAtomically(next.resolve(ManagedModReceipt.NAME),new ManagedModReceipt(id,current.installation().versionId(),Set.of("fabric","quilt","forge","neoforge").contains(loader(current.installation()))?loader(current.installation()):old.loader(),kept).bytes());
   var after=ModDirectorySnapshot.capture(storage,next,context.token());var fresh=ModDirectorySnapshot.capture(storage,directory(id),context.token());if(!fresh.fingerprint().equals(expected.beforeSha256())||!active(id).equals(current.installation()))throw new TaskFailure("PLAN_CHANGED","确认后实例或模组目录变化，未替换");
   context.publish(()->{
    var journal=new LinkedHashMap<String,Object>(Map.of("schemaVersion",1,"instanceId",id,"operationId",operation,"phase","PREPARED","hadOriginal",before.existed(),"beforeSha256",before.fingerprint(),"afterSha256",after.fingerprint()));storage.writeAtomically(base(id).resolve("mods-transaction.json"),LauncherJson.write(journal));
    try{if(before.existed())storage.publishDirectory(directory(id),transaction.resolve("previous"));storage.publishDirectory(next,directory(id));journal.put("phase","COMMITTED");storage.writeAtomically(base(id).resolve("mods-transaction.json"),LauncherJson.write(journal));}
    catch(IOException failure){try{recoverLocked(id,new CancellationToken());}catch(IOException recovery){failure.addSuppressed(recovery);}throw failure;}
   });
  }
 }
 private void validateJar(Path relative,String loader)throws IOException{
  try(var zip=new ZipFile(storage.root().resolve(relative).toFile())){
   if(zip.size()<1||zip.size()>100000)throw new IOException("JAR目录数量无效");var candidates=switch(loader){case "fabric"->List.of("fabric.mod.json");case "quilt"->List.of("quilt.mod.json","fabric.mod.json");case "forge"->List.of("META-INF/mods.toml");case "neoforge"->List.of("META-INF/neoforge.mods.toml","META-INF/mods.toml");default->throw new IOException("加载器无效");};
   var metadata=candidates.stream().map(zip::getEntry).filter(Objects::nonNull).findFirst().orElseThrow(()->new IOException("缺少对应加载器元数据"));if(metadata.isDirectory()||metadata.getSize()<1||metadata.getSize()>1024*1024)throw new IOException("模组元数据超限");try(var in=zip.getInputStream(metadata)){byte[] bytes=in.readNBytes(1024*1024+1);if(bytes.length>1024*1024)throw new IOException("模组元数据超限");if(metadata.getName().endsWith(".json"))LauncherJson.read(bytes,1024*1024);}
  }catch(IOException invalid){throw new TaskFailure("MOD_JAR_INVALID","下载文件不是有效的对应加载器模组，实例原件保留");}
 }
 public Listing listing(String id,CancellationToken token)throws IOException{
  recoverPending(id,token);try(var lock=storage.lock(base(id).resolve("installations/.install.lock"))){var core=active(id);var snapshot=ModDirectorySnapshot.capture(storage,directory(id),token);var owned=owned(id,core);verifyOwned(owned,snapshot);var names=new HashSet<String>();names.add(SafeInstallPath.key(Path.of(ManagedModReceipt.NAME)));for(var e:owned.entries())names.add(SafeInstallPath.key(Path.of(e.fileName())));int manual=(int)snapshot.files().stream().filter(e->!names.contains(SafeInstallPath.key(Path.of(e.path())))).count();return new Listing(id,core.versionId(),loader(core),owned.loader(),owned.entries(),manual,storage.root().resolve(directory(id)).toString(),snapshot.fingerprint(),compatible(owned,core));}
 }
 public String verifyForLaunch(InstallationReceipt core,CancellationToken token)throws IOException{
  String id=core.instanceId();if(pending(id))throw new TaskFailure("MOD_RECOVERY_REQUIRED","实例有未恢复的模组事务，请重新连接并检查");var snapshot=ModDirectorySnapshot.capture(storage,directory(id),token);var owned=owned(id,core);if(!compatible(owned,core))throw new TaskFailure("MOD_LOADER_CHANGED","实例核心与受管模组不同，请先移除旧核心模组");verifyOwned(owned,snapshot);return snapshot.fingerprint();
 }
 private boolean pending(String id)throws IOException{Path journal=base(id).resolve("mods-transaction.json");if(!storage.exists(journal))return false;var node=LauncherJson.read(storage.read(journal,8192),8192);validateJournal(id,node);return "PREPARED".equals(node.path("phase").asText());}
 private static void validateJournal(String id,com.fasterxml.jackson.databind.JsonNode journal)throws IOException{
  try{ManagedModReceipt.keys(journal,Set.of("schemaVersion","instanceId","operationId","phase","hadOriginal","beforeSha256","afterSha256"));
   if(!id.equals(LauncherJson.text(journal,"instanceId",36))||!journal.path("schemaVersion").isInt()||journal.path("schemaVersion").intValue()!=1||!Set.of("PREPARED","COMMITTED","ROLLED_BACK").contains(LauncherJson.text(journal,"phase",16))||!journal.path("hadOriginal").isBoolean())throw new IOException();
   TaskRequest.uuid(LauncherJson.text(journal,"operationId",36));for(String key:List.of("beforeSha256","afterSha256"))if(!LauncherJson.text(journal,key,64).matches("[0-9a-f]{64}"))throw new IOException();
  }catch(IOException|RuntimeException invalid){throw new TaskFailure("MOD_RECOVERY_REQUIRED","模组事务记录无效，所有目录已保留");}
 }
 public void recoverPending(String id,CancellationToken token)throws IOException{
  if(!pending(id))return;try(var running=storage.lock(base(id).resolve(".running.lock"));var lock=storage.lock(base(id).resolve("installations/.install.lock"))){HistoricalProcessGuard.ensureIdle(storage,id);recoverLocked(id,token);}
 }
 private void recoverLocked(String id,CancellationToken token)throws IOException{
  Path path=base(id).resolve("mods-transaction.json");if(!storage.exists(path))return;var journal=LauncherJson.read(storage.read(path,8192),8192);validateJournal(id,journal);ManagedModReceipt.keys(journal,Set.of("schemaVersion","instanceId","operationId","phase","hadOriginal","beforeSha256","afterSha256"));
  String phase=LauncherJson.text(journal,"phase",16);if(!id.equals(LauncherJson.text(journal,"instanceId",36))||!journal.path("schemaVersion").isInt()||journal.path("schemaVersion").intValue()!=1||!Set.of("PREPARED","COMMITTED","ROLLED_BACK").contains(phase)||!journal.path("hadOriginal").isBoolean())throw new TaskFailure("MOD_RECOVERY_REQUIRED","模组事务记录无效，所有目录已保留");
  String operation=LauncherJson.text(journal,"operationId",36);TaskRequest.uuid(operation);String before=LauncherJson.text(journal,"beforeSha256",64),after=LauncherJson.text(journal,"afterSha256",64);if(!before.matches("[0-9a-f]{64}")||!after.matches("[0-9a-f]{64}"))throw new IOException("模组事务摘要无效");if(!phase.equals("PREPARED"))return;
  Path transaction=base(id).resolve("mod-transactions").resolve(operation),previous=transaction.resolve("previous"),discarded=transaction.resolve("discarded");boolean had=journal.path("hadOriginal").booleanValue();var current=ModDirectorySnapshot.capture(storage,directory(id),token);
  if(storage.exists(previous)){
   if(!had||!ModDirectorySnapshot.capture(storage,previous,token).fingerprint().equals(before))throw new TaskFailure("MOD_RECOVERY_REQUIRED","模组原始备份已变化，所有文件已保留");
   if(current.existed()){if(!current.fingerprint().equals(after))throw new TaskFailure("MOD_RECOVERY_REQUIRED","中断后模组目录又有变化，请保留并人工核对；未覆盖任何文件");storage.publishDirectory(directory(id),discarded);}
   storage.publishDirectory(previous,directory(id));
  }else if(!current.fingerprint().equals(before)){
   if(!had&&current.existed()&&current.fingerprint().equals(after))storage.publishDirectory(directory(id),discarded);else throw new TaskFailure("MOD_RECOVERY_REQUIRED","模组事务缺少可确认的原始目录，所有文件已保留");
  }
  if(!ModDirectorySnapshot.capture(storage,directory(id),token).fingerprint().equals(before))throw new TaskFailure("MOD_RECOVERY_REQUIRED","恢复后的模组目录与原摘要不同");var done=new LinkedHashMap<String,Object>();journal.fields().forEachRemaining(e->done.put(e.getKey(),e.getValue()));done.put("phase","ROLLED_BACK");storage.writeAtomically(path,LauncherJson.write(done));
 }
}
