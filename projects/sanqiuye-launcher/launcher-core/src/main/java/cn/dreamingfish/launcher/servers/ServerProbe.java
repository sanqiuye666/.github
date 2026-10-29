package cn.dreamingfish.launcher.servers;
import java.io.IOException;
/** 真实探测和离线测试替身通过同一能力接口接入，不散落模式分支。 */
public interface ServerProbe extends AutoCloseable {
 record Result(String state,Long latencyMs,String checkedAt,String message){}
 Result probe(ServerAddress address)throws IOException;
 @Override default void close(){}
}
