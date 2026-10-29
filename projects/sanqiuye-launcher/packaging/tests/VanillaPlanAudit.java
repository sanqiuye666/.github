import cn.dreamingfish.launcher.catalog.*;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.*;
import cn.dreamingfish.launcher.vanilla.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
/** Read-only official metadata audit; never installs the listed game binaries. */
public class VanillaPlanAudit {
 public static void main(String[] args)throws Exception{
  var options=new HashMap<String,String>();for(int i=0;i<args.length;i+=2)options.put(args[i],args[i+1]);Path root=Path.of(options.get("--data-root")),report=Path.of(options.get("--report"));if(!root.isAbsolute()||!report.isAbsolute())throw new IllegalArgumentException("Absolute test paths required");
  var storage=new LauncherStorage(root);if(!storage.exists(Path.of(".sqy-m2b-m3a-smoke.json")))throw new IllegalArgumentException("A marked smoke test root is required");
  var catalog=MojangVersionCatalog.production(root);var manifest=catalog.load(false).manifest();var store=new DownloadStore(root,new HttpRemoteClient());var planner=new VanillaPlanner(catalog,store,Platform.current());var results=Collections.synchronizedList(new ArrayList<Map<String,Object>>());
  try(var workers=Executors.newFixedThreadPool(4)){
   var completion=new ExecutorCompletionService<Map<String,Object>>(workers);int next=0,done=0,inFlight=0;
   while(done<manifest.versions().size()){
    while(next<manifest.versions().size()&&inFlight<4){var entry=manifest.versions().get(next++);completion.submit(()->{var result=new LinkedHashMap<String,Object>();result.put("id",entry.id());result.put("type",entry.type().wireName());try{var plan=planner.plan(entry.id(),new CancellationToken());result.put("status","SUCCESS");result.put("fileCount",plan.files().size());result.put("totalBytes",plan.totalBytes());result.put("metadataSha1",plan.metadataSha1());}catch(Exception error){boolean parse=error instanceof IllegalArgumentException||error.getCause() instanceof IllegalArgumentException||error.getMessage()!=null&&error.getMessage().contains("索引无效");result.put("status",error instanceof FileSystemException?"LOCAL_FAILURE":parse?"PARSE_FAILURE":"EXTERNAL_FAILURE");result.put("errorType",error.getClass().getName());result.put("reason",error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());}return result;});inFlight++;}
    var result=completion.take().get();results.add(result);done++;inFlight--;if(done%25==0)System.out.println("AUDIT "+done+"/"+manifest.versions().size());
   }
  }
  var counts=new TreeMap<String,Long>();for(var result:results)counts.merge((String)result.get("status"),1L,Long::sum);results.sort(Comparator.comparing(r->(String)r.get("id")));var payload=new LinkedHashMap<String,Object>();payload.put("schemaVersion",1);payload.put("auditedAt",Instant.now().toString());payload.put("catalogTotal",manifest.versions().size());payload.put("counts",counts);payload.put("results",results);Files.createDirectories(report.getParent());Files.write(report,LauncherJson.write(payload));System.out.println(counts);
  if(counts.getOrDefault("PARSE_FAILURE",0L)>0||counts.getOrDefault("LOCAL_FAILURE",0L)>0)System.exit(1);
 }
}
