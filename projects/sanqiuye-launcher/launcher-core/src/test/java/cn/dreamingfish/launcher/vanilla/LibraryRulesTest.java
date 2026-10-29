package cn.dreamingfish.launcher.vanilla;
import cn.dreamingfish.launcher.storage.LauncherJson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LibraryRulesTest {
 @Test void rulesAreOrderedAndUnknownConditionsFailClosed()throws Exception{
  var win=new Platform("windows","10.0","amd64",64);var linux=new Platform("linux","6.6","aarch64",64);
  var rules=LauncherJson.readValue("[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\"windows\",\"version\":\"10.*\",\"arch\":\"amd64\"}}]".getBytes(),4096);
  assertFalse(LibraryRules.allowed(rules,win,Map.of()));assertTrue(LibraryRules.allowed(rules,linux,Map.of()));
  assertFalse(LibraryRules.allowed(LauncherJson.readValue("[{\"action\":\"allow\",\"features\":{\"unknown\":false}}]".getBytes(),1024),win,Map.of()));
  assertFalse(LibraryRules.allowed(LauncherJson.readValue("[{\"action\":\"allow\",\"future\":true}]".getBytes(),1024),win,Map.of()));
  assertEquals(64,Platform.current().bits());
 }
}
