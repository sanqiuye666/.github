package cn.dreamingfish.dispatch.server.real;
import cn.dreamingfish.dispatch.server.ports.ServerPorts;
import cn.dreamingfish.dispatch.server.model.RemoteFailure;
/** 账号首批真实能力边界；未实现的远程操作明确拒绝，不借用mock成功。 */
abstract class AccountsOnlyServer implements ServerPorts {
 public PanelSessionPort sessions(){throw RemoteFailure.of("FEATURE_DISABLED");}
 public PlayerProfilePort profiles(){throw RemoteFailure.of("FEATURE_DISABLED");}
 public DispatchDirectoryPort directory(){throw RemoteFailure.of("FEATURE_DISABLED");}
 public ManagedPackPort packs(){throw RemoteFailure.of("FEATURE_DISABLED");}
 public PackAdminPort packAdmin(){throw RemoteFailure.of("FEATURE_DISABLED");}
 public PackPublishPort publish(){throw RemoteFailure.of("FEATURE_DISABLED");}
 public PackContentPort content(){throw RemoteFailure.of("FEATURE_DISABLED");}
 public ServerRuntimePort runtime(){throw RemoteFailure.of("FEATURE_DISABLED");}
 public ServerFilePort files(){throw RemoteFailure.of("FEATURE_DISABLED");}
 public MinecraftControlPort minecraft(){throw RemoteFailure.of("FEATURE_DISABLED");}
 public PanelAccountRepository accounts(){throw RemoteFailure.of("FEATURE_DISABLED");}
 public ServerReleasePort release(){throw RemoteFailure.of("FEATURE_DISABLED");}
}
