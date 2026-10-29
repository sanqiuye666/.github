package cn.dreamingfish.launcher.downloads;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class TaskManagerTest {
 @TempDir Path root;static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"),ZoneOffset.UTC);
 static TaskRequest request(){return new TaskRequest(TaskKind.INSTALLATION_VERIFY,Map.of("instanceId",UUID.randomUUID().toString()));}
 static void await(CountDownLatch latch,CancellationToken token)throws IOException{try{while(!latch.await(10,TimeUnit.MILLISECONDS))token.check();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}}
 static TaskSnapshot terminal(TaskManager manager,UUID id)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(System.nanoTime()<end){var got=manager.get(id);if(got.state().terminal())return got;Thread.sleep(5);}throw new AssertionError("task did not end");}
 @Test void boundedQueueAndQueuedCancellationDoesNotRun()throws Exception {
  var started=new CountDownLatch(2);var release=new CountDownLatch(1);var calls=new AtomicInteger();
  try(var manager=new TaskManager(root,CLOCK,Map.of(TaskKind.INSTALLATION_VERIFY,(r,c)->{calls.incrementAndGet();started.countDown();await(release,c.token());c.publish(()->{});}))) {
   manager.submit(request());manager.submit(request());assertTrue(started.await(2,TimeUnit.SECONDS));var queued=new ArrayList<TaskSnapshot>();for(int i=0;i<32;i++)queued.add(manager.submit(request()));
   assertThrows(IOException.class,()->manager.submit(request()));manager.cancel(queued.getFirst().id());release.countDown();for(var task:queued)terminal(manager,task.id());assertEquals(33,calls.get());
  }
 }
 @Test void cancelAtPublishBoundaryAndRetryKeepsInputs()throws Exception {
  var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var published=new AtomicBoolean();
  try(var manager=new TaskManager(root,CLOCK,Map.of(TaskKind.INSTALLATION_VERIFY,(r,c)->{ready.countDown();await(release,c.token());c.publish(()->published.set(true));}))) {
   var initial=manager.submit(request());assertTrue(ready.await(2,TimeUnit.SECONDS));assertEquals(TaskState.CANCELLED,manager.cancel(initial.id()).state());release.countDown();assertEquals(TaskState.CANCELLED,terminal(manager,initial.id()).state());assertFalse(published.get());
   var retry=manager.retry(initial.id());assertNotEquals(initial.id(),retry.id());assertEquals(initial.id(),retry.retryOf());assertEquals(initial.inputs(),retry.inputs());assertEquals(TaskState.COMPLETED,terminal(manager,retry.id()).state());assertTrue(published.get());assertEquals(TaskState.COMPLETED,manager.cancel(retry.id()).state());assertThrows(IOException.class,()->manager.retry(retry.id()));
  }
 }
 @Test void handlerReturnWithoutPublicationIsFailureAndRecordFailureDoesNotPublish()throws Exception {
  try(var manager=new TaskManager(root,CLOCK,Map.of(TaskKind.INSTALLATION_VERIFY,(r,c)->{}))){assertEquals("NO_RESULT",terminal(manager,manager.submit(request()).id()).errorCode());}
  var gate=new CountDownLatch(1);var ready=new CountDownLatch(1);var published=new AtomicBoolean();
  try(var manager=new TaskManager(root,CLOCK,Map.of(TaskKind.INSTALLATION_VERIFY,(r,c)->{ready.countDown();await(gate,c.token());c.publish(()->published.set(true));}))){var task=manager.submit(request());assertTrue(ready.await(2,TimeUnit.SECONDS));Path file=root.resolve("tasks/"+task.id()+".json");Files.delete(file);Files.createDirectory(file);gate.countDown();assertEquals(TaskState.FAILED,terminal(manager,task.id()).state());assertFalse(published.get());}
 }
 @Test void committedResultWithRecordFailureNeverBecomesCancelled()throws Exception {
  var published=new AtomicBoolean();
  try(var manager=new TaskManager(root,CLOCK,Map.of(TaskKind.INSTALLATION_VERIFY,(r,c)->c.publish(()->{published.set(true);Path file=root.resolve("tasks/"+c.id()+".json");Files.delete(file);Files.createDirectory(file);})))){
   var result=terminal(manager,manager.submit(request()).id());assertTrue(published.get());assertEquals(TaskState.COMPLETED,result.state());assertEquals("IO_ERROR",result.errorCode());assertEquals(TaskState.COMPLETED,manager.cancel(result.id()).state());assertTrue(result.message().contains("结果已提交"));
  }
 }
}
