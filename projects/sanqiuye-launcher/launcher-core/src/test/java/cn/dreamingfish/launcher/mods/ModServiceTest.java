package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class ModServiceTest {
 @TempDir Path root;
 @Test void pinsAnExplicitCompatibleFileAndRejectsChangesBeforeDownloading()throws Exception{
  byte[] bytes="mod".getBytes();var current=new AtomicReference<>(ModLibraryTest.file("mod.jar",bytes));var calls=new AtomicInteger();var provider=new ModProvider(){public ModSource source(){return ModSource.MODRINTH;}public SourceStatus status(CancellationToken token){return new SourceStatus(source(),"AVAILABLE",null,List.of("fabric"),null);}public ModPage search(ModQuery q,CancellationToken token){return new ModPage(List.of(ModLibraryTest.project()),0,20,1,1,false);}public ModProject project(String id,CancellationToken token){return ModLibraryTest.project();}public ModFilesPage files(String id,ModFileQuery q,CancellationToken token){return new ModFilesPage(List.of(current.get()),0,20,1,1,false);}public ModFile resolve(ModFileRef ref,CancellationToken token){return current.get();}};
  var library=new ModLibrary(root,new DownloadStore(root,(r,l,t,s)->{calls.incrementAndGet();s.accept(ByteBuffer.wrap(bytes));}),Clock.systemUTC());var service=new ModService(Map.of(ModSource.MODRINTH,provider),library);var ref=current.get().ref();
  assertThrows(IOException.class,()->service.prepareDownload(ref,"","fabric",new CancellationToken()));assertThrows(IOException.class,()->service.prepareDownload(ref,"1.0","fabric",new CancellationToken()));
  var request=service.prepareDownload(ref,"1.21.1","fabric",new CancellationToken());assertEquals("1.21.1",request.inputs().get("gameVersion"));assertFalse(request.inputs().toString().contains("https://"));assertEquals(0,calls.get());
  current.set(ModLibraryTest.file("renamed.jar",bytes));assertEquals("FILE_CHANGED",assertThrows(TaskFailure.class,()->service.runDownload(request,new ModLibraryTest.Context())).code());assertEquals(0,calls.get());
  try(var tasks=new TaskManager(root,Clock.systemUTC(),Map.of(TaskKind.MOD_DOWNLOAD,service::runDownload))){
   var failed=terminal(tasks,tasks.submit(request).id());assertEquals(TaskState.FAILED,failed.state());assertEquals("FILE_CHANGED",failed.errorCode());assertEquals(0,calls.get());current.set(ModLibraryTest.file("mod.jar",bytes));var retry=terminal(tasks,tasks.retry(failed.id()).id());assertEquals(TaskState.COMPLETED,retry.state());assertEquals(request.inputs(),retry.inputs());assertEquals(failed.id(),retry.retryOf());
  }assertEquals(1,calls.get());assertEquals(1,library.listing().entries().getFirst().dependencies().size());
  assertThrows(IOException.class,()->service.prepareDownload(new ModFileRef(ModSource.CURSEFORGE,"project","version",ref.fileKey()),"1.21.1","fabric",new CancellationToken()));
 }
 static TaskSnapshot terminal(TaskManager tasks,UUID id)throws Exception{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);while(System.nanoTime()<end){var task=tasks.get(id);if(task.state().terminal())return task;Thread.sleep(5);}throw new AssertionError("task did not finish");}
}
