package cn.dreamingfish.launcher.servers;
/** 离线演练不发网络请求，显式返回示例状态。 */
public final class MockServerProbe implements ServerProbe {
 @Override public Result probe(ServerAddress address){return new Result("MOCK",null,java.time.Instant.now().toString(),"MOCK · 离线示例，未连接目标地址，不代表真实可达");}
}
