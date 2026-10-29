package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ModLibraryTest {
 @TempDir Path root;
 static class Context implements TaskContext{final UUID id=UUID.randomUUID();final CancellationToken token=new CancellationToken();boolean cancel;public UUID id(){return id;}public CancellationToken token(){return token;}public void progress(TaskState s,long b,Long t,long f,Long n)throws IOException{token.check();}public void publish(IoAction action)throws IOException{if(cancel)token.cancel();token.check();action.run();}}
 static ModProject project(){return new ModProject(ModSource.MODRINTH,"project","示例模组",null,"独立协议 fixture",null);}
 static ModFile file(String name,byte[] bytes){var digest=new FileDigest(FileDigest.Algorithm.SHA256,HexFormat.of().formatHex(new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest().digest(bytes)));return new ModFile(new ModFileRef(ModSource.MODRINTH,"project","version",ModProtocol.fileKey(name,digest,"mod")),"demo",name,List.of("1.21.1"),List.of("fabric"),"release",Instant.parse("2026-01-01T00:00:00Z"),(long)bytes.length,digest,List.of(new ModDependency(ModDependency.Kind.REQUIRED,"dependency",null,null)),true,null,new DownloadSpec(RemoteService.MODRINTH_CDN,URI.create("https://cdn.modrinth.com/data/project/mod.jar"),bytes.length,1024,digest,"fixture"));}
 @Test void publishedLibraryReopensAndNeverUsesTheOriginalNameAsAPath()throws Exception{
  byte[] bytes="actual-mod-content".getBytes();var store=new DownloadStore(root,(r,l,t,s)->s.accept(ByteBuffer.wrap(bytes)));var library=new ModLibrary(root,store,Clock.systemUTC());var file=file("../../not-a-target.jar",bytes);var first=library.publish(project(),file,new Context());var second=library.publish(project(),file,new Context());assertNotEquals(first.id(),second.id());assertFalse(first.relativeFile().toString().contains(".."));assertArrayEquals(bytes,Files.readAllBytes(root.resolve(first.relativeFile())));assertEquals(first,new ModLibrary(root,store,Clock.systemUTC()).verify(first.id(),new CancellationToken()));assertEquals(2,library.listing().entries().size());
  byte[] changed=bytes.clone();changed[0]^=1;Files.write(root.resolve(first.relativeFile()),changed);assertThrows(IOException.class,()->library.verify(first.id(),new CancellationToken()));assertArrayEquals(changed,Files.readAllBytes(root.resolve(first.relativeFile())));
  var cancel=new Context();cancel.cancel=true;assertThrows(InterruptedIOException.class,()->library.publish(project(),file,cancel));assertEquals(2,library.listing().entries().size());
  Path broken=Files.createDirectories(root.resolve("mod-library/"+UUID.randomUUID())).resolve("entry.json");Files.writeString(broken,"broken");assertEquals(2,library.listing().entries().size());assertEquals(1,library.listing().problems().size());assertEquals("broken",Files.readString(broken));
 }
 @Test void failedHashDoesNotPublishOrTouchGame()throws Exception{
  Path game=Files.createDirectories(root.resolve("existing-game")).resolve("options.txt");byte[] before="player-config".getBytes();Files.write(game,before);var library=new ModLibrary(root,new DownloadStore(root,(r,l,t,s)->s.accept(ByteBuffer.wrap("wrong".getBytes()))),Clock.systemUTC());assertThrows(IOException.class,()->library.publish(project(),file("mod.jar","right".getBytes()),new Context()));assertEquals(0,library.listing().entries().size());assertArrayEquals(before,Files.readAllBytes(game));
 }
}
