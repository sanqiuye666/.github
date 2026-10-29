package cn.dreamingfish.launcher.runtime;

import cn.dreamingfish.launcher.downloads.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class JavaProbeTest {
    @TempDir Path root;
    static Path actualJava(){return Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");}
    static Process fixture(String mode,String version,String bits)throws IOException {
        return new ProcessBuilder(actualJava().toString(),"-cp",System.getProperty("java.class.path"),Fixture.class.getName(),mode,version,bits).start();
    }
    Path executable(String name)throws IOException {
        Path file=root.resolve(name).resolve("bin").resolve(actualJava().getFileName());
        Files.createDirectories(file.getParent());Files.copy(actualJava(),file);return file;
    }
    @Test void detectsTheActualInstalledJava()throws Exception {
        var result=new JavaProbe().probe(actualJava(),new CancellationToken());
        assertEquals(21,result.majorVersion());assertEquals(64,result.bits());
        assertEquals(actualJava().toRealPath(),result.executable());assertFalse(result.vendor().isBlank());
    }
    @Test void detectsActualVersionAndBoundsOutput()throws Exception {
        for(var row:List.of(new String[]{"1.8.0_402","32","8"},new String[]{"21.0.12","64","21"},new String[]{"25.0.1","64","25"})){
            Path file=executable("jdk25 实际"+row[0]);var command=new AtomicReference<List<String>>();
            var probe=new JavaProbe(Duration.ofSeconds(5),args->{command.set(List.copyOf(args));return fixture("valid",row[0],row[1]);});
            var value=probe.probe(file,new CancellationToken());
            assertEquals(Integer.parseInt(row[2]),value.majorVersion());assertEquals(Integer.parseInt(row[1]),value.bits());
            assertEquals(List.of(file.toRealPath().toString(),"-XshowSettings:properties","-version"),command.get());
        }
        var overflow=new JavaProbe(Duration.ofSeconds(5),args->fixture("overflow","21","64"));
        assertThrows(IOException.class,()->overflow.probe(actualJava(),new CancellationToken()));
    }
    @Test void rejectsTimeoutNonzeroMalformedAndScript()throws Exception {
        for(String mode:List.of("exit","bad")){
            var probe=new JavaProbe(Duration.ofSeconds(5),args->fixture(mode,"21","64"));
            assertThrows(IOException.class,()->probe.probe(actualJava(),new CancellationToken()));
        }
        var timeout=new JavaProbe(Duration.ofMillis(250),args->fixture("wait","21","64"));
        long before=System.nanoTime();assertThrows(IOException.class,()->timeout.probe(actualJava(),new CancellationToken()));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)<2500);
        Path script=root.resolve("java.cmd");Files.writeString(script,"echo not-java");
        assertThrows(IOException.class,()->new JavaProbe().probe(script,new CancellationToken()));
    }
    @Test void keepsPartialStdoutSeparateFromStderrProperties()throws Exception {
        var probe=new JavaProbe(Duration.ofSeconds(5),args->fixture("split","21","64"));
        var value=probe.probe(actualJava(),new CancellationToken());
        assertEquals(21,value.majorVersion());assertEquals(64,value.bits());
    }
    @Test void cancelsTheOwnedProbeProcess()throws Exception {
        var running=new AtomicReference<Process>();var started=new CountDownLatch(1);var token=new CancellationToken();
        var probe=new JavaProbe(Duration.ofSeconds(5),args->{Process process=fixture("wait","21","64");running.set(process);started.countDown();return process;});
        try(var pool=Executors.newSingleThreadExecutor()){
            var result=pool.submit(()->probe.probe(actualJava(),token));assertTrue(started.await(3,TimeUnit.SECONDS));token.cancel();
            assertInstanceOf(InterruptedIOException.class,assertThrows(ExecutionException.class,()->result.get(3,TimeUnit.SECONDS)).getCause());
            assertTrue(running.get().waitFor(2,TimeUnit.SECONDS));
        }
    }
    public static class Fixture {
        public static void main(String[] args)throws Exception {
            switch(args[0]){
                case "wait" -> Thread.sleep(30000);
                case "exit" -> System.exit(7);
                case "bad" -> System.out.println("not a Java property report");
                case "overflow" -> {System.out.write(new byte[65537]);System.out.flush();Thread.sleep(30000);}
                case "split" -> {System.out.print("    sun.arch.data.model = ");System.out.flush();Thread.sleep(200);System.err.println("    java.version = 21");System.err.println("    java.vendor = 测试运行环境");Thread.sleep(200);System.out.println("64");}
                default -> {System.err.println("    java.version = "+args[1]);System.out.println("    sun.arch.data.model = "+args[2]);System.err.println("    java.vendor = 测试运行环境");}
            }
        }
    }
}
