package cn.dreamingfish.launcher.resources;
import cn.dreamingfish.launcher.downloads.CancellationToken;
import cn.dreamingfish.launcher.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.io.*;import java.util.*;import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class ResourceStoreTest {
 @TempDir Path root;String id=UUID.randomUUID().toString();CancellationToken token=new CancellationToken();
 ResourceStore store;
 @BeforeEach void setup()throws Exception {instance(id);store=new ResourceStore(root);}
 void instance(String key)throws Exception {Path base=root.resolve("instances/"+key);Files.createDirectories(base.resolve(".minecraft"));Files.writeString(base.resolve("instance.json"),"{\"schemaVersion\":1,\"id\":\""+key+"\",\"name\":\"资源测试\",\"gameVersion\":\"1.20.6\",\"loader\":\"VANILLA\",\"createdAt\":\"2026-10-27T00:00:00Z\",\"state\":\"PREPARED\"}");}
 Path zip(String name,String entry,String text)throws Exception {Path file=root.resolve(name);try(var z=new ZipOutputStream(Files.newOutputStream(file))){z.putNextEntry(new ZipEntry(entry));z.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));z.closeEntry();}return file;}
 @Test void importsFourKindsIntoSeparateGameDirectoriesWithoutChangingSources()throws Exception {
  var pack=zip("纹理.zip","pack.mcmeta","{\"pack\":{\"pack_format\":32,\"description\":\"测试资源\"}}");var shader=zip("光影.zip","shaders/test.fsh","void main(){}");var replay=zip("录像.mcpr","metaData.json","{\"mcversion\":\"1.20.6\",\"duration\":100}");
  try(var z=new ZipOutputStream(Files.newOutputStream(replay))){for(var name:List.of("metaData.json","recording.tmcpr")){z.putNextEntry(new ZipEntry(name));z.write(name.equals("metaData.json")?"{\"mcversion\":\"1.20.6\",\"duration\":100}".getBytes():new byte[]{0});z.closeEntry();}}
  Path projection=root.resolve("小屋.litematic");try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(projection)))){out.writeByte(10);out.writeUTF("");out.writeByte(0);}
  for(var pair:Map.of(ResourceKind.RESOURCEPACKS,pack,ResourceKind.SHADERS,shader,ResourceKind.RECORDINGS,replay,ResourceKind.SCHEMATICS,projection).entrySet()){
   byte[] before=Files.readAllBytes(pair.getValue());var result=store.importFile(id,pair.getKey(),pair.getValue(),token);assertEquals(1,result.entries().size());assertArrayEquals(before,Files.readAllBytes(pair.getValue()));assertArrayEquals(before,Files.readAllBytes(Path.of(result.directory()).resolve(pair.getValue().getFileName())));
  }
 }
 @Test void conflictAndUnsafeNamesNeverOverwrite()throws Exception {
  Path file=zip("same.zip","pack.mcmeta","{\"pack\":{\"pack_format\":32,\"description\":\"a\"}}");var list=store.importFile(id,ResourceKind.RESOURCEPACKS,file,token);var entry=list.entries().getFirst();assertThrows(IOException.class,()->store.importFile(id,ResourceKind.RESOURCEPACKS,file,token));
  for(String name:List.of("../escape.zip","CON.zip","x.zip:secret","bad/child.zip","x.zip "))assertThrows(IllegalArgumentException.class,()->store.change(id,ResourceKind.RESOURCEPACKS,entry.name(),entry.revision(),"COPY",name,token));assertEquals(1,store.listing(id,ResourceKind.RESOURCEPACKS).entries().size());
 }
 @Test void removeCreatesVerifiedBackupAndRestoreRefusesCollision()throws Exception {
  Path file=zip("back.zip","pack.mcmeta","{\"pack\":{\"pack_format\":32,\"description\":\"a\"}}");var entry=store.importFile(id,ResourceKind.RESOURCEPACKS,file,token).entries().getFirst();var removed=store.change(id,ResourceKind.RESOURCEPACKS,entry.name(),entry.revision(),"REMOVE",null,token);assertTrue(removed.entries().isEmpty());assertEquals(1,removed.backups().size());
  var restored=store.restore(id,ResourceKind.RESOURCEPACKS,removed.backups().getFirst().id(),token);assertEquals(1,restored.entries().size());assertArrayEquals(Files.readAllBytes(file),Files.readAllBytes(Path.of(restored.directory()).resolve(entry.name())));assertThrows(IOException.class,()->store.restore(id,ResourceKind.RESOURCEPACKS,removed.backups().getFirst().id(),token));
 }
 @Test void copyRenameAndOtherInstanceAreIndependent()throws Exception {
  String other=UUID.randomUUID().toString();instance(other);Path file=zip("first.zip","pack.mcmeta","{\"pack\":{\"pack_format\":32,\"description\":\"a\"}}");var e=store.importFile(id,ResourceKind.RESOURCEPACKS,file,token).entries().getFirst();store.change(id,ResourceKind.RESOURCEPACKS,e.name(),e.revision(),"COPY","copy.zip",token);var renamed=store.change(id,ResourceKind.RESOURCEPACKS,e.name(),e.revision(),"RENAME","renamed.zip",token);assertEquals(Set.of("copy.zip","renamed.zip"),new HashSet<>(renamed.entries().stream().map(ResourceStore.Entry::name).toList()));assertTrue(store.listing(other,ResourceKind.RESOURCEPACKS).entries().isEmpty());assertEquals(1,renamed.backups().size());
 }
 @Test void locksAndChangedRevisionProtectCurrentFile()throws Exception {
  Path file=zip("safe.zip","pack.mcmeta","{\"pack\":{\"pack_format\":32,\"description\":\"a\"}}");var e=store.importFile(id,ResourceKind.RESOURCEPACKS,file,token).entries().getFirst();var storage=new LauncherStorage(root);try(var lock=storage.lock(Path.of("instances",id,".running.lock"))){assertThrows(IOException.class,()->store.change(id,ResourceKind.RESOURCEPACKS,e.name(),e.revision(),"REMOVE",null,token));}
  assertThrows(IOException.class,()->store.change(id,ResourceKind.RESOURCEPACKS,e.name(),"changed","REMOVE",null,token));assertEquals(1,store.listing(id,ResourceKind.RESOURCEPACKS).entries().size());
 }
 @Test void wrongContainerCancelledImportAndUnknownInstanceDoNotPublish()throws Exception {
  Path bad=root.resolve("bad.zip");Files.writeString(bad,"not a ZIP");assertThrows(IOException.class,()->store.importFile(id,ResourceKind.RESOURCEPACKS,bad,token));var cancelled=new CancellationToken();cancelled.cancel();assertThrows(IOException.class,()->store.importFile(id,ResourceKind.RESOURCEPACKS,bad,cancelled));assertThrows(IOException.class,()->store.listing(UUID.randomUUID().toString(),ResourceKind.RESOURCEPACKS));assertTrue(store.listing(id,ResourceKind.RESOURCEPACKS).entries().isEmpty());
 }
 @Test void corruptBackupAndZipTraversalAreRejected()throws Exception {
  Path file=zip("保留.zip","pack.mcmeta","{\"pack\":{\"pack_format\":32}}");var e=store.importFile(id,ResourceKind.RESOURCEPACKS,file,token).entries().getFirst();var removed=store.change(id,ResourceKind.RESOURCEPACKS,e.name(),e.revision(),"REMOVE",null,token);var backup=removed.backups().getFirst();Files.writeString(root.resolve("instances/"+id+"/resource-backups/"+backup.id()+"/original"),"tampered");assertThrows(IOException.class,()->store.restore(id,ResourceKind.RESOURCEPACKS,backup.id(),token));assertTrue(store.listing(id,ResourceKind.RESOURCEPACKS).entries().isEmpty());
  Path traversal=zip("危险.zip","../escape.txt","x");assertThrows(IOException.class,()->store.importFile(id,ResourceKind.RESOURCEPACKS,traversal,token));assertFalse(Files.exists(root.resolve("escape.txt")));
 }
 @Test void historicalLiveProcessBlocksWritesEvenWhenOsLockIsFree()throws Exception {
  Path file=zip("idle.zip","pack.mcmeta","{\"pack\":{\"pack_format\":32}}");var e=store.importFile(id,ResourceKind.RESOURCEPACKS,file,token).entries().getFirst();String run=UUID.randomUUID().toString();Path history=root.resolve("instances/"+id+"/launches/"+run+"/launch.json");Files.createDirectories(history.getParent());Files.write(history,LauncherJson.write(Map.of("schemaVersion",1,"id",run,"instanceId",id,"state","RUNNING","pid",ProcessHandle.current().pid(),"processStartedAt",ProcessHandle.current().info().startInstant().orElseThrow().toString())));assertThrows(IOException.class,()->store.change(id,ResourceKind.RESOURCEPACKS,e.name(),e.revision(),"REMOVE",null,token));assertEquals(1,store.listing(id,ResourceKind.RESOURCEPACKS).entries().size());
 }

 @Test void copyAndRestoreAtCapacityFailBeforePublishing()throws Exception {
  Path source=zip("capacity.zip","pack.mcmeta","{\"pack\":{\"pack_format\":32}}");var original=store.importFile(id,ResourceKind.RESOURCEPACKS,source,token).entries().getFirst();var removed=store.change(id,ResourceKind.RESOURCEPACKS,original.name(),original.revision(),"REMOVE",null,token);Path dir=Path.of(removed.directory());
  for(int i=0;i<1000;i++)Files.writeString(dir.resolve("item-"+i+".zip"),"untouched");var entry=store.listing(id,ResourceKind.RESOURCEPACKS).entries().getFirst();assertThrows(IOException.class,()->store.change(id,ResourceKind.RESOURCEPACKS,entry.name(),entry.revision(),"COPY","overflow.zip",token));assertFalse(Files.exists(dir.resolve("overflow.zip")));assertEquals(1000,store.listing(id,ResourceKind.RESOURCEPACKS).entries().size());assertThrows(IOException.class,()->store.restore(id,ResourceKind.RESOURCEPACKS,removed.backups().getFirst().id(),token));assertFalse(Files.exists(dir.resolve(original.name())));assertEquals(1000,store.listing(id,ResourceKind.RESOURCEPACKS).entries().size());
 }
 @Test void malformedBackupDateIsReportedWithoutHidingHealthyEntries()throws Exception {
  Path source=zip("date.zip","pack.mcmeta","{\"pack\":{\"pack_format\":32}}");var entry=store.importFile(id,ResourceKind.RESOURCEPACKS,source,token).entries().getFirst();var removed=store.change(id,ResourceKind.RESOURCEPACKS,entry.name(),entry.revision(),"REMOVE",null,token);store.importFile(id,ResourceKind.RESOURCEPACKS,source,token);
  Path record=root.resolve("instances/"+id+"/resource-backups/"+removed.backups().getFirst().id()+"/backup.json");var text=Files.readString(record);Files.writeString(record,text.replace(removed.backups().getFirst().createdAt(),"bad-date"));var result=store.listing(id,ResourceKind.RESOURCEPACKS);assertEquals(1,result.entries().size());assertEquals(1,result.problems().size());
 }
}
