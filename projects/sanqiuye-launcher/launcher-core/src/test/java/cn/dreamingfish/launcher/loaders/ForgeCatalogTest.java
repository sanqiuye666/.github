package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.GameInstance.LoaderKind;
import org.junit.jupiter.api.Test;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class ForgeCatalogTest {
 @Test void exactGameBoundarySortingAndNoExternalEntities()throws Exception{
  String xml="<metadata><versioning><versions><version>1.20.6-50.1.9</version><version>1.20.60-99.0.0</version><version>1.20.6-50.1.10</version></versions></versioning></metadata>";
  assertEquals(java.util.List.of("50.1.10","50.1.9"),ForgeCatalog.parse(xml.getBytes(StandardCharsets.UTF_8),LoaderKind.FORGE,"1.20.6"));
  assertEquals(java.util.List.of("20.6.144"),ForgeCatalog.parse(xml.replace("1.20.6-50.1.9","20.6.144").getBytes(StandardCharsets.UTF_8),LoaderKind.NEOFORGE,"1.20.6"));
  assertThrows(java.io.IOException.class,()->ForgeCatalog.parse("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///outside'>]><metadata>&e;</metadata>".getBytes(StandardCharsets.UTF_8),LoaderKind.FORGE,"1.20.6"));
 }
}
