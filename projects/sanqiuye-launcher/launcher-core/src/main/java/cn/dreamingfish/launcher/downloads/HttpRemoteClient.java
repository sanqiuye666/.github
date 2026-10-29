package cn.dreamingfish.launcher.downloads;
import java.io.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.Flow;

public final class HttpRemoteClient implements RemoteClient {
 @FunctionalInterface interface Policy {void validate(RemoteRequest request) throws IOException;}
 private final HttpClient client;private final Clock clock;private final Policy policy;private final Duration timeout,idle;
 private final Map<RemoteService,Instant> cooldown=new EnumMap<>(RemoteService.class);private final Map<RemoteService,Deque<Long>> requestStarts=new EnumMap<>(RemoteService.class);
 public HttpRemoteClient(){this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build(),Clock.systemUTC(),new RemotePolicy()::validate,null,Duration.ofSeconds(15));}
 HttpRemoteClient(HttpClient client,Clock clock,Policy policy,Duration timeout,Duration idle){if(client.followRedirects()!=HttpClient.Redirect.NEVER)throw new IllegalArgumentException("禁止 HTTP 重定向");this.client=client;this.clock=clock;this.policy=policy;this.timeout=timeout;this.idle=idle;}
 @Override public void get(RemoteRequest request,long limit,CancellationToken token,ChunkConsumer sink) throws IOException {
  if(limit<1)throw new IllegalArgumentException("响应上限必须为正数");token.check();policy.validate(request);acquire(request.service(),token);
  Duration total=timeout!=null?timeout:switch(request.service()){case MOJANG_META,MODRINTH_API,CURSEFORGE_API,FTB_API,ATLAUNCHER_API,FABRIC_META,QUILT_META->Duration.ofSeconds(30);default->Duration.ofMinutes(15);};
  long deadline=System.nanoTime()+total.toNanos();
  var builder=HttpRequest.newBuilder(request.uri()).timeout(total).GET().header("User-Agent","SanQiuYeLauncher/0.1 (+https://github.com/playmaker1978llll-art/play)");
  request.headers().forEach((k,v)->{if(!k.equalsIgnoreCase("user-agent"))builder.header(k,v);});
  var pending=client.sendAsync(builder.build(),HttpResponse.BodyHandlers.ofPublisher());
  var subscription=new AtomicReference<Flow.Subscription>();var bodyDone=new CompletableFuture<Void>();
  try(var registration=token.onCancel(()->{pending.cancel(true);var s=subscription.get();if(s!=null)s.cancel();bodyDone.completeExceptionally(new InterruptedIOException("操作已取消"));})){
   var response=await(pending,deadline,token);
   if(response.statusCode()!=200){response.body().subscribe(new Flow.Subscriber<>(){public void onSubscribe(Flow.Subscription s){s.cancel();}public void onNext(List<ByteBuffer>b){}public void onError(Throwable e){}public void onComplete(){}});
    if(response.statusCode()==429){int seconds=response.headers().firstValue("Retry-After").map(this::retry).orElseGet(()->quotaReset(request.service(),response.headers()));rememberCooldown(request.service(),seconds);throw new RemoteHttpException(429,"RATE_LIMITED",seconds);}
    throw new RemoteHttpException(response.statusCode(),"HTTP_ERROR",null);
   }
   if(response.headers().firstValue("X-Ratelimit-Remaining").map(String::strip).filter("0"::equals).isPresent())rememberCooldown(request.service(),quotaReset(request.service(),response.headers()));
   long declared=response.headers().firstValueAsLong("Content-Length").orElse(-1);if(declared>limit){pending.cancel(true);response.body().subscribe(new Flow.Subscriber<>(){public void onSubscribe(Flow.Subscription s){s.cancel();}public void onNext(List<ByteBuffer>b){}public void onError(Throwable e){}public void onComplete(){}});throw new IOException("响应超过大小上限");}
   var last=new AtomicLong(System.nanoTime());
   response.body().subscribe(new Flow.Subscriber<>() {
    long count;
    public void onSubscribe(Flow.Subscription s){subscription.set(s);if(token.isCancelled())s.cancel();else s.request(1);}
    public void onNext(List<ByteBuffer> buffers){try{token.check();for(var buffer:buffers){if(buffer.remaining()>limit-count)throw new IOException("响应超过大小上限");count+=buffer.remaining();sink.accept(buffer);}last.set(System.nanoTime());subscription.get().request(1);}catch(Exception e){subscription.get().cancel();bodyDone.completeExceptionally(e);}}
    public void onError(Throwable e){bodyDone.completeExceptionally(e);}public void onComplete(){bodyDone.complete(null);}
   });
   while(true){token.check();long remaining=Math.min(deadline-System.nanoTime(),idle.toNanos()-(System.nanoTime()-last.get()));if(remaining<=0)throw new IOException("下载正文空闲或整次请求超时");try{bodyDone.get(remaining,TimeUnit.NANOSECONDS);break;}catch(TimeoutException elapsed){/* Re-check activity and deadline. */}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("操作已取消");}catch(ExecutionException e){throw io(e.getCause());}}
   token.check();
  }finally{pending.cancel(true);var s=subscription.get();if(s!=null)s.cancel();}
 }
 private <T>T await(CompletableFuture<T> future,long deadline,CancellationToken token)throws IOException {
  try{return future.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("操作已取消");}catch(CancellationException e){token.check();throw new IOException("请求已终止");}catch(TimeoutException e){throw new IOException("官方请求超时");}catch(ExecutionException e){throw io(e.getCause());}
 }
 private static IOException io(Throwable error){return error instanceof IOException io?io:new IOException("官方请求未完成",error);}
 private int retry(String value){try{return clamp(Long.parseLong(value));}catch(NumberFormatException ignored){try{return clamp(Duration.between(clock.instant(),ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds());}catch(RuntimeException badDate){return 60;}}}
 private static int clamp(long seconds){return (int)Math.max(1,Math.min(3600,seconds));}
 private int quotaReset(RemoteService service,HttpHeaders headers){
  try{long value=Long.parseLong(headers.firstValue("X-Ratelimit-Reset").orElse(""));
   if(service==RemoteService.ATLAUNCHER_API)value=Math.subtractExact(value,clock.instant().getEpochSecond());
   else if(service!=RemoteService.MODRINTH_API)return 60;
   return clamp(value);
  }catch(RuntimeException invalid){return 60;}
 }
 private void rememberCooldown(RemoteService service,int seconds){synchronized(cooldown){var until=clock.instant().plusSeconds(seconds);cooldown.merge(service,until,(old,next)->old.isAfter(next)?old:next);}}
 private void acquire(RemoteService service,CancellationToken token)throws IOException {
  int maximum=service==RemoteService.MODRINTH_API?4:service==RemoteService.ATLAUNCHER_API?1:0;
  while(true){token.check();synchronized(cooldown){var until=cooldown.get(service);if(until!=null&&until.isAfter(clock.instant()))throw new RemoteHttpException(429,"RATE_LIMITED",clamp(Duration.between(clock.instant(),until).getSeconds()+1));}
   if(maximum==0)return;
   synchronized(requestStarts){var starts=requestStarts.computeIfAbsent(service,key->new ArrayDeque<>());long now=System.nanoTime();while(!starts.isEmpty()&&now-starts.peekFirst()>=1_000_000_000L)starts.removeFirst();if(starts.size()<maximum){starts.addLast(now);return;}}try{Thread.sleep(25);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("操作已取消");}}
 }
}
