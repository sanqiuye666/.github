package cn.dreamingfish.launcher.downloads;
import java.io.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
public final class TaskManager implements AutoCloseable {
 @FunctionalInterface public interface TaskHandler {void run(TaskRequest request,TaskContext context)throws IOException;}
 public record TaskListing(List<TaskSnapshot> tasks,List<TaskRepository.Problem> problems,int total){}
 private final TaskRepository repository;private final Clock clock;private final Map<TaskKind,TaskHandler> handlers;private final Map<UUID,Entry> entries=new ConcurrentHashMap<>();
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),Thread.ofPlatform().daemon(true).name("sqy-task-",0).factory());
 private boolean closed;
 private static final class Entry {volatile TaskSnapshot snapshot;final CancellationToken token=new CancellationToken();long lastWrite;boolean published;Future<?> future;Entry(TaskSnapshot s){snapshot=s;}}
 public TaskManager(Path root,Clock clock,Map<TaskKind,TaskHandler> handlers)throws IOException{this.clock=clock;this.handlers=Map.copyOf(handlers);repository=new TaskRepository(root,clock);repository.snapshots().forEach((id,s)->entries.put(id,new Entry(s)));}
 public synchronized TaskSnapshot submit(TaskRequest request)throws IOException{return submit(request,null);}
 private TaskSnapshot submit(TaskRequest request,UUID retryOf)throws IOException{
  if(closed)throw new TaskFailure("BUSY","任务队列已关闭");if(!handlers.containsKey(request.kind()))throw new IllegalArgumentException("任务能力待接入");if(workers.getActiveCount()>=2&&workers.getQueue().remainingCapacity()==0)throw new TaskFailure("QUEUE_FULL","任务队列已满");
  var now=clock.instant();var snapshot=new TaskSnapshot(UUID.randomUUID(),request.kind(),TaskState.QUEUED,request.inputs(),now,now,0,null,0,null,null,null,retryOf);repository.save(snapshot);var entry=new Entry(snapshot);entries.put(snapshot.id(),entry);
  try{entry.future=workers.submit(()->run(entry,request));}catch(RejectedExecutionException error){fail(entry,"QUEUE_FULL","任务队列已满");throw new TaskFailure("QUEUE_FULL","任务队列已满");}return snapshot;
 }
 public TaskSnapshot get(UUID id)throws IOException{var e=entries.get(id);if(e==null)throw new FileNotFoundException("任务不存在");return e.snapshot;}
 public TaskListing listing(int offset,int limit){if(offset<0||limit<1||limit>100)throw new IllegalArgumentException("分页范围无效");var all=entries.values().stream().map(e->e.snapshot).sorted(Comparator.comparing((TaskSnapshot s)->s.state().terminal()).thenComparing(TaskSnapshot::createdAt,Comparator.reverseOrder()).thenComparing(s->s.id().toString())).toList();return new TaskListing(all.stream().skip(offset).limit(limit).toList(),repository.problems(),all.size());}
 public TaskSnapshot cancel(UUID id)throws IOException{
  var e=entries.get(id);if(e==null)throw new FileNotFoundException("任务不存在");synchronized(e){if(e.snapshot.state().terminal())return e.snapshot;e.token.cancel();boolean queued=e.snapshot.state()==TaskState.QUEUED;set(e,copy(e,TaskState.CANCELLED,"CANCELLED","任务已取消"),true);if(e.future!=null){e.future.cancel(true);if(queued)workers.remove((Runnable)e.future);}return e.snapshot;}
 }
 public synchronized TaskSnapshot retry(UUID id)throws IOException{var old=get(id);if(!Set.of(TaskState.FAILED,TaskState.CANCELLED,TaskState.INTERRUPTED).contains(old.state()))throw new TaskFailure("NOT_RETRYABLE","只有失败、取消或中断的任务可以重试");return submit(new TaskRequest(old.kind(),old.inputs()),old.id());}
 private void run(Entry e,TaskRequest request){
  try{synchronized(e){if(e.snapshot.state().terminal())return;set(e,copy(e,TaskState.RESOLVING,null,null),true);}var context=new Context(e);handlers.get(request.kind()).run(request,context);synchronized(e){if(!e.snapshot.state().terminal())fail(e,"NO_RESULT","任务未发布任何结果");}}
  catch(Exception error){synchronized(e){if(!e.published&&!e.snapshot.state().terminal())fail(e,e.token.isCancelled()||error instanceof InterruptedIOException?"CANCELLED":error instanceof TaskFailure failure?failure.code():error instanceof RemoteHttpException remote?remote.code():error instanceof cn.dreamingfish.launcher.storage.LauncherBusyException?"BUSY":"IO_ERROR",e.token.isCancelled()?"任务已取消":error instanceof TaskFailure||error instanceof RemoteHttpException?error.getMessage():error instanceof cn.dreamingfish.launcher.storage.LauncherBusyException?"目录正在被其他任务或进程使用，请稍后重试":"任务未完成，请检查网络和文件记录后重试");}}
 }
 private TaskSnapshot copy(Entry e,TaskState state,String code,String message){var s=e.snapshot;return new TaskSnapshot(s.id(),s.kind(),state,s.inputs(),s.createdAt(),clock.instant(),s.downloadedBytes(),s.totalBytes(),s.completedFiles(),s.totalFiles(),code,message,s.retryOf());}
 private void fail(Entry e,String code,String message){var s=copy(e,code.equals("CANCELLED")?TaskState.CANCELLED:TaskState.FAILED,code,message);try{repository.save(s);e.snapshot=s;}catch(IOException disk){e.snapshot=copy(e,s.state(),"IO_ERROR","任务记录保存失败，结果未发布");}}
 private void set(Entry e,TaskSnapshot s,boolean durable)throws IOException{if(durable)repository.save(s);e.snapshot=s;if(durable)e.lastWrite=System.nanoTime();}
 private final class Context implements TaskContext {
  private final Entry e;Context(Entry e){this.e=e;}public UUID id(){return e.snapshot.id();}public CancellationToken token(){return e.token;}
  public void progress(TaskState state,long bytes,Long total,long files,Long count)throws IOException{synchronized(e){e.token.check();if(e.snapshot.state().terminal())throw new IOException("任务已结束");if(state.terminal()||state==TaskState.QUEUED)throw new IllegalArgumentException("进度状态无效");var s=e.snapshot;if(bytes<s.downloadedBytes()||files<s.completedFiles())throw new IllegalArgumentException("进度不能回退");boolean durable=s.state()!=state||System.nanoTime()-e.lastWrite>=1_000_000_000L;set(e,new TaskSnapshot(s.id(),s.kind(),state,s.inputs(),s.createdAt(),clock.instant(),bytes,total,files,count,null,null,s.retryOf()),durable);}}
  public void publish(IoAction action)throws IOException{synchronized(e){e.token.check();if(e.snapshot.state().terminal())throw new IOException("任务已结束");set(e,copy(e,TaskState.VERIFYING,null,"正在提交结果"),true);e.token.check();action.run();e.published=true;var completed=copy(e,TaskState.COMPLETED,null,"任务已完成");try{repository.save(completed);e.snapshot=completed;}catch(IOException disk){e.snapshot=copy(e,TaskState.COMPLETED,"IO_ERROR","结果已提交，任务完成记录保存失败");throw disk;}}}
 }
 @Override public void close()throws IOException{
  synchronized(this){if(closed)return;closed=true;}
  IOException failure=null;for(var e:entries.values())if(!e.snapshot.state().terminal())try{cancel(e.snapshot.id());}catch(IOException error){failure=error;e.token.cancel();}
  workers.shutdownNow();boolean interrupted=false;while(!workers.isTerminated())try{workers.awaitTermination(1,TimeUnit.SECONDS);}catch(InterruptedException error){interrupted=true;}try{repository.close();}finally{if(interrupted)Thread.currentThread().interrupt();}if(failure!=null)throw failure;
 }
}
