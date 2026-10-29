package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.GameInstance.LoaderKind;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.vanilla.VanillaPlanner;
import java.nio.file.Path;
import java.util.Map;
/** 真实协议适配器集中装配；离线模式由既有ServerPortsFactory在调用处一次选择。 */
public final class LoaderProviders{
 private LoaderProviders(){}
 public static Map<LoaderKind,LoaderProvider> production(Path root,VanillaPlanner vanilla,DownloadStore store,RemoteClient remote){return Map.of(LoaderKind.QUILT,new QuiltProvider(vanilla,store,remote),LoaderKind.FORGE,new ForgeProvider(LoaderKind.FORGE,vanilla,store,remote,root),LoaderKind.NEOFORGE,new ForgeProvider(LoaderKind.NEOFORGE,vanilla,store,remote,root));}
}
