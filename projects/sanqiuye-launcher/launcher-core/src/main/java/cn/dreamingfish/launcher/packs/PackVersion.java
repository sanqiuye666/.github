package cn.dreamingfish.launcher.packs;
import java.time.Instant;
import java.util.List;
public record PackVersion(String id,String name,List<String> gameVersions,List<String> loaders,Instant publishedAt) {
    public PackVersion {gameVersions=List.copyOf(gameVersions);loaders=List.copyOf(loaders);}
}
