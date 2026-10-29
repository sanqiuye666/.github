package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.*;
import java.time.Instant;
import java.util.*;
public record ModFile(ModFileRef ref,String displayName,String fileName,List<String> gameVersions,List<String> loaders,String releaseChannel,Instant publishedAt,Long size,FileDigest sourceDigest,List<ModDependency> dependencies,boolean downloadable,String blockedReason,DownloadSpec downloadSpec){
 public ModFile{Objects.requireNonNull(ref);gameVersions=List.copyOf(gameVersions);loaders=List.copyOf(loaders);dependencies=List.copyOf(dependencies);if(downloadable&&(downloadSpec==null||sourceDigest==null||size==null||blockedReason!=null)||!downloadable&&(downloadSpec!=null||blockedReason==null))throw new IllegalArgumentException("模组文件状态不一致");}
}
