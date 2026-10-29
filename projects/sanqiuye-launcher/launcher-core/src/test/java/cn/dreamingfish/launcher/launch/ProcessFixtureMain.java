package cn.dreamingfish.launcher.launch;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 真实 Java 子程序，只检验进程边界，不是 Minecraft。 */
public final class ProcessFixtureMain {
    public static void main(String[] args) throws Exception {
        var values = Arrays.asList(args);
        if (values.contains("--fixture-output-gate")) while (!Files.exists(Path.of("fixture-output.txt"))) Thread.sleep(20);
        System.out.println("参数=" + values.get(values.indexOf("--fixture-value") + 1));
        System.out.println("工作目录=" + Path.of("").toAbsolutePath());
        System.out.println("身份=" + values.get(values.indexOf("--username") + 1));
        System.out.println("注入属性=" + System.getProperty("fixture.injected"));
        for (String key : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH"))
            System.out.println(key + "=" + System.getenv(key));
        if(values.contains("--quickPlayMultiplayer"))Files.writeString(Path.of("fixture-direct-target.txt"),values.get(values.indexOf("--quickPlayMultiplayer")+1));
        Files.writeString(Path.of("fixture-started.txt"), "started", StandardOpenOption.CREATE_NEW);
        if (values.contains("--fixture-wait")) {
            while (!Files.exists(Path.of("fixture-exit.txt"))) Thread.sleep(30);
        } else {
            for (int i = 0; i < 5000; i++) { System.out.println("输出=" + i); System.err.println("错误=" + i); }
            byte[] text = "分块中文完整\n".getBytes(StandardCharsets.UTF_8);
            for (byte b : text) { System.out.write(b); System.out.flush(); }
            System.out.print("长".repeat(30000)); System.out.println();
        }
        System.out.println("末尾中文完整");
        System.exit(7);
    }
}
