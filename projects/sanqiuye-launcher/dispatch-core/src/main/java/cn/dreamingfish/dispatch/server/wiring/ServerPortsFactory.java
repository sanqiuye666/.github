package cn.dreamingfish.dispatch.server.wiring;

import cn.dreamingfish.dispatch.server.config.ServerConfig;
import cn.dreamingfish.dispatch.server.mock.MockServer;
import cn.dreamingfish.dispatch.server.model.RemoteFailure;
import cn.dreamingfish.dispatch.server.ports.ServerPorts;
import java.util.function.Supplier;

/** 唯一mock/真实选择点；真实失败绝不改用模拟实现。 */
public final class ServerPortsFactory {
    private ServerPortsFactory(){}
    public static ServerPorts create(ServerConfig config){if(!config.enabled())return new MockServer(config);return implementation(config,()->{if(!config.flag("ACCOUNTS_ENABLED")||!java.util.Set.of("player","admin").contains(config.role()))throw RemoteFailure.of("REAL_NOT_IMPLEMENTED");return new cn.dreamingfish.dispatch.server.real.HttpAccountServer(config);},()->new MockServer(config));}
    public static <T>T implementation(ServerConfig config,Supplier<T> real,Supplier<T> mock){
        if(!config.enabled())return real.get();
        return switch(config.mode()){case "mock"->mock.get();case "real"->real.get();default->throw new RemoteFailure("CONFIG_MISSING","缺少配置项：SQY_SERVER_MODE");};
    }
}
