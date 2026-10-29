package cn.dreamingfish.launcher.runtime;

import cn.dreamingfish.launcher.downloads.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class JavaRuntimeServiceTest {
    @TempDir Path root;
    Path home(String name)throws Exception {
        Path home=root.resolve(name);Files.createDirectories(home.resolve("bin"));
        Files.copy(JavaProbeTest.actualJava(),home.resolve("bin").resolve(JavaProbeTest.actualJava().getFileName()));return home;
    }
    @Test void boundsAndDeduplicatesCandidates()throws Exception {
        var homes=new ArrayList<Path>();Path first=home("jdk25 实际8");homes.add(first);homes.add(first.resolve("."));
        for(int i=0;i<70;i++)homes.add(home("候选 "+i));var calls=new AtomicInteger();
        var probe=new JavaProbe(Duration.ofSeconds(5),args->{calls.incrementAndGet();return JavaProbeTest.fixture("valid","1.8.0_402","64");});
        var service=new JavaRuntimeService(()->homes,probe);
        assertEquals(64,service.discover(new CancellationToken()).size());assertEquals(64,calls.get());
        assertEquals(64,service.runtimes().size());assertTrue(service.select(25,64).isEmpty());
        assertEquals(8,service.select(8,64).orElseThrow().majorVersion());assertTrue(service.select(8,32).isEmpty());
    }
    @Test void registersHomeAndSeparatesFailedCandidates()throws Exception {
        Path valid=home("JDK 含空格"),invalid=Files.createDirectories(root.resolve("非Java"));
        var service=new JavaRuntimeService(()->List.of(valid,invalid),new JavaProbe(Duration.ofSeconds(5),args->JavaProbeTest.fixture("valid","21.0.12","64")));
        var values=service.discover(new CancellationToken());assertEquals(1,values.size());assertEquals(1,service.diagnostics().size());
        assertEquals(values.getFirst(),service.require(values.getFirst().id()));
        assertEquals(values.getFirst(),service.registerHome(valid,new CancellationToken()));
        assertTrue(service.select(25,64).isEmpty());assertThrows(java.io.IOException.class,()->service.require("not-registered"));
        assertThrows(IllegalArgumentException.class,()->service.registerHome(Path.of("relative"),new CancellationToken()));
    }
}
