package cn.dreamingfish.launcher.downloads;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RemotePolicyTest {
 @Test void fabricMetadataAndLibrariesHaveSeparateHostsAndNoCredentials()throws Exception{
  var policy=new RemotePolicy(host->new InetAddress[]{InetAddress.getByName("8.8.8.8")});
  for(var service:List.of(RemoteService.FABRIC_META,RemoteService.FABRIC_LIBRARIES)){
   String host=service==RemoteService.FABRIC_META?"meta.fabricmc.net":"maven.fabricmc.net";
   policy.validate(new RemoteRequest(service,URI.create("https://"+host+"/file"),Map.of()));
   String other=service==RemoteService.FABRIC_META?"maven.fabricmc.net":"meta.fabricmc.net";
   assertThrows(Exception.class,()->policy.validate(new RemoteRequest(service,URI.create("https://"+other+"/file"),Map.of())));
   assertThrows(Exception.class,()->policy.validate(new RemoteRequest(service,URI.create("https://"+host+".invalid/file"),Map.of())));
   assertThrows(IllegalArgumentException.class,()->new RemoteRequest(service,URI.create("https://"+host+"/file"),Map.of("x-api-key","fixture")));
  }
 }
 @Test void packApisHaveTheirOwnExactHostsAndNeverReceiveKeys() throws Exception {
  var policy=new RemotePolicy(host->new InetAddress[]{InetAddress.getByName("8.8.8.8")});
  for(var service:List.of(RemoteService.FTB_API,RemoteService.ATLAUNCHER_API)){
   String host=service==RemoteService.FTB_API?"api.feed-the-beast.com":"api.atlauncher.com";
   policy.validate(new RemoteRequest(service,URI.create("https://"+host+"/v1/"),Map.of()));
   assertThrows(Exception.class,()->policy.validate(new RemoteRequest(service,URI.create("https://"+host+".invalid/v1"),Map.of())));
   assertThrows(IllegalArgumentException.class,()->new RemoteRequest(service,URI.create("https://"+host+"/v1/"),Map.of("x-api-key","synthetic-test-only")));
  }
 }
 @Test void exactServicesAndPublicAddressesOnly() throws Exception {
  var policy=new RemotePolicy(host->new InetAddress[]{InetAddress.getByName("8.8.8.8")});
  String[] hosts={"piston-meta.mojang.com","piston-data.mojang.com","libraries.minecraft.net","resources.download.minecraft.net","api.modrinth.com","cdn.modrinth.com","api.curseforge.com","edge.forgecdn.net"};
  for(int i=0;i<hosts.length;i++)policy.validate(new RemoteRequest(RemoteService.values()[i],URI.create("https://"+hosts[i]+"/file"),Map.of()));
  for(String url:List.of("http://api.modrinth.com/a","https://api.modrinth.com.evil/a","https://user@api.modrinth.com/a","https://api.modrinth.com:444/a","https://api.modrinth.com/a#x"))assertThrows(Exception.class,()->policy.validate(new RemoteRequest(RemoteService.MODRINTH_API,URI.create(url),Map.of())));
  for(String ip:List.of("127.0.0.1","10.0.0.1","169.254.1.1","0.0.0.0","224.0.0.1","::1","fc00::1"))assertThrows(Exception.class,()->new RemotePolicy(h->new InetAddress[]{InetAddress.getByName(ip)}).validate(new RemoteRequest(RemoteService.MODRINTH_API,URI.create("https://api.modrinth.com/a"),Map.of())));
 }
 @Test void keysCannotLeakToOtherServicesOrStrings() {
  var request=new RemoteRequest(RemoteService.CURSEFORGE_API,URI.create("https://api.curseforge.com/v1/mods"),Map.of("x-api-key","secret-fixture"));
  assertFalse(request.toString().contains("secret-fixture"));
  assertThrows(IllegalArgumentException.class,()->new RemoteRequest(RemoteService.CURSEFORGE_CDN,URI.create("https://edge.forgecdn.net/file"),request.headers()));
  assertThrows(IllegalArgumentException.class,()->new RemoteRequest(RemoteService.MODRINTH_API,URI.create("https://api.modrinth.com/a"),Map.of("Authorization","secret")));
 }
}
