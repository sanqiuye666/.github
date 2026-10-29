import cn.dreamingfish.launcher.catalog.*;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.instances.*;
import cn.dreamingfish.launcher.storage.*;
import cn.dreamingfish.launcher.vanilla.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
/** Independent JVM receipt and official-source digest verification of marked smoke data. */
public class VanillaInstallProbe {
 public static void main(String[] args)throws Exception{
  var options=new HashMap<String,String>();for(int i=0;i<args.length;i+=2)options.put(args[i],args[i+1]);Path root=Path.of(options.get("--data-root")),report=Path.of(options.get("--report"));if(!root.isAbsolute()||!report.isAbsolute())throw new IllegalArgumentException("Absolute paths required");var storage=new LauncherStorage(root);if(!storage.exists(Path.of(".sqy-m2b-m3a-smoke.json")))throw new IllegalArgumentException("Marked root required");
  var instances=new FileInstanceCatalog(root,Clock.systemUTC());var store=new DownloadStore(root,new HttpRemoteClient());var results=new ArrayList<Map<String,Object>>();
  try(var batch=new DownloadBatch(store,4)){var installer=new VanillaInstaller(root,instances,store,batch,Clock.systemUTC());var planner=new VanillaPlanner(MojangVersionCatalog.production(root),store,Platform.current());
   for(var prepared:instances.listing().instances()){String id=prepared.instance().id();var listing=installer.listing(id);if(listing.installations().isEmpty())continue;var receipt=installer.verifyActive(id,new CancellationToken());var plan=planner.planPinned(receipt.versionId(),receipt.metadataSha1(),new CancellationToken());int sources=0;
    for(var file:plan.files()){String path=file.relativePath().toString().replace('\\','/');if(!path.equals("versions/client.jar")&&!path.startsWith("assets/objects/"))continue;var digest=file.download().digest().newDigest();Path relative=Path.of("instances/"+id+"/installations/"+receipt.installationId()).resolve(file.relativePath());long count=0;try(var in=storage.openRead(relative)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){count+=n;digest.update(buffer,0,n);}}if(count!=file.download().expectedSize()||!HexFormat.of().formatHex(digest.digest()).equals(file.download().digest().hex()))throw new IOException("Official source mismatch "+receipt.versionId()+" "+path);sources++;}
    results.add(Map.of("instanceId",id,"versionId",receipt.versionId(),"installationId",receipt.installationId(),"receiptFilesVerified",receipt.files().size(),"officialSourceFilesVerified",sources));
   }
  }
  Files.createDirectories(report.getParent());Files.write(report,LauncherJson.write(Map.of("schemaVersion",1,"results",results)));System.out.println("Verified installations "+results.size());
 }
}
