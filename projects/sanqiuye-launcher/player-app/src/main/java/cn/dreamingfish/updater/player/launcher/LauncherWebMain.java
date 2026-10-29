package cn.dreamingfish.updater.player.launcher;

import cn.dreamingfish.launcher.catalog.*;
import cn.dreamingfish.launcher.instances.FileInstanceCatalog;
import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.vanilla.*;
import cn.dreamingfish.launcher.mods.*;
import cn.dreamingfish.launcher.packs.*;
import cn.dreamingfish.launcher.runtime.JavaRuntimeService;
import cn.dreamingfish.launcher.identity.OfflineIdentitySession;
import cn.dreamingfish.launcher.launch.*;
import cn.dreamingfish.launcher.options.GameOptionsStore;
import cn.dreamingfish.dispatch.server.config.ServerConfig;
import cn.dreamingfish.dispatch.server.model.RemoteFailure;
import cn.dreamingfish.dispatch.server.web.ServerEndpoint;
import cn.dreamingfish.dispatch.server.wiring.ServerPortsFactory;
import cn.dreamingfish.updater.player.launcher.server.OfflineResourceTransport;
import cn.dreamingfish.updater.player.launcher.web.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Explicit local player panel; the updater's default main remains unchanged. */
public final class LauncherWebMain {
    private LauncherWebMain() {}
    public static void main(String[] args) { int code=run(args,System.out,System.err);if(code!=0)System.exit(code); }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            var options=new HashMap<String,String>();
            if(args==null)throw new IllegalArgumentException("缺少参数");
            for(int i=0;i<args.length;i++) {
                String key=args[i];
                if(!Set.of("--data-root","--port","--stdin-lifetime").contains(key)||options.containsKey(key)||++i==args.length)throw new IllegalArgumentException("未知、重复或缺少参数");
                options.put(key,args[i]);
            }
            if(!options.containsKey("--data-root"))throw new IllegalArgumentException("需要 --data-root <绝对目录>");
            Path root=Path.of(options.get("--data-root"));if(!root.isAbsolute())throw new IllegalArgumentException("数据根必须是绝对目录");
            int port=Integer.parseInt(options.getOrDefault("--port","0"));if(port<0||port>65535)throw new IllegalArgumentException("端口范围为 0–65535");
            if(options.containsKey("--stdin-lifetime")&&!options.get("--stdin-lifetime").equals("true"))throw new IllegalArgumentException("原生管道选项必须为 true");
            var serverConfig=ServerConfig.fromEnvironment(root.resolve("server.env"));var serverPorts=ServerPortsFactory.create(serverConfig);
            var serverEndpoint=new ServerEndpoint(serverConfig,serverPorts,"player");
            try(var lifetime=NativeLifetime.open(root,options.containsKey("--stdin-lifetime"))) {
            var clock=Clock.systemUTC();
            ManifestTransport manifestTransport=ServerPortsFactory.implementation(serverConfig,HttpManifestTransport::new,OfflineResourceTransport::new);
            var mojang=new MojangVersionCatalog(ServerPortsFactory.implementation(serverConfig,()->root,()->root.resolve("mock-catalog")),clock,manifestTransport);
            var catalog=new CatalogSession(mojang);
            var instances=new FileInstanceCatalog(root,clock);
            RemoteClient remote=ServerPortsFactory.implementation(serverConfig,HttpRemoteClient::new,OfflineResourceTransport::new);var store=new DownloadStore(root,remote);
            var library=new ModLibrary(root,store,clock);var mods=new ModService(Map.of(ModSource.MODRINTH,new ModrinthProvider(remote,clock),ModSource.CURSEFORGE,new CurseForgeProvider(remote,()->System.getenv("SQY_CURSEFORGE_API_KEY"),clock)),library);
            try(var batch=new DownloadBatch(store,4)) {
            var planner=new VanillaPlanner(mojang,store,Platform.current());var fabric=ServerPortsFactory.implementation(serverConfig,()->new cn.dreamingfish.launcher.loaders.fabric.FabricPlanner(planner,store,remote),()->null);var installer=new VanillaInstaller(root,instances,store,batch,clock);var loaders=ServerPortsFactory.implementation(serverConfig,()->cn.dreamingfish.launcher.loaders.LoaderProviders.production(root,planner,store,remote),()->java.util.Map.<cn.dreamingfish.launcher.GameInstance.LoaderKind,cn.dreamingfish.launcher.loaders.LoaderProvider>of());
            var instanceMods=new InstanceModService(mods,new InstanceModStore(root,installer,store,batch,clock));
            try(var tasks=new TaskManager(root,clock,Map.of(TaskKind.MOD_INSTALL,instanceMods::run,TaskKind.MOD_REMOVE,instanceMods::run,TaskKind.LOADER_INSTALL,(request,context)->{var inputs=request.inputs();var provider=loaders.get(cn.dreamingfish.launcher.GameInstance.LoaderKind.valueOf(inputs.get("loader")));if(provider==null)throw new TaskFailure("SOURCE_UNAVAILABLE","此模式尚未接入所选核心");provider.install(inputs.get("instanceId"),provider.pinned(inputs.get("versionId"),inputs.get("metadataSha1"),inputs.get("loaderVersion"),inputs.get("sourceSha256"),inputs.get("planSha256"),context.token()),installer,context);},TaskKind.FABRIC_INSTALL,(request,context)->{if(fabric==null)throw new TaskFailure("SOURCE_UNAVAILABLE","此模式尚未接入Fabric");var inputs=request.inputs();installer.install(inputs.get("instanceId"),fabric.planPinned(inputs.get("versionId"),inputs.get("metadataSha1"),inputs.get("fabricVersion"),inputs.get("profileSha256"),inputs.get("planSha256"),context.token()).installation(),context);},TaskKind.VANILLA_INSTALL,(request,context)->installer.install(request.inputs().get("instanceId"),planner.planPinned(request.inputs().get("versionId"),request.inputs().get("metadataSha1"),context.token()),context),TaskKind.INSTALLATION_VERIFY,(request,context)->{context.progress(TaskState.VERIFYING,0,null,0,null);installer.verifyActive(request.inputs().get("instanceId"),context.token());context.publish(()->{});},TaskKind.MOD_DOWNLOAD,mods::runDownload,TaskKind.MOD_VERIFY,(request,context)->{context.progress(TaskState.VERIFYING,0,null,0,null);library.verify(UUID.fromString(request.inputs().get("entryId")),context.token());context.publish(()->{});}))){
            var runtimes=new JavaRuntimeService();var identities=new OfflineIdentitySession();var launchSettings=new LaunchSettingsStore(root);var playerServers=new cn.dreamingfish.launcher.servers.ServerStore(root);
            try(var resourceRoutes=new ResourceRoutes(new cn.dreamingfish.launcher.resources.ResourceStore(root));var playerServerRoutes=new PlayerServerRoutes(playerServers,ServerPortsFactory.implementation(serverConfig,cn.dreamingfish.launcher.servers.TcpServerProbe::new,cn.dreamingfish.launcher.servers.MockServerProbe::new));var instanceModRoutes=new InstanceModRoutes(instanceMods,tasks,clock);var processes=new GameProcessService(root,instances,installer,runtimes,identities,launchSettings,clock);var runtimeRoutes=new RuntimeRoutes(runtimes,clock);var installationRoutes=new InstallationRoutes(planner,installer,tasks,instances,clock,fabric,loaders);var packRoutes=new PackRoutes(new PackCatalog(Map.of(PackSource.MODRINTH,new ModrinthPackProvider(remote,clock),PackSource.FTB,new FtbPackProvider(remote,clock),PackSource.ATLAUNCHER,new AtLauncherPackProvider(remote,clock))),ServerPortsFactory.implementation(serverConfig,()->null,()->"MOCK"))) {
            var capabilities=new HashMap<>(Map.of("catalog","AVAILABLE","instances","AVAILABLE","vanilla","AVAILABLE","mods","AVAILABLE","packs","AVAILABLE","runtimes","AVAILABLE","identities","AVAILABLE","launch","AVAILABLE","gameOptions","AVAILABLE","server",serverPorts.status().source()));capabilities.put("instanceMods","AVAILABLE");capabilities.put("resources","AVAILABLE");capabilities.put("playerServers","AVAILABLE");capabilities.put("fabric",fabric==null?"PENDING":"AVAILABLE");capabilities.put("loaders",loaders.isEmpty()?"PENDING":"AVAILABLE");
            var api=new LauncherApi(List.of(resourceRoutes,playerServerRoutes,new ServerRoutes(serverEndpoint),new CatalogRoutes(catalog,ServerPortsFactory.implementation(serverConfig,()->null,()->"MOCK")),new InstanceRoutes(instances,catalog,installer),installationRoutes,new TaskRoutes(tasks),instanceModRoutes,new ModRoutes(mods,library,tasks),packRoutes,runtimeRoutes,new IdentityRoutes(identities),new LaunchRoutes(processes,launchSettings,playerServers),new GameOptionsRoutes(new GameOptionsStore(root))),capabilities);
            try(var server=LauncherWebServer.start(port,api,LauncherWebMain.class.getClassLoader())) {
                var stop=new CountDownLatch(1);
                Thread hook=new Thread(()->{try{server.close();resourceRoutes.close();playerServerRoutes.close();instanceModRoutes.close();installationRoutes.close();packRoutes.close();runtimeRoutes.close();processes.close();tasks.close();}catch(IOException error){err.println("任务记录关闭失败，请在下次启动时检查中断任务");}finally{batch.close();stop.countDown();}},"sqy-player-shutdown");Runtime.getRuntime().addShutdownHook(hook);
                lifetime.watch(System.in,stop::countDown);
                try {
                    out.println(new String(LauncherJson.write(Map.of("url",server.baseUri().toString(),"mode","LOCAL")),StandardCharsets.UTF_8));out.flush();
                    stop.await();
                } catch(InterruptedException cancelled){Thread.currentThread().interrupt();}
                finally{try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException shuttingDown){/* JVM owns hook. */}}
            }
            }}}
            }
            return 0;
        }catch(RemoteFailure error){err.println(error.code()+"："+error.getMessage());return 4;}
        catch(IllegalArgumentException error){err.println("启动参数无效："+error.getMessage());return 2;}
        catch(IOException error){err.println("本机面板未启动："+error.getMessage());return 3;}
    }
}
