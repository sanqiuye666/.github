package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.storage.LauncherJson;
import cn.dreamingfish.launcher.vanilla.Platform;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LaunchArgumentsTest {
    @Test void legacyQuotesPreserveBoundariesWithoutShellExpansion()throws Exception {
        assertEquals(List.of("--gameDir","${game_directory}","--tweakClass","a.Tweak","--value",";$(echo hi)&"),
                LaunchArguments.splitLegacy("--gameDir \"${game_directory}\" --tweakClass a.Tweak --value ';$(echo hi)&'"));
        var value=LaunchArguments.compile("${game_directory}",Map.of("game_directory","C:\\测试 ${原样} 实例"));
        assertEquals("C:\\测试 ${原样} 实例",value.resolve(Map.of()));
        assertThrows(LaunchFailure.class,()->LaunchArguments.splitLegacy("unclosed 'quote"));
    }
    @Test void slotsAreBoundOnlyAfterLiteralSubstitution()throws Exception {
        var value=LaunchArguments.compile("-Djava.library.path=${natives_directory}/java",Map.of());
        assertEquals("-Djava.library.path=C:\\私有 目录/java",value.resolve(Map.of(LaunchArguments.Slot.NATIVES_DIRECTORY,"C:\\私有 目录")));
        assertEquals("UNSUPPORTED_LAUNCH_FORMAT",assertThrows(LaunchFailure.class,()->LaunchArguments.compile("${unknown_required}",Map.of())).code());
    }
    @Test void unknownRequiredRulesFailButKnownDisabledFeaturesSkip()throws Exception {
        var platform=new Platform("windows","10.0","amd64",64);
        var bad=LauncherJson.readValue("[{\"rules\":[{\"action\":\"allow\",\"features\":{\"unknown_feature\":true}}],\"value\":\"required\"}]".getBytes(StandardCharsets.UTF_8),1024);
        assertEquals("UNSUPPORTED_LAUNCH_FORMAT",assertThrows(LaunchFailure.class,()->LaunchArguments.read(bad,platform,Map.of(),Map.of())).code());
        var malformed=LauncherJson.readValue("[{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":42}}],\"value\":\"required\"}]".getBytes(StandardCharsets.UTF_8),1024);
        assertEquals("UNSUPPORTED_LAUNCH_FORMAT",assertThrows(LaunchFailure.class,()->LaunchArguments.read(malformed,platform,Map.of(),Map.of())).code());
        var optional=LauncherJson.readValue("[{\"rules\":[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}],\"value\":\"${not_used}\"},\"literal\"]".getBytes(StandardCharsets.UTF_8),1024);
        assertEquals(List.of("literal"),LaunchArguments.read(optional,platform,Map.of("is_demo_user",false),Map.of()).stream().map(LaunchArguments.Argument::template).toList());
    }
}
