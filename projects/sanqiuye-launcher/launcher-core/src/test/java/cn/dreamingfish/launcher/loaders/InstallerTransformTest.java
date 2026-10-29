package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.instances.FileInstanceCatalog;
import cn.dreamingfish.launcher.vanilla.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InstallerTransformTest {
 @TempDir Path root;
 static class Context implements TaskContext {final CancellationToken token=new CancellationToken();public UUID id(){return UUID.randomUUID();}public CancellationToken token(){return token;}public void progress(TaskState s,long b,Long t,long f,Long n){}public void publish(IoAction action)throws java.io.IOException{token.check();action.run();}}
 @Test void failedGenerationCannotReplaceActiveInstallationOrOriginalFiles()throws Exception{
  var f=VanillaFixtures.setup(root,false,false,false);var catalog=new FileInstanceCatalog(root,VanillaFixtures.CLOCK);String id=catalog.create("generation","26.3",f.catalog().load(false).manifest()).instance().id();var plan=new VanillaPlanner(f.catalog(),f.store(),VanillaFixtures.WINDOWS).plan("26.3",new CancellationToken());
  try(var batch=new DownloadBatch(f.store(),4)){var installer=new VanillaInstaller(root,catalog,f.store(),batch,VanillaFixtures.CLOCK);installer.install(id,plan,new Context());Path pointer=root.resolve("instances/"+id+"/active-installation.json");byte[] before=Files.readAllBytes(pointer);
   assertThrows(java.io.IOException.class,()->installer.install(id,plan,new Context(),(stage,token)->{Files.writeString(stage.resolve("versions/generated.json"),"incomplete");throw new java.io.IOException("fixture generation failed");}));assertArrayEquals(before,Files.readAllBytes(pointer));
   assertThrows(java.io.IOException.class,()->installer.install(id,plan,new Context(),(stage,token)->{Files.writeString(stage.resolve("versions/client.jar"),"mutated");return List.of();}));assertArrayEquals(before,Files.readAllBytes(pointer));
  }
 }
}
