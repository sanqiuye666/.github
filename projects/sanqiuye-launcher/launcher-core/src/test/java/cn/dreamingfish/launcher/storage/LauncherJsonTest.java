package cn.dreamingfish.launcher.storage;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class LauncherJsonTest {
 @Test void arraysAreExplicitAndStrict() throws Exception {
  assertTrue(LauncherJson.readValue("[]".getBytes(),16).isArray());
  for(String text:new String[]{"[]","{\"x\":1,\"x\":2}","{} {}","null"})assertThrows(IOException.class,()->LauncherJson.read(text.getBytes(StandardCharsets.UTF_8),128));
  assertThrows(IOException.class,()->LauncherJson.readValue("[{\"x\":1,\"x\":2}]".getBytes(),128));
 }
}
