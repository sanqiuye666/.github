package cn.dreamingfish.updater.player.launcher.web;
import cn.dreamingfish.updater.player.launcher.server.OfflineResourceTransport;
import cn.dreamingfish.launcher.catalog.VersionManifestParser;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.mods.*;
import cn.dreamingfish.launcher.packs.*;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Clock;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class OfflineResourceTransportTest {
    @Test void fixturesUseNormalParsersAndUnknownDownloadsFailClosed() throws Exception {
        var offline=new OfflineResourceTransport();var token=new CancellationToken();
        assertEquals(4,new VersionManifestParser().parse(offline.fetch()).versions().size());
        var clock=Clock.systemUTC();
        assertEquals(1,new FtbPackProvider(offline,clock).search(new PackQuery("",0,20),token).items().size());
        assertEquals(1,new AtLauncherPackProvider(offline,clock).search(new PackQuery("",0,20),token).items().size());
        assertEquals(1,new ModrinthPackProvider(offline,clock).search(new PackQuery("",0,20),token).items().size());
        assertThrows(java.io.IOException.class,()->offline.bytes(new RemoteRequest(RemoteService.MODRINTH_CDN,URI.create("https://cdn.modrinth.com/data/Test/mod.jar"),Map.of()),100,token));
        var cancelled=new CancellationToken();cancelled.cancel();assertThrows(java.io.InterruptedIOException.class,()->offline.bytes(new RemoteRequest(RemoteService.MODRINTH_API,URI.create("https://api.modrinth.com/v2/search"),Map.of()),100,cancelled));
    }
}
