package cn.dreamingfish.dispatch.server.real;
import cn.dreamingfish.dispatch.server.config.ServerConfig;import cn.dreamingfish.dispatch.server.model.*;
import cn.dreamingfish.updater.protocol.JsonCodec;import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;import java.net.http.*;import java.nio.*;import java.io.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.Flow.*;
/** 账号专用HTTP能力：固定动作白名单、完整响应总预算、有限GET重试，不跟随重定向。 */
final class AccountHttpTransport {
 private static final Set<String> ROUTES=Set.of("account/register","account/login","account/me","account/logout","admin/accounts");
 private final ServerConfig config;private final URI base;private final HttpClient client;private final JsonCodec json=new JsonCodec();private final ServerCallLog log;
 AccountHttpTransport(ServerConfig config){this.config=config;this.base=URI.create(config.value("API_BASE_URL").replaceAll("/+$","")+"/");client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.number("TIMEOUT_CONNECT_MS"))).followRedirects(HttpClient.Redirect.NEVER).proxy(HttpClient.Builder.NO_PROXY).build();log=new ServerCallLog(config.logRoot(),config.value("TARGET_NAME"));}
 JsonNode call(CallContext context,String route,Object body,String token){
  if(!ROUTES.contains(route))throw RemoteFailure.of("FORBIDDEN");boolean read=body==null;int maximum=read?config.retryMax()+1:1,attempt=0;long start=System.nanoTime();String result="OK";
  try{for(;;){attempt++;try{return attempt(context,route,body,token);}catch(RemoteFailure e){if(!read||!e.code().equals("CONNECT_FAILED")||attempt>=maximum)throw e;context.check();long delay=Math.min(config.number("RETRY_MAX_MS"),config.number("RETRY_BASE_MS")*attempt);if(Duration.between(Instant.now(),context.deadline()).toMillis()<=delay)throw e;try{Thread.sleep(delay);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw RemoteFailure.of("CANCELLED");}}}}
  catch(RemoteFailure e){result=e.code();throw e;}finally{log.record(context.requestId(),route,start,result,attempt);}
 }
 private JsonNode attempt(CallContext c,String route,Object body,String token){
  c.check();long budget=Math.min(config.number("TIMEOUT_SESSION_MS"),Duration.between(Instant.now(),c.deadline()).toMillis());if(budget<1)throw RemoteFailure.of("CONNECT_FAILED");
  var builder=HttpRequest.newBuilder(base.resolve(route)).timeout(Duration.ofMillis(budget)).header("Accept","application/json").header("User-Agent","SanQiuYe/0.2 (panel accounts)").header("X-Request-ID",c.requestId());
  if(token!=null)builder.header("Authorization","Bearer "+token);if(body==null)builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json.write(body)));
  CompletableFuture<HttpResponse<byte[]>> future=client.sendAsync(builder.build(),info->new LimitedBody());
  try{var response=future.get(budget,TimeUnit.MILLISECONDS);c.check();int status=response.statusCode();
   if(status==401)throw RemoteFailure.of("AUTH_FAILED");if(status==403)throw RemoteFailure.of("FORBIDDEN");if(status==409)throw RemoteFailure.of("CONFLICT");if(status==429)throw new RemoteFailure("RATE_LIMITED","请求过于频繁，请稍后再试");if(status>=500)throw RemoteFailure.of("SERVER_ERROR");if(status>=300&&status<400)throw RemoteFailure.of("INVALID_DATA");
   if(status<200||status>=300)throw RemoteFailure.of("INVALID_DATA");if(!response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).startsWith("application/json"))throw RemoteFailure.of("INVALID_DATA");
   JsonNode node=json.read(response.body(),JsonNode.class);if(!node.isObject())throw RemoteFailure.of("INVALID_DATA");return node;
  }catch(RemoteFailure e){throw e;}catch(TimeoutException e){throw RemoteFailure.of("CONNECT_FAILED");}catch(InterruptedException e){Thread.currentThread().interrupt();throw RemoteFailure.of("CANCELLED");}catch(ExecutionException e){if(e.getCause() instanceof TooLarge)throw RemoteFailure.of("INVALID_DATA");if(e.getCause() instanceof javax.net.ssl.SSLException)throw RemoteFailure.of("AUTH_FAILED");throw RemoteFailure.of("CONNECT_FAILED");}catch(RuntimeException e){throw RemoteFailure.of("INVALID_DATA");}finally{future.cancel(true);}
 }
 private static final class TooLarge extends IOException {}
 private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
  private final CompletableFuture<byte[]> body=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Subscription subscription;
  public CompletionStage<byte[]> getBody(){return body;}
  public void onSubscribe(Subscription s){subscription=s;s.request(1);}
  public void onNext(List<ByteBuffer> buffers){for(var buffer:buffers){if(buffer.remaining()>1024*1024-bytes.size()){subscription.cancel();body.completeExceptionally(new TooLarge());return;}byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);}subscription.request(1);}
  public void onError(Throwable e){body.completeExceptionally(e);}public void onComplete(){body.complete(bytes.toByteArray());}
 }
}
