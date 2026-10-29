package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
public final class ModLibrary {
 public record Problem(String entryId,String reason){}
 public record LibraryListing(List<ModLibraryEntry> entries,List<Problem> problems){public LibraryListing{entries=List.copyOf(entries);problems=List.copyOf(problems);}}
 private final LauncherStorage storage;private final DownloadStore store;private final Clock clock;
 public ModLibrary(Path root,DownloadStore store,Clock clock){storage=new LauncherStorage(root);this.store=store;this.clock=clock;}
 public ModLibraryEntry publish(ModProject project,ModFile file,TaskContext context)throws IOException{
  if(project.source()!=file.ref().source()||!project.projectId().equals(file.ref().projectId())||!file.downloadable()||file.downloadSpec()==null||!ModProtocol.ordinary(file.fileName()))throw new TaskFailure("INCOMPATIBLE","此文件不可下载到模组库");
  context.token().check();context.progress(TaskState.DOWNLOADING,0,file.size(),0,1L);var downloaded=new AtomicLong();var object=store.fetch(file.downloadSpec(),context.token(),n->{synchronized(downloaded){try{context.progress(TaskState.DOWNLOADING,downloaded.addAndGet(n),file.size(),0,1L);}catch(IOException error){throw new UncheckedIOException(error);}}});store.verify(object,context.token());
  String ext=file.fileName().toLowerCase(Locale.ROOT).endsWith(".litemod")?".litemod":".jar";UUID id=UUID.randomUUID();Path pending=Path.of("mod-library/.pending-"+id),destination=Path.of("mod-library/"+id),relative=destination.resolve("content"+ext);storage.ensureDirectory(pending);Path staged=pending.resolve("content"+ext);context.progress(TaskState.VERIFYING,downloaded.get(),file.size(),0,1L);storage.copyNew(object.relativeObject(),staged,context.token());verifyFile(staged,object.size(),object.sha256(),file.sourceDigest(),context.token());
  var entry=new ModLibraryEntry(id,context.id(),project.source(),project.projectId(),file.ref().versionId(),file.ref().fileKey(),project.title(),file.fileName(),file.gameVersions(),file.loaders(),file.sourceDigest(),object.size(),object.sha256(),clock.instant(),file.dependencies(),relative);storage.writeAtomically(pending.resolve("entry.json"),entry.bytes());context.progress(TaskState.VERIFYING,downloaded.get(),file.size(),1,1L);context.publish(()->storage.publishDirectory(pending,destination));return entry;
 }
 public LibraryListing listing()throws IOException{
  var entries=new ArrayList<ModLibraryEntry>();var problems=new ArrayList<Problem>();for(var path:storage.children(Path.of("mod-library"))){String id=path.getFileName().toString();if(id.startsWith(".pending-"))continue;try{TaskRequest.uuid(id);storage.requireDirectory(path);var entry=ModLibraryEntry.parse(storage.read(path.resolve("entry.json"),128*1024));if(!entry.id().toString().equals(id)||storage.size(entry.relativeFile())!=entry.size())throw new IOException("库内容与记录不匹配");entries.add(entry);}catch(IOException|RuntimeException error){problems.add(new Problem(id,"模组记录损坏或文件缺失，原件已保留"));}}entries.sort(Comparator.comparing(ModLibraryEntry::createdAt).reversed().thenComparing(e->e.id().toString()));return new LibraryListing(entries,problems);
 }
 public ModLibraryEntry verify(UUID id,CancellationToken token)throws IOException{
  token.check();Path record=Path.of("mod-library/"+id+"/entry.json");if(!storage.exists(record))throw new TaskFailure("RECORD_MISSING","模组记录缺失，已有文件未被改动");byte[] bytes=storage.read(record,128*1024);ModLibraryEntry entry;try{entry=ModLibraryEntry.parse(bytes);}catch(IOException error){throw new TaskFailure("RECORD_INVALID","模组记录损坏，原件已保留");}if(!entry.id().equals(id))throw new TaskFailure("RECORD_INVALID","模组记录 ID 不匹配，原件已保留");verifyFile(entry.relativeFile(),entry.size(),entry.sha256(),entry.sourceDigest(),token);return entry;
 }
 public Path filePath(ModLibraryEntry entry)throws IOException{if(storage.size(entry.relativeFile())!=entry.size())throw new IOException("模组库文件长度已变化");return storage.root().resolve(entry.relativeFile());}
 private void verifyFile(Path relative,long size,String sha256,FileDigest source,CancellationToken token)throws IOException{
  token.check();if(!storage.exists(relative))throw new TaskFailure("FILE_MISSING","模组文件缺失，已有文件未被改动");if(storage.size(relative)!=size)throw new TaskFailure("FILE_CHANGED","模组文件长度已变化，原件已保留");var sha=new FileDigest(FileDigest.Algorithm.SHA256,sha256).newDigest();var digest=source.newDigest();long total=0;try(var in=storage.openRead(relative)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){token.check();total+=n;if(total>size)throw new TaskFailure("FILE_CHANGED","模组内容长度已变化，原件已保留");sha.update(buffer,0,n);digest.update(buffer,0,n);}}if(total!=size||!HexFormat.of().formatHex(sha.digest()).equals(sha256)||!HexFormat.of().formatHex(digest.digest()).equals(source.hex()))throw new TaskFailure("FILE_CHANGED","模组内容摘要已变化，原件已保留");token.check();
 }
}
