package cn.dreamingfish.launcher.launch;
import cn.dreamingfish.launcher.downloads.*;import cn.dreamingfish.launcher.instances.*;import cn.dreamingfish.launcher.mods.*;import cn.dreamingfish.launcher.storage.*;import cn.dreamingfish.launcher.vanilla.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class ModScanConcurrencyTest {
 @TempDir Path root;
 @Test void checkStartAndBeforeProcessScansAllowCancellationAndStoppingAnotherInstance()throws Exception{
  for(int phase:new int[]{1,2,4}){
   var fixture=new GameProcessServiceTest();fixture.root=root;var slow=fixture.installed("slow-"+phase,false);var other=fixture.installed("other-"+phase,true);
   fixture.runtimes.registerHome(Path.of(System.getProperty("java.home")),new CancellationToken());var instances=new FileInstanceCatalog(root,fixture.clock);var installer=new VanillaInstaller(root,instances,null,null,fixture.clock);var mods=new InstanceModStore(root,installer,fixture.clock);
   var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();var control=Executors.newVirtualThreadPerTaskExecutor();
   try(var service=new GameProcessService(root,instances,installer,fixture.runtimes,fixture.identities,new LaunchSettingsStore(root),fixture.clock,(core,token)->{
    if(core.instanceId().equals(slow.id)&&calls.incrementAndGet()==phase){entered.countDown();while(release.getCount()>0){token.check();try{release.await(10,TimeUnit.MILLISECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();token.check();}}}
    return mods.verifyForLaunch(core,token);
   })){
    var otherLaunch=service.start(fixture.ready(service,other.id).id());fixture.until(service,other.id,LaunchSnapshot.State.RUNNING);
    GameProcessService.CheckSnapshot check;Future<LaunchSnapshot> starting=null;LaunchSnapshot launch=null;
    if(phase==1)check=service.check(slow.id,fixture.identities.create("Slow").id(),new CancellationToken());
    else{check=fixture.ready(service,slow.id);if(phase==2){var checkId=check.id();starting=control.submit(()->service.start(checkId));}else launch=service.start(check.id());}
    try{
     assertTrue(entered.await(5,TimeUnit.SECONDS),"必须实际进入受控慢扫描阶段"+phase);
     control.submit(()->service.stop(otherLaunch.id(),true)).get(1,TimeUnit.SECONDS);
     if(phase==4){var launchId=launch.id();control.submit(()->service.stop(launchId,true)).get(1,TimeUnit.SECONDS);}
     else{var checkId=check.id();control.submit(()->{service.cancelCheck(checkId);return service.checkStatus(checkId);}).get(1,TimeUnit.SECONDS);}
    }finally{release.countDown();Files.writeString(other.instance.directory().resolve("fixture-exit.txt"),"exit");Files.writeString(slow.instance.directory().resolve("fixture-exit.txt"),"exit");
     var record=LauncherJson.read(Files.readAllBytes(root.resolve("instances/"+other.id+"/launches/"+otherLaunch.id()+"/launch.json")),1024*1024);var process=ProcessHandle.of(record.path("pid").longValue());if(process.isPresent())process.get().onExit().get(5,TimeUnit.SECONDS);
    }
    if(starting!=null){var attempt=starting;assertThrows(ExecutionException.class,()->attempt.get(5,TimeUnit.SECONDS));}
    assertFalse(Files.exists(slow.instance.directory().resolve("fixture-started.txt")),"取消后不能创建目标游戏");
    fixture.until(service,other.id,LaunchSnapshot.State.EXITED);
   }finally{release.countDown();control.shutdownNow();}
  }
 }
}
