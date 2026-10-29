package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.downloads.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LoaderTaskTest{
 @Test void genericQueuePinsKindVersionSourceAndPlanAndRejectsPaths(){var fields=new HashMap<>(Map.of("instanceId",UUID.randomUUID().toString(),"versionId","1.20.6","metadataSha1","a".repeat(40),"loader","NEOFORGE","loaderVersion","20.6.139","sourceSha256","b".repeat(64),"planSha256","c".repeat(64)));assertEquals("NEOFORGE",new TaskRequest(TaskKind.valueOf("LOADER_INSTALL"),fields).inputs().get("loader"));for(String key:List.of("sourceSha256","planSha256","loader")){var missing=new HashMap<>(fields);missing.remove(key);assertThrows(IllegalArgumentException.class,()->new TaskRequest(TaskKind.valueOf("LOADER_INSTALL"),missing));}fields.put("loaderVersion","../bad");assertThrows(IllegalArgumentException.class,()->new TaskRequest(TaskKind.valueOf("LOADER_INSTALL"),fields));}
}
