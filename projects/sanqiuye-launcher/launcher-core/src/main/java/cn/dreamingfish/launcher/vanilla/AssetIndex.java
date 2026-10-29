package cn.dreamingfish.launcher.vanilla;
import cn.dreamingfish.launcher.storage.LauncherJson;
import java.io.*;
import java.util.*;
public record AssetIndex(Map<String,Asset> objects,boolean virtual,boolean mapToResources) {
 public record Asset(String hash,long size){}
 public static AssetIndex parse(byte[] bytes)throws IOException{
  try{var n=LauncherJson.read(bytes,32*1024*1024);if(!n.path("objects").isObject()||n.path("objects").size()>100000||n.has("virtual")&&!n.path("virtual").isBoolean()||n.has("map_to_resources")&&!n.path("map_to_resources").isBoolean())throw new IllegalArgumentException("资源索引结构无效");var objects=new LinkedHashMap<String,Asset>();var fields=n.path("objects").fields();while(fields.hasNext()){var e=fields.next();SafeInstallPath.parse(e.getKey());var value=e.getValue();String hash=LauncherJson.text(value,"hash",40);if(!hash.matches("[0-9a-f]{40}")||!value.path("size").isIntegralNumber()||!value.path("size").canConvertToLong()||value.path("size").longValue()<0||value.path("size").longValue()>2L*1024*1024*1024)throw new IllegalArgumentException("资源摘要或长度无效");objects.put(e.getKey(),new Asset(hash,value.path("size").longValue()));}return new AssetIndex(Map.copyOf(objects),n.path("virtual").asBoolean(false),n.path("map_to_resources").asBoolean(false));}catch(IllegalArgumentException error){throw new IOException("资源索引无效："+error.getMessage(),error);}
 }
}
