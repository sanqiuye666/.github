package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.downloads.CancellationToken;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 安装元数据整次查询预算；取消信号传到实际HTTP和缓存读写。 */
final class InstallationQueries implements AutoCloseable {
 @FunctionalInterface interface Work {Optional<ApiResponse> run(CancellationToken token)throws IOException;}
 private final Duration budget;
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("sqy-install-query-timeout").factory());
 private final Semaphore permits=new Semaphore(3);
 private final Map<String,CancellationToken> active=new HashMap<>();
 private final Map<String,Long> cancelled=new LinkedHashMap<>();
 InstallationQueries(Duration budget){this.budget=budget;}
 static String id(String value){try{return cn.dreamingfish.launcher.downloads.TaskRequest.uuid(value==null?UUID.randomUUID().toString():value).toString();}catch(IllegalArgumentException invalid){throw ApiRequest.invalid("安装查询标识无效");}}
 synchronized void cancel(String id){prune();cancelled.put(id,System.nanoTime());while(cancelled.size()>256)cancelled.remove(cancelled.keySet().iterator().next());var token=active.get(id);if(token!=null)token.cancel();}
 private void prune(){long now=System.nanoTime();cancelled.entrySet().removeIf(e->now-e.getValue()>TimeUnit.MINUTES.toNanos(1));}
 Optional<ApiResponse> run(String id,Work work)throws IOException{
  if(!permits.tryAcquire())throw new LauncherApiException(503,"BUSY","安装查询繁忙，请稍后重试");
  var token=new CancellationToken();var expired=new AtomicBoolean();ScheduledFuture<?> alarm=null;
  try{
   synchronized(this){prune();if(active.containsKey(id))throw new LauncherApiException(409,"QUERY_EXISTS","此安装查询正在执行");active.put(id,token);if(cancelled.containsKey(id))token.cancel();}
   token.check();alarm=timer.schedule(()->{expired.set(true);token.cancel();},budget.toMillis(),TimeUnit.MILLISECONDS);
   var result=work.run(token);token.check();return result;
  }catch(IOException error){if(expired.get())throw new LauncherApiException(504,"SOURCE_TIMEOUT","安装元数据查询超时，请稍后重试");if(token.isCancelled())throw new LauncherApiException(409,"QUERY_CANCELLED","安装查询已取消");throw error;}
  catch(RejectedExecutionException closed){throw new LauncherApiException(503,"SERVICE_CLOSED","安装查询已关闭");}
  finally{if(alarm!=null)alarm.cancel(false);synchronized(this){active.remove(id,token);}token.cancel();permits.release();}
 }
 @Override public synchronized void close(){active.values().forEach(CancellationToken::cancel);timer.shutdownNow();}
}
