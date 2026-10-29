package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.GameInstance.LoaderKind;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.vanilla.*;
import java.io.IOException;
import java.util.List;
public interface LoaderProvider {
 LoaderKind kind();
 List<LoaderVersions.Version> versions(String game,CancellationToken token)throws IOException;
 LoaderPlan plan(String game,String loader,CancellationToken token)throws IOException;
 LoaderPlan pinned(String game,String metadataSha1,String loader,String sourceSha256,String fingerprint,CancellationToken token)throws IOException;
 default InstallationReceipt install(String instanceId,LoaderPlan plan,VanillaInstaller installer,TaskContext context)throws IOException{return installer.install(instanceId,plan.installation(),context);}
}
