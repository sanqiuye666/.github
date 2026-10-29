package cn.dreamingfish.launcher.downloads;
import java.net.URI;
import java.util.*;
public record RemoteRequest(RemoteService service,URI uri,Map<String,String> headers) {
 public RemoteRequest {
  Objects.requireNonNull(service);Objects.requireNonNull(uri);Objects.requireNonNull(headers);
  var copy=new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);
  headers.forEach((name,value)->{if(name==null||value==null||value.isBlank()||value.length()>4096||value.chars().anyMatch(Character::isISOControl)||!Set.of("accept","user-agent","x-api-key").contains(name.toLowerCase(Locale.ROOT))||name.equalsIgnoreCase("x-api-key")&&service!=RemoteService.CURSEFORGE_API||copy.putIfAbsent(name,value)!=null)throw new IllegalArgumentException("不允许的请求头");});
  headers=Collections.unmodifiableMap(copy);
 }
 @Override public String toString(){return "RemoteRequest[service="+service+"]";}
}
