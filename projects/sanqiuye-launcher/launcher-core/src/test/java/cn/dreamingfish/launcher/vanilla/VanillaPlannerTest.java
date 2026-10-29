package cn.dreamingfish.launcher.vanilla;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VanillaPlannerTest {
 @TempDir Path root;
 @Test void modernAndLegacyArgumentsStillProduceClientAndAllMappings()throws Exception {
  for(String id:List.of("26.3","1.12.2","1.5.2","c0.0.11a")){boolean legacy=!id.equals("26.3");var fixture=VanillaFixtures.setup(root.resolve("case-"+id),id,legacy,true,true);var planner=new VanillaPlanner(fixture.catalog(),fixture.store(),VanillaFixtures.WINDOWS);var plan=planner.plan(id,new CancellationToken());var paths=plan.files().stream().map(f->f.relativePath().toString().replace('\\','/')).toList();
   for(String path:List.of("versions/client.jar","versions/game.json","libraries/native/windows.jar","assets/indexes/game.json","assets/virtual/"+(legacy?"pre-1.6":"34")+"/sounds/a.ogg","resources/sounds/b.ogg","logging/client.xml"))assertTrue(paths.contains(path),path);
   assertFalse(paths.contains("libraries/native/windows32.jar"));assertEquals(legacy?8:25,plan.requiredJavaMajor());assertEquals(1,paths.stream().filter(p->p.startsWith("assets/objects/")).count());assertThrows(IllegalArgumentException.class,()->planner.plan("missing",new CancellationToken()));
  }
 }
 @Test void missingHashIncorrectMetadataAndOversizedPlansFail()throws Exception {
  var fixture=VanillaFixtures.setup(root,false,false,false);fixture.responses().put("https://piston-meta.mojang.com/game.json","{\"id\":\"wrong\"}".getBytes());assertThrows(IOException.class,()->new VanillaPlanner(fixture.catalog(),fixture.store(),VanillaFixtures.WINDOWS).plan("26.3",new CancellationToken()));
  assertThrows(IllegalArgumentException.class,()->new DownloadSpec(RemoteService.MOJANG_FILES,URI.create("https://piston-data.mojang.com/x"),Long.MAX_VALUE,Long.MAX_VALUE,new FileDigest(FileDigest.Algorithm.SHA1,"a".repeat(40)),"oversize"));
  var files=new ArrayList<InstallFile>();for(int i=0;i<11;i++)files.add(new InstallFile(Path.of("file"+i),new DownloadSpec(RemoteService.MOJANG_FILES,URI.create("https://piston-data.mojang.com/"+i),2L*1024*1024*1024,2L*1024*1024*1024,new FileDigest(FileDigest.Algorithm.SHA1,String.format("%040x",i)),"large")));
  assertThrows(IllegalArgumentException.class,()->new VanillaPlan("x","a".repeat(40),VanillaFixtures.WINDOWS,null,files,0));
  assertThrows(IllegalArgumentException.class,()->new VanillaPlan("x","a".repeat(40),VanillaFixtures.WINDOWS,null,Collections.nCopies(100001,files.getFirst()),0));
  assertThrows(IOException.class,()->AssetIndex.parse("{\"objects\":{\"../escape\":{\"hash\":\"x\",\"size\":1}}}".getBytes()));
 }
}
