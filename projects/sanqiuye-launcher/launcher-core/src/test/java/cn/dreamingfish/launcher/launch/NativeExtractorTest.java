package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.downloads.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeExtractorTest {
    @TempDir Path root;
    @Test void boundsActualInflationEvenWhenExcludedDirectorySizeLies() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("META-INF/oversized.bin"));
            byte[] block = new byte[65536];
            for (int i = 0; i < 2048; i++) zip.write(block);
            zip.write(1); zip.closeEntry();
        }
        var compressed = archive(central(output.toByteArray(), 0, 1, false), List.of("META-INF/"));
        Path target = root.resolve("actual-limit");
        var error = assertThrows(LaunchFailure.class, () -> new NativeExtractor().extract(List.of(compressed), target, new CancellationToken()));
        assertTrue(error.getMessage().contains("实际展开超过 128"), error.getMessage());
        assertFalse(Files.exists(target.resolve("META-INF/oversized.bin")));
    }
    static byte[] zip(Map<String,byte[]> files)throws IOException {
        var output=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(output)){for(var file:files.entrySet()){zip.putNextEntry(new ZipEntry(file.getKey()));zip.write(file.getValue());zip.closeEntry();}}return output.toByteArray();
    }
    LaunchPlan.NativeArchive archive(byte[] data,List<String> excludes)throws Exception {
        Path file=root.resolve(UUID.randomUUID()+".jar");Files.write(file,data);
        return new LaunchPlan.NativeArchive(file,data.length,VanillaLaunchPlannerTest.hash("SHA-256",data),excludes);
    }
    static byte[] central(byte[] original,int entry,long size,boolean link) {
        byte[] data=original.clone();var buffer=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);int index=0;
        for(int offset=0;offset+46<=data.length;offset++)if(buffer.getInt(offset)==0x02014b50){
            if(index++==entry){if(size>=0)buffer.putInt(offset+24,(int)size);if(link)buffer.putInt(offset+38,0120777<<16);return data;}
        }
        throw new AssertionError("缺少中央目录测试条目");
    }
    @Test void rejectsHostileEntriesBeforeExclusion()throws Exception {
        for(String name:List.of("../escape.dll","C:/escape.dll","CON.dll","META-INF/../escape.dll")){
            var archive=archive(zip(Map.of(name,new byte[]{1})),List.of("META-INF/"));
            assertEquals("NATIVE_EXTRACTION_FAILED",assertThrows(LaunchFailure.class,()->new NativeExtractor().extract(List.of(archive),root.resolve(UUID.randomUUID().toString()),new CancellationToken())).code());
        }
        var collision=archive(zip(new LinkedHashMap<>(Map.of("A.dll",new byte[]{1},"a.dll",new byte[]{2}))),List.of());
        assertThrows(LaunchFailure.class,()->new NativeExtractor().extract(List.of(collision),root.resolve("collision"),new CancellationToken()));
        var link=archive(central(zip(Map.of("META-INF/link",new byte[]{1})),0,-1,true),List.of("META-INF/"));
        assertThrows(LaunchFailure.class,()->new NativeExtractor().extract(List.of(link),root.resolve("link"),new CancellationToken()));
        assertFalse(Files.exists(root.getParent().resolve("escape.dll")));
    }
    @Test void checksDeclaredLimitsBeforeInflating()throws Exception {
        var huge=archive(central(zip(Map.of("native.dll",new byte[]{1})),0,128L*1024*1024+1,false),List.of());
        assertTrue(assertThrows(LaunchFailure.class,()->new NativeExtractor().extract(List.of(huge),root.resolve("huge"),new CancellationToken())).getMessage().contains("128"));
        var names=new LinkedHashMap<String,byte[]>();for(int i=0;i<5;i++)names.put("native"+i+".dll",new byte[]{1});byte[] data=zip(names);
        for(int i=0;i<5;i++)data=central(data,i,i<4?128L*1024*1024:1,false);
        var total=archive(data,List.of());assertTrue(assertThrows(LaunchFailure.class,()->new NativeExtractor().extract(List.of(total),root.resolve("total"),new CancellationToken())).getMessage().contains("512"));
        var entries=new LinkedHashMap<String,byte[]>();for(int i=0;i<20001;i++)entries.put("n"+i,new byte[0]);
        var many=archive(zip(entries),List.of());assertTrue(assertThrows(LaunchFailure.class,()->new NativeExtractor().extract(List.of(many),root.resolve("entries"),new CancellationToken())).getMessage().contains("20000"));
    }
    @Test void verifiesSourceAndKeepsEachTargetPrivate()throws Exception {
        byte[] data=zip(Map.of("native.dll",new byte[]{1,2,3},"META-INF/MANIFEST.MF",new byte[]{4}));var archive=archive(data,List.of("META-INF/"));
        Path target=root.resolve("private");new NativeExtractor().extract(List.of(archive),target,new CancellationToken());
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(target.resolve("native.dll")));assertFalse(Files.exists(target.resolve("META-INF/MANIFEST.MF")));
        byte[] changed=data.clone();changed[0]^=1;Files.write(archive.archive(),changed);
        assertEquals("FILE_CHANGED",assertThrows(LaunchFailure.class,()->new NativeExtractor().extract(List.of(archive),root.resolve("bad"),new CancellationToken())).code());
    }
}
