package cn.dreamingfish.launcher.downloads;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongConsumer;
public final class DownloadBatch implements AutoCloseable {
 private final DownloadStore store;private final ThreadPoolExecutor workers;private final Semaphore slots=new Semaphore(8,true);
 public DownloadBatch(DownloadStore store,int parallelism){if(parallelism<1||parallelism>4)throw new IllegalArgumentException("共享下载槽为 1–4");this.store=store;workers=new ThreadPoolExecutor(parallelism,parallelism,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),Thread.ofPlatform().daemon(true).name("sqy-download-",0).factory()){@Override protected void afterExecute(Runnable r,Throwable t){try{super.afterExecute(r,t);}finally{slots.release();}}};}
 public List<VerifiedDownload> fetch(List<DownloadSpec> specs,CancellationToken token,LongConsumer progress)throws IOException{
  var child=new CancellationToken();var pending=new HashSet<Future<Item>>();var completion=new ExecutorCompletionService<Item>(workers);var results=new VerifiedDownload[specs.size()];int next=0,done=0;
  try(var registration=token.onCancel(child::cancel)){
   while(done<specs.size()){
    while(next<specs.size()&&pending.size()<8){
     child.check();while(!slots.tryAcquire(50,TimeUnit.MILLISECONDS)){child.check();if(workers.isShutdown())throw new IOException("下载调度已关闭");}
     final int index=next++;
     try{pending.add(completion.submit(()->new Item(index,store.fetch(specs.get(index),child,progress))));}
     catch(RuntimeException e){slots.release();throw e;}
    }
    var finished=completion.poll(50,TimeUnit.MILLISECONDS);child.check();if(finished==null)continue;pending.remove(finished);var item=finished.get();results[item.index]=item.value;done++;
   }token.check();return List.of(results);
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("批下载已取消");}catch(ExecutionException e){throw e.getCause() instanceof IOException io?io:new IOException("批下载失败",e.getCause());}catch(RejectedExecutionException e){throw new IOException("下载调度已关闭",e);}finally{child.cancel();for(var future:pending)future.cancel(true);}
 }
 private record Item(int index,VerifiedDownload value){}
 @Override public void close(){workers.shutdownNow();try{workers.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
