package cn.dreamingfish.launcher.servers;
import cn.dreamingfish.launcher.launch.LaunchFailure;
import java.io.*;import java.net.*;import java.time.*;import java.util.*;import java.util.concurrent.*;
/** 手动TCP检测只有连通层含义；DNS/连接总预算5秒，无重试。 */
public final class TcpServerProbe implements ServerProbe {
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,4,20,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),Thread.ofPlatform().daemon(true).name("sqy-server-probe-",0).factory());
 @Override public Result probe(ServerAddress address)throws IOException{
  long start=System.nanoTime();String requestId=UUID.randomUUID().toString();Future<Result> pending;
  try{pending=workers.submit(()->{try(var socket=new Socket()){socket.connect(new InetSocketAddress(address.host(),address.port()),3000);return result("REACHABLE",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),"TCP端口可达；不代表Minecraft协议、登录或入服成功");}catch(SocketTimeoutException timeout){return result("TIMEOUT",null,"连接超时");}catch(IOException failed){return result("UNREACHABLE",null,"无法连接主机或端口；请检查地址、网络与服务状态");}});}catch(RejectedExecutionException busy){throw new LaunchFailure("SERVER_PROBE_BUSY","检测繁忙，请稍后重试");}
  Result result;
  try{result=pending.get(5,TimeUnit.SECONDS);}catch(TimeoutException timeout){pending.cancel(true);result=result("TIMEOUT",null,"DNS或连接超过5秒预算");}catch(InterruptedException cancelled){pending.cancel(true);Thread.currentThread().interrupt();throw new InterruptedIOException("检测已取消");}catch(ExecutionException failed){throw new IOException("服务器检测未完成");}
  java.util.logging.Logger.getLogger(TcpServerProbe.class.getName()).info("{\"requestId\":\""+requestId+"\",\"target\":\""+address.authority()+"\",\"elapsedMs\":"+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)+",\"code\":\""+result.state()+"\"}");return result;
 }
 private static Result result(String state,Long latency,String message){return new Result(state,latency,Instant.now().toString(),message);}
 @Override public void close(){workers.shutdownNow();}
}
