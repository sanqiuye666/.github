package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.catalog.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

final class LauncherRouteFixtures {
    static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"),ZoneOffset.UTC);
    static byte[] manifest(String suffix) throws IOException {
        var versions=new ArrayList<Map<String,Object>>();
        String[] types={"release","snapshot","old_alpha","old_beta"};
        for(int i=0;i<205;i++) versions.add(Map.of("id",String.format("v%03d",i)+suffix,"type",types[i%4],
                "url","https://piston-meta.mojang.com/test/version.json","sha1","a".repeat(40),
                "releaseTime","2024-01-01T00:00:00Z","time","2024-01-02T00:00:00Z"));
        return LauncherJson.write(Map.of("latest",Map.of("release","v000"+suffix,"snapshot","v001"+suffix),"versions",versions));
    }
    static MojangVersionCatalog catalog(Path root,AtomicReference<byte[]> response) {
        return new MojangVersionCatalog(root,CLOCK,()->{if(response.get()==null)throw new IOException("controlled offline");return response.get();});
    }
    static ApiRequest get(String path,Map<String,String> query) { return new ApiRequest("GET",path,query,null); }
    static ApiRequest post(String path,Map<String,Object> body) throws IOException {
        return new ApiRequest("POST",path,Map.of(),LauncherJson.read(LauncherJson.write(body),16384));
    }
    static JsonNode body(ApiResponse response) throws IOException { return LauncherJson.read(LauncherJson.write(response.body()),1024*1024); }
}
