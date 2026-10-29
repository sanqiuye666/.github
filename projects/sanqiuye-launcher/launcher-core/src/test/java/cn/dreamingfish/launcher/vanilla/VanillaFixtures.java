package cn.dreamingfish.launcher.vanilla;
import cn.dreamingfish.launcher.catalog.*;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.*;
import java.time.*;
import java.util.*;
public final class VanillaFixtures {
 public static final Platform WINDOWS=new Platform("windows","10.0","amd64",64);
 public static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"),ZoneOffset.UTC);
 public static String sha1(byte[] data){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));}catch(Exception e){throw new IllegalStateException(e);}}
 public static Map<String,Object> file(String path,byte[] data){return Map.of("path",path,"url","https://libraries.minecraft.net/"+path,"sha1",sha1(data),"size",data.length);}
 public record Setup(MojangVersionCatalog catalog,DownloadStore store,Map<String,byte[]> responses){}
 public static Setup setup(Path root,boolean legacy,boolean virtual,boolean mapped)throws IOException{
  return setup(root,legacy?"1.5.2":"26.3",legacy,virtual,mapped);
 }
 public static Setup setup(Path root,String id,boolean legacy,boolean virtual,boolean mapped)throws IOException{
  byte[] payload="fixture-binary".getBytes();var responses=new HashMap<String,byte[]>();
  var asset=Map.of("hash",sha1(payload),"size",payload.length);byte[] index=LauncherJson.write(Map.of("objects",Map.of("sounds/a.ogg",asset,"sounds/b.ogg",asset),"virtual",virtual,"map_to_resources",mapped));
  String indexUrl="https://piston-meta.mojang.com/index.json";responses.put(indexUrl,index);
  var metadata=new LinkedHashMap<String,Object>();metadata.put("id",id);metadata.put(legacy?"minecraftArguments":"arguments",legacy?"--username ${auth_player_name}":Map.of("game",List.of()));metadata.put("javaVersion",Map.of("majorVersion",legacy?8:25));metadata.put("downloads",Map.of("client",Map.of("url","https://piston-data.mojang.com/client.jar","sha1",sha1(payload),"size",payload.length)));
  metadata.put("libraries",List.of(Map.of("downloads",Map.of("artifact",file("group/artifact/1/artifact.jar",payload))),Map.of("natives",Map.of("windows","natives-windows-${arch}"),"downloads",Map.of("classifiers",Map.of("natives-windows-64",file("native/windows.jar",payload),"natives-windows-32",file("native/windows32.jar",payload))))));
  metadata.put("assetIndex",Map.of("id",legacy?"pre-1.6":"34","url",indexUrl,"sha1",sha1(index),"size",index.length));metadata.put("logging",Map.of("client",Map.of("file",Map.of("url","https://piston-data.mojang.com/log.xml","sha1",sha1(payload),"size",payload.length))));
  byte[] json=LauncherJson.write(metadata);String metadataUrl="https://piston-meta.mojang.com/game.json";responses.put(metadataUrl,json);
  for(String url:List.of("https://piston-data.mojang.com/client.jar","https://piston-data.mojang.com/log.xml","https://libraries.minecraft.net/group/artifact/1/artifact.jar","https://libraries.minecraft.net/native/windows.jar","https://resources.download.minecraft.net/"+sha1(payload).substring(0,2)+"/"+sha1(payload)))responses.put(url,payload);
  byte[] manifest=LauncherJson.write(Map.of("latest",Map.of("release",id,"snapshot",id),"versions",List.of(Map.of("id",id,"type","release","url",metadataUrl,"sha1",sha1(json),"releaseTime","2024-01-01T00:00:00Z","time","2024-01-01T00:00:00Z"))));
  var catalog=new MojangVersionCatalog(root,CLOCK,()->manifest);
  RemoteClient remote=(request,limit,token,sink)->{token.check();byte[] bytes=responses.get(request.uri().toString());if(bytes==null)throw new IOException("unexpected fixture URL");sink.accept(ByteBuffer.wrap(bytes));};
  return new Setup(catalog,new DownloadStore(root,remote),responses);
 }
}
