package cn.dreamingfish.launcher.vanilla;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SafeInstallPathTest {
 @Test void rejectsTraversalDevicesAndNormalizedCollisions(){
  for(String p:List.of("../a","a/../b","/abs","a//b","a\\b","C:a","a/CON.txt","a/LPT1","a./x","a /x","a/./b","a\u0000b"))assertThrows(IllegalArgumentException.class,()->SafeInstallPath.parse(p),p);
  for(List<Path> paths:List.of(List.of(Path.of("mods/A.jar"),Path.of("mods/a.jar")),List.of(Path.of("a"),Path.of("a/b")),List.of(Path.of("é.jar"),Path.of("e\u0301.jar"))))assertThrows(IllegalArgumentException.class,()->SafeInstallPath.checkDistinct(paths));
 }
}
