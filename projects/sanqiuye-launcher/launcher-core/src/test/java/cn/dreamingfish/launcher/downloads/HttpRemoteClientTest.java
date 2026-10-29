package cn.dreamingfish.launcher.downloads;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class HttpRemoteClientTest {
 @Test void queuedRequestsObserveCooldownRecordedWhileWaiting() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var secondValidated=new CountDownLatch(1);var policies=new AtomicInteger();
  server.createContext("/",x->{calls.incrementAndGet();entered.countDown();try(x){try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}x.getResponseHeaders().add("X-Ratelimit-Remaining","0");x.getResponseHeaders().add("X-Ratelimit-Reset","1792540860");x.sendResponseHeaders(200,0);x.getResponseBody().write("ok".getBytes());}});server.start();
  try(var http=HttpClient.newHttpClient();var workers=Executors.newVirtualThreadPerTaskExecutor()){
   var client=new HttpRemoteClient(http,Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"),ZoneOffset.UTC),r->{if(policies.incrementAndGet()==2)secondValidated.countDown();},Duration.ofSeconds(4),Duration.ofSeconds(2));
   var request=new RemoteRequest(RemoteService.ATLAUNCHER_API,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"),Map.of());
   var first=workers.submit(()->client.bytes(request,8,new CancellationToken()));assertTrue(entered.await(2,TimeUnit.SECONDS));
   var second=workers.submit(()->assertThrows(RemoteHttpException.class,()->client.bytes(request,8,new CancellationToken())));
   assertTrue(secondValidated.await(2,TimeUnit.SECONDS));Thread.sleep(100);release.countDown();assertEquals("ok",new String(first.get(3,TimeUnit.SECONDS)));
   assertEquals(429,second.get(3,TimeUnit.SECONDS).status());assertEquals(1,calls.get());
  }finally{release.countDown();server.stop(0);}
 }
 @Test void identifyingUserAgentCannotBeOverriddenAndSuccessfulQuotaExhaustionCoolsDown() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();var agent=new java.util.concurrent.atomic.AtomicReference<String>();
  server.createContext("/",x->{calls.incrementAndGet();agent.set(x.getRequestHeaders().getFirst("User-Agent"));try(x){x.getResponseHeaders().add("X-Ratelimit-Remaining","0");x.getResponseHeaders().add("X-Ratelimit-Reset",x.getRequestURI().getPath().equals("/atl")?"1792540802":"2");x.sendResponseHeaders(200,0);x.getResponseBody().write("ok".getBytes());}});server.start();
  try(var http=HttpClient.newHttpClient()){
   String base="http://127.0.0.1:"+server.getAddress().getPort();
   for(var service:List.of(RemoteService.MODRINTH_API,RemoteService.ATLAUNCHER_API)){
    var client=client(http);var request=new RemoteRequest(service,URI.create(base+(service==RemoteService.ATLAUNCHER_API?"/atl":"/mod")),Map.of("User-Agent","python-requests/default"));
    assertArrayEquals("ok".getBytes(),client.bytes(request,8,new CancellationToken()));
    assertTrue(agent.get().startsWith("SanQiuYeLauncher/"));assertTrue(agent.get().contains("https://github.com/playmaker1978llll-art/play"));
    int before=calls.get();assertEquals(429,assertThrows(RemoteHttpException.class,()->client.bytes(request,8,new CancellationToken())).status());assertEquals(before,calls.get());
   }
  }finally{server.stop(0);}
 }
 @Test void realStreamingLimitsRedirectsStatusAndCooldown() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();
  server.createContext("/",x->{calls.incrementAndGet();try(x){String p=x.getRequestURI().getPath();if(p.equals("/redirect")){x.getResponseHeaders().add("Location","/ok");x.sendResponseHeaders(302,-1);}else if(p.equals("/limited")){x.getResponseHeaders().add("Retry-After","2");x.sendResponseHeaders(429,-1);}else if(p.equals("/date")){x.getResponseHeaders().add("Retry-After","Wed, 21 Oct 2026 00:00:04 GMT");x.sendResponseHeaders(429,-1);}else if(p.equals("/missing"))x.sendResponseHeaders(404,-1);else{x.sendResponseHeaders(200,0);x.getResponseBody().write("hello".getBytes());}}});server.start();
  try(var http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()){
   var client=client(http);String base="http://127.0.0.1:"+server.getAddress().getPort();
   assertArrayEquals("hello".getBytes(),client.bytes(req(base+"/ok"),5,new CancellationToken()));
   assertThrows(IOException.class,()->client.bytes(req(base+"/ok"),4,new CancellationToken()));
   assertEquals(302,assertThrows(RemoteHttpException.class,()->client.bytes(req(base+"/redirect"),8,new CancellationToken())).status());
   assertEquals(404,assertThrows(RemoteHttpException.class,()->client.bytes(req(base+"/missing"),8,new CancellationToken())).status());
   assertEquals(4,assertThrows(RemoteHttpException.class,()->client(http).bytes(req(base+"/date"),8,new CancellationToken())).retryAfterSeconds());
   assertEquals(2,assertThrows(RemoteHttpException.class,()->client.bytes(req(base+"/limited"),8,new CancellationToken())).retryAfterSeconds());int before=calls.get();
   assertEquals("RATE_LIMITED",assertThrows(RemoteHttpException.class,()->client.bytes(req(base+"/ok"),8,new CancellationToken())).code());assertEquals(before,calls.get());
  }finally{server.stop(0);}
 }
 @Test void headersArriveThenBodyStalls() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var release=new CountDownLatch(1);
  server.createContext("/",x->{try(x){x.sendResponseHeaders(200,0);x.getResponseBody().flush();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}});server.start();
  try(var http=HttpClient.newHttpClient()){
   long started=System.nanoTime();assertThrows(IOException.class,()->client(http).bytes(req("http://127.0.0.1:"+server.getAddress().getPort()+"/stall"),1024,new CancellationToken()));
   long millis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);assertTrue(millis>=120&&millis<1800,"elapsed "+millis);
  }finally{release.countDown();server.stop(0);}
 }
 @Test void cancellationRegistrationAndActiveStream() throws Exception {
  var token=new CancellationToken();var calls=new AtomicInteger();token.cancel();try(var registration=token.onCancel(calls::incrementAndGet)){assertEquals(1,calls.get());}assertThrows(InterruptedIOException.class,token::check);
  var active=new CancellationToken();try(var registration=active.onCancel(calls::incrementAndGet)){active.cancel();active.cancel();assertEquals(2,calls.get());}
  for(int i=0;i<100;i++){var race=new CancellationToken();var count=new AtomicInteger();var worker=new Thread(race::cancel);worker.start();var registration=race.onCancel(count::incrementAndGet);worker.join();assertEquals(1,count.get());registration.close();}
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",x->{try(x){x.sendResponseHeaders(200,0);x.getResponseBody().write(new byte[100]);}});server.start();
  try(var http=HttpClient.newHttpClient()) {var streaming=new CancellationToken();assertThrows(InterruptedIOException.class,()->client(http).get(req("http://127.0.0.1:"+server.getAddress().getPort()+"/"),1024,streaming,b->{streaming.cancel();}));}finally{server.stop(0);}
 }
 private static HttpRemoteClient client(HttpClient http){return new HttpRemoteClient(http,Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"),ZoneOffset.UTC),r->{},Duration.ofSeconds(2),Duration.ofMillis(150));}
 private static RemoteRequest req(String url){return new RemoteRequest(RemoteService.MOJANG_META,URI.create(url),Map.of());}
}
