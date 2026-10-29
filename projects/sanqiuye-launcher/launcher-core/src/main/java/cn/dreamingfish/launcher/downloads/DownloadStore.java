package cn.dreamingfish.launcher.downloads;
import cn.dreamingfish.launcher.storage.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongConsumer;
public final class DownloadStore {
 private final LauncherStorage storage;private final RemoteClient remote;
 private final java.util.concurrent.Semaphore fileSlots=new java.util.concurrent.Semaphore(4,true);
 public DownloadStore(Path root,RemoteClient remote){storage=new LauncherStorage(root);this.remote=Objects.requireNonNull(remote);}
 /** 缓存调用方刚从受控元数据接口读出的固定字节；与下载对象采用同样的摘要和原子发布规则。 */
 public VerifiedDownload rememberMetadata(DownloadSpec spec,byte[] bytes,CancellationToken token)throws IOException{
  token.check();if(!Set.of(RemoteService.MOJANG_META,RemoteService.FABRIC_META,RemoteService.QUILT_META).contains(spec.service()))throw new IllegalArgumentException("只能缓存固定元数据");
  if(bytes.length>spec.maxBytes()||spec.expectedSize()>=0&&bytes.length!=spec.expectedSize()||!HexFormat.of().formatHex(spec.digest().newDigest().digest(bytes)).equals(spec.digest().hex()))throw new TaskFailure("FILE_CHANGED","元数据长度或摘要不匹配");
  String sha=HexFormat.of().formatHex(new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest().digest(bytes));var result=new VerifiedDownload(Path.of("downloads/objects/"+sha),bytes.length,sha,spec.digest());
  Path index=Path.of("downloads/index/"+spec.digest().algorithm()+"-"+spec.digest().hex()+".json");if(storage.exists(index))return fetch(spec,token,n->{});
  try(var stage=storage.beginStagedWrite(Path.of("downloads/staging"))){stage.write(java.nio.ByteBuffer.wrap(bytes));token.check();try{stage.publishNew(result.relativeObject());}catch(FileAlreadyExistsException exists){verify(result,token);}}
  byte[] record=LauncherJson.write(Map.of("schemaVersion",1,"size",bytes.length,"sha256",sha));
  try(var stage=storage.beginStagedWrite(Path.of("downloads/index"))){stage.write(java.nio.ByteBuffer.wrap(record));token.check();try{stage.publishNew(index);}catch(FileAlreadyExistsException exists){if(!LauncherJson.read(storage.read(index,4096),4096).equals(LauncherJson.read(record,4096)))throw new TaskFailure("CACHE_CHANGED","现有元数据索引冲突，原件已保留");verify(result,token);}}
  token.check();return result;
 }
 public VerifiedDownload fetch(DownloadSpec spec,CancellationToken token,LongConsumer progress)throws IOException{
  try{while(!fileSlots.tryAcquire(50,java.util.concurrent.TimeUnit.MILLISECONDS))token.check();}catch(InterruptedException error){Thread.currentThread().interrupt();throw new InterruptedIOException("等待下载槽时已取消");}
  try{return fetchObject(spec,token,progress);}finally{fileSlots.release();}
 }
 private VerifiedDownload fetchObject(DownloadSpec spec,CancellationToken token,LongConsumer progress)throws IOException{
  token.check();Path index=Path.of("downloads/index/"+spec.digest().algorithm()+"-"+spec.digest().hex()+".json");
  if(storage.exists(index)){
   var record=LauncherJson.read(storage.read(index,4096),4096);
   var keys=new HashSet<String>();record.fieldNames().forEachRemaining(keys::add);
   if(!keys.equals(Set.of("schemaVersion","size","sha256"))||!record.path("schemaVersion").isInt()||record.path("schemaVersion").intValue()!=1||!record.path("size").isIntegralNumber()||!record.path("size").canConvertToLong())throw new TaskFailure("CACHE_INVALID","缓存索引格式无效");
   String sha=record.path("sha256").asText();long size=record.path("size").longValue();if(!sha.matches("[0-9a-f]{64}")||size<0||size>spec.maxBytes()||spec.expectedSize()>=0&&size!=spec.expectedSize())throw new TaskFailure("CACHE_INVALID","缓存索引长度无效");
   var result=new VerifiedDownload(Path.of("downloads/objects/"+sha),size,sha,spec.digest());verify(result,token);return result;
  }
  var source=spec.digest().newDigest();var sha256=new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest();long[] count={0};
  try(var stage=storage.beginStagedWrite(Path.of("downloads/staging"))){
   remote.get(new RemoteRequest(spec.service(),spec.uri(),Map.of()),spec.maxBytes(),token,bytes->{token.check();int n=bytes.remaining();if(n>spec.maxBytes()-count[0])throw new TaskFailure("FILE_TOO_LARGE","文件超过上限");source.update(bytes.duplicate());sha256.update(bytes.duplicate());stage.write(bytes);count[0]+=n;progress.accept(n);});
   token.check();String sourceHash=HexFormat.of().formatHex(source.digest()),sha=HexFormat.of().formatHex(sha256.digest());
   if(spec.expectedSize()>=0&&spec.expectedSize()!=count[0]||!sourceHash.equals(spec.digest().hex()))throw new TaskFailure("FILE_CHANGED","文件长度或来源摘要不匹配");
   var result=new VerifiedDownload(Path.of("downloads/objects/"+sha),count[0],sha,spec.digest());
   try{stage.publishNew(result.relativeObject());}catch(FileAlreadyExistsException existing){verify(result,token);}
   token.check();byte[] indexBytes=LauncherJson.write(Map.of("schemaVersion",1,"size",count[0],"sha256",sha));
   try(var indexStage=storage.beginStagedWrite(Path.of("downloads/index"))){indexStage.write(java.nio.ByteBuffer.wrap(indexBytes));try{indexStage.publishNew(index);}catch(FileAlreadyExistsException existing){if(!LauncherJson.read(storage.read(index,4096),4096).equals(LauncherJson.read(indexBytes,4096)))throw new TaskFailure("CACHE_CHANGED","现有缓存索引冲突，原文件已保留");verify(result,token);}}
   token.check();return result;
  }
 }
 public void verify(VerifiedDownload value,CancellationToken token)throws IOException{
  if(!value.sha256().matches("[0-9a-f]{64}")||!value.relativeObject().equals(Path.of("downloads/objects/"+value.sha256())))throw new TaskFailure("CACHE_INVALID","对象路径无效");
  if(!storage.exists(value.relativeObject()))throw new TaskFailure("CACHE_MISSING","缓存文件缺失，未改动已有文件");
  if(storage.size(value.relativeObject())!=value.size())throw new TaskFailure("CACHE_CHANGED","缓存长度已变化");
  var source=value.sourceDigest().newDigest();var sha=new FileDigest(FileDigest.Algorithm.SHA256,value.sha256()).newDigest();long count=0;
  try(var in=storage.openRead(value.relativeObject())){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){token.check();count+=n;if(count>value.size())throw new TaskFailure("CACHE_CHANGED","缓存长度已变化");source.update(b,0,n);sha.update(b,0,n);}}
  token.check();if(count!=value.size()||!HexFormat.of().formatHex(source.digest()).equals(value.sourceDigest().hex())||!HexFormat.of().formatHex(sha.digest()).equals(value.sha256()))throw new TaskFailure("CACHE_CHANGED","缓存摘要已变化，原文件已保留");
 }
 public byte[] read(VerifiedDownload value,int limit,CancellationToken token)throws IOException{
  token.check();byte[] bytes=storage.read(value.relativeObject(),limit);var digest=new FileDigest(FileDigest.Algorithm.SHA256,value.sha256()).newDigest();if(bytes.length!=value.size()||!HexFormat.of().formatHex(digest.digest(bytes)).equals(value.sha256()))throw new TaskFailure("CACHE_CHANGED","已下载元数据被改动");token.check();return bytes;
 }
}
