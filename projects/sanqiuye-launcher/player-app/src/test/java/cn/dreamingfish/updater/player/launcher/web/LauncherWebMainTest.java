package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.updater.player.launcher.LauncherWebMain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LauncherWebMainTest {
    @TempDir Path root;
    @Test void rejectsRelativeDataRoot() {
        for (String[] args : new String[][]{{"--data-root","relative"},{},{"--port","0"},
                {"--data-root",root.toString(),"--data-root",root.toString()},
                {"--data-root",root.toString(),"--unknown","x"},
                {"--data-root",root.toString(),"--port","65536"}}) {
            var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
            assertEquals(2,LauncherWebMain.run(args,new PrintStream(out),new PrintStream(err)));
            assertEquals("",out.toString());assertFalse(err.toString().contains("sessionToken"));
        }
    }
    @Test void occupiedPortIsAnError() throws Exception {
        try(var socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
            var out=new ByteArrayOutputStream();
            assertEquals(3,LauncherWebMain.run(new String[]{"--data-root",root.toString(),"--port",Integer.toString(socket.getLocalPort())},new PrintStream(out),new PrintStream(OutputStream.nullOutputStream())));
            assertEquals("",out.toString());
        }
    }
}
