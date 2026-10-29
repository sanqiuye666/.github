package cn.dreamingfish.launcher.downloads;
import java.io.*;
import java.nio.ByteBuffer;
public interface RemoteClient {
 @FunctionalInterface interface ChunkConsumer {void accept(ByteBuffer bytes) throws IOException;}
 void get(RemoteRequest request,long limit,CancellationToken token,ChunkConsumer sink) throws IOException;
 default byte[] bytes(RemoteRequest request,int limit,CancellationToken token) throws IOException {
  var out=new ByteArrayOutputStream();get(request,limit,token,bytes->{byte[] part=new byte[bytes.remaining()];bytes.get(part);out.write(part);});return out.toByteArray();
 }
}
