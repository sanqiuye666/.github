package cn.dreamingfish.launcher.downloads;
import java.io.IOException;
import java.net.*;
import java.util.*;
public final class RemotePolicy {
 @FunctionalInterface interface Resolver {InetAddress[] resolve(String host) throws IOException;}
 private final Resolver resolver;
 public RemotePolicy(){this(InetAddress::getAllByName);}
 RemotePolicy(Resolver resolver){this.resolver=resolver;}
 public void validate(RemoteRequest request) throws IOException {
  try{validateUri(request.service(),request.uri());}catch(IllegalArgumentException invalid){throw new IOException(invalid.getMessage(),invalid);}
  InetAddress[] addresses=resolver.resolve(request.uri().getHost());if(addresses.length==0)throw new IOException("官方来源无法解析");
  for(InetAddress address:addresses)if(!publicAddress(address))throw new IOException("官方来源解析到了非公网地址");
 }
 public static void validateUri(RemoteService service,URI uri) {
  String host=uri.getHost();
  Set<String> hosts=switch(service){
   case MOJANG_META->Set.of("piston-meta.mojang.com","launchermeta.mojang.com");
   case MOJANG_FILES->Set.of("piston-data.mojang.com","launcher.mojang.com");
   case MOJANG_LIBRARIES->Set.of("libraries.minecraft.net");case MOJANG_ASSETS->Set.of("resources.download.minecraft.net");
   case MODRINTH_API->Set.of("api.modrinth.com");case MODRINTH_CDN->Set.of("cdn.modrinth.com");
   case CURSEFORGE_API->Set.of("api.curseforge.com");case CURSEFORGE_CDN->Set.of("edge.forgecdn.net","mediafilez.forgecdn.net");
   case FTB_API->Set.of("api.feed-the-beast.com");case ATLAUNCHER_API->Set.of("api.atlauncher.com");
   case FABRIC_META->Set.of("meta.fabricmc.net");case FABRIC_LIBRARIES->Set.of("maven.fabricmc.net");
   case QUILT_META->Set.of("meta.quiltmc.org");case QUILT_LIBRARIES->Set.of("maven.quiltmc.org");case FORGE_MAVEN->Set.of("maven.minecraftforge.net");case NEOFORGE_MAVEN->Set.of("maven.neoforged.net");};
  if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||!hosts.contains(host.toLowerCase(Locale.ROOT))||uri.getPort()!=-1&&uri.getPort()!=443||uri.getRawUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("仅允许指定官方 HTTPS 来源");
 }
 private static boolean publicAddress(InetAddress a){
  if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;
  byte[] b=a.getAddress();if(b.length==16)return (b[0]&0xfe)!=0xfc;
  int first=b[0]&255,second=b[1]&255;return first!=0&&first!=127&&first<224&&!(first==100&&second>=64&&second<=127)&&!(first==198&&(second==18||second==19));
 }
}
