package cn.dreamingfish.launcher.downloads;
import java.net.URI;
import java.util.*;
public record DownloadSpec(RemoteService service,URI uri,long expectedSize,long maxBytes,FileDigest digest,String displayName) {
 public DownloadSpec {Objects.requireNonNull(service);Objects.requireNonNull(uri);Objects.requireNonNull(digest);RemotePolicy.validateUri(service,uri);if(maxBytes<1||maxBytes>2L*1024*1024*1024||expectedSize>maxBytes||expectedSize<0&&(expectedSize!=-1||!Set.of(RemoteService.MOJANG_META,RemoteService.FABRIC_META,RemoteService.FABRIC_LIBRARIES,RemoteService.QUILT_META,RemoteService.QUILT_LIBRARIES,RemoteService.FORGE_MAVEN,RemoteService.NEOFORGE_MAVEN).contains(service))||service==RemoteService.MOJANG_META&&maxBytes>32L*1024*1024||Set.of(RemoteService.FABRIC_META,RemoteService.QUILT_META).contains(service)&&maxBytes>2L*1024*1024||service==RemoteService.FABRIC_LIBRARIES&&maxBytes>64L*1024*1024||Set.of(RemoteService.QUILT_LIBRARIES,RemoteService.FORGE_MAVEN,RemoteService.NEOFORGE_MAVEN).contains(service)&&maxBytes>128L*1024*1024)throw new IllegalArgumentException("下载长度或上限无效");if(displayName==null||displayName.length()>256||displayName.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("文件名称无效");}
 @Override public String toString(){return "DownloadSpec[service="+service+",size="+expectedSize+"]";}
}
