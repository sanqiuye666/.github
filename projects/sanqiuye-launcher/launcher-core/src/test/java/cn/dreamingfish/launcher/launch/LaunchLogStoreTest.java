package cn.dreamingfish.launcher.launch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LaunchLogStoreTest {
    @TempDir Path root;
    @Test void redactsCredentialsAcrossLongLineSegments() throws Exception {
        try (var logs = new LaunchLogStore(root, "instances/" + UUID.randomUUID() + "/launches/" + UUID.randomUUID())) {
            String secret = "long-private-value";
            logs.consume(new ByteArrayInputStream(("x".repeat(4090) + "accessToken=" + secret.repeat(400) + "\n公开中文\n").getBytes(StandardCharsets.UTF_8)), "stdout");
            String text = String.join("\n", logs.page(null, 500).lines());
            assertFalse(text.contains(secret)); assertTrue(text.contains("公开中文"));
        }
    }
    @Test void rotatesAndBoundsPagesWithoutLosingUtf8Boundaries() throws Exception {
        var logs = new LaunchLogStore(root, "instances/" + UUID.randomUUID() + "/launches/" + UUID.randomUUID());
        for (int i = 0; i < 5000; i++) logs.append("stdout", "行" + i + "中".repeat(2400));
        logs.consume(new ByteArrayInputStream(("尾部中文\n" + "长".repeat(30000) + "\naccessToken=secret-token\n").getBytes(StandardCharsets.UTF_8)), "stderr");
        var files = Files.list(logs.directory()).filter(p -> p.getFileName().toString().endsWith(".log")).toList();
        assertTrue(files.size() <= 4); assertTrue(files.stream().mapToLong(p -> { try { return Files.size(p); } catch (IOException e) { throw new UncheckedIOException(e); }}).sum() <= 4L * 8 * 1024 * 1024);
        var first = logs.page("0:0", 500); assertTrue(first.truncated()); assertEquals(500, first.lines().size());
        String cursor = first.nextCursor(); var text = new StringBuilder();
        for (int i = 0; i < 100; i++) { var page = logs.page(cursor, 500); assertTrue(page.lines().size() <= 500); page.lines().forEach(text::append); if (cursor.equals(page.nextCursor())) break; cursor = page.nextCursor(); }
        assertTrue(text.toString().contains("尾部中文")); assertFalse(text.toString().contains("�")); assertFalse(text.toString().contains("secret-token"));
        assertThrows(IllegalArgumentException.class, () -> logs.page(null, 501));
        assertThrows(IllegalArgumentException.class, () -> logs.page("../invalid", 1));
        logs.close();
        assertFalse(new LaunchLogStore(root, root.relativize(logs.directory().getParent()).toString()).page(cursor, 1).truncated());
    }
}
