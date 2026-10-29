package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.catalog.*;
import cn.dreamingfish.launcher.instances.FileInstanceCatalog;
import cn.dreamingfish.launcher.storage.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LaunchSettingsStoreTest {
    @TempDir Path root;
    String instance(String name)throws Exception {
        return new FileInstanceCatalog(root,Clock.systemUTC()).create(name,"1.20.1",new VersionManifestParser().parse(ManifestFixtures.bytes())).instance().id();
    }
    static LaunchSettings settings(List<String> extra){return new LaunchSettings(null,256,2048,960,540,extra,false);}
    @Test void settingsStayInsideInstanceAndRejectOverrides()throws Exception {
        String first=instance("生存 A"),second=instance("快照 B");var store=new LaunchSettingsStore(root);
        Path sentinel=root.resolve("instances/"+second+"/.minecraft/options.txt");Files.writeString(sentinel,"保留配置");
        var original=store.read(first);assertEquals(2048,original.settings().maximumMemoryMiB());
        assertEquals(960,original.settings().width());assertEquals(540,original.settings().height());
        var other=store.read(second);var chosen=new LaunchSettings("a".repeat(64),512,4096,1280,720,List.of("-XX:+UseG1GC","-Dlabel=C:\\游戏 文件"),false);
        var saved=store.save(first,original.revision(),chosen);assertEquals(chosen,new LaunchSettingsStore(root).read(first).settings());
        assertNotEquals(original.revision(),saved.revision());assertEquals(other,store.read(second));assertEquals("保留配置",Files.readString(sentinel));
        assertEquals("SETTINGS_CHANGED",assertThrows(LaunchFailure.class,()->store.save(first,original.revision(),settings(List.of()))).code());
        assertEquals(chosen,store.read(first).settings());
        for(String arg:new String[]{"-cp","--class-path=outside","-Djava.class.path=C:\\outside","-javaagent:x.jar","-agentlib:jdwp","@args","-Xmx4G","-XX:MaxHeapSize=4G","--gameDir=C:\\outside","MainClass","-Dfoo=a\nb","-XX:OnError=cmd","-XX:VMOptionsFile=opts"}){
            assertThrows(IllegalArgumentException.class,()->settings(List.of(arg)),arg);
        }
        assertThrows(IllegalArgumentException.class,()->settings(List.of("-cp","C:\\outside")));
        assertThrows(IllegalArgumentException.class,()->store.read("../outside"));
        assertThrows(LaunchFailure.class,()->store.save(UUID.randomUUID().toString(),original.revision(),chosen));
        assertFalse(Files.exists(root.resolve("accounts.json")));
    }
    @Test void validatesMemoryWindowAndBoundedArgumentLists() {
        for(int[] values:new int[][]{{255,2048,960,540},{256,32769,960,540},{4096,2048,960,540},{256,2048,639,540},{256,2048,960,4321}}){
            assertThrows(IllegalArgumentException.class,()->new LaunchSettings(null,values[0],values[1],values[2],values[3],List.of(),false));
        }
        assertThrows(IllegalArgumentException.class,()->settings(Collections.nCopies(33,"-XX:+UseG1GC")));
        assertThrows(IllegalArgumentException.class,()->settings(List.of("-Dlabel="+"x".repeat(4096))));
        assertThrows(IllegalArgumentException.class,()->settings(Collections.nCopies(5,"-Dlabel="+"x".repeat(4088))));
        assertEquals(16384,settings(Collections.nCopies(4,"-Dlabel="+"x".repeat(4088))).extraJvmArgs().stream().mapToInt(String::length).sum());
    }
    @Test void damagedSettingsAreReportedAndPreserved()throws Exception {
        String id=instance("独立设置");var store=new LaunchSettingsStore(root);var before=store.read(id);
        store.save(id,before.revision(),settings(List.of()));Path file=root.resolve("instances/"+id+"/launch-settings.json");
        var json=(ObjectNode)LauncherJson.read(Files.readAllBytes(file),1024*1024);
        ((ObjectNode)json.path("payload").path("settings")).put("maximumMemoryMiB",8192);
        byte[] changed=LauncherJson.write(json);Files.write(file,changed);
        assertEquals("SETTINGS_INVALID",assertThrows(LaunchFailure.class,()->store.read(id)).code());
        assertArrayEquals(changed,Files.readAllBytes(file));
    }
}
