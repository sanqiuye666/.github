package cn.dreamingfish.launcher.mods;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
final class ModProtocol {
 private ModProtocol(){}
 static String id(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException("平台标识无效");return id;}
 static String text(JsonNode n,String key,int max){var v=n.get(key);if(v==null||v.isNull())return null;if(!v.isTextual()||v.textValue().length()>max||v.textValue().chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))throw new IllegalArgumentException("平台文本字段无效");return v.textValue();}
 static Long number(JsonNode n,String key){var v=n.get(key);if(v==null||v.isNull())return null;if(!v.isIntegralNumber()||!v.canConvertToLong()||v.longValue()<0)throw new IllegalArgumentException("平台数值字段无效");return v.longValue();}
 static long requiredNumber(JsonNode n,String key){Long result=number(n,key);if(result==null)throw new IllegalArgumentException("平台缺少数值字段");return result;}
 static List<String> strings(JsonNode n,int max){if(!n.isArray()||n.size()>max)throw new IllegalArgumentException("平台标签数组无效");var result=new ArrayList<String>();for(var v:n){if(!v.isTextual()||v.textValue().isBlank()||v.textValue().length()>128||v.textValue().chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("平台标签无效");result.add(v.textValue());}return List.copyOf(result);}
 static Instant instant(JsonNode n,String key){String value=text(n,key,64);return value==null?null:Instant.parse(value);}
 static String json(Object value)throws IOException{return new String(LauncherJson.write(value),StandardCharsets.UTF_8);}
 static URI uri(String base,Map<String,String> query){var values=new ArrayList<String>();query.forEach((k,v)->values.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));return URI.create(base+(values.isEmpty()?"":"?"+String.join("&",values)));}
 static String fileKey(String name,FileDigest hash,String type){String content=Normalizer.normalize(name,Normalizer.Form.NFC)+"\n"+(hash==null?"NO_DIGEST":hash.algorithm()+":"+hash.hex())+"\n"+type;return HexFormat.of().formatHex(new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest().digest(content.getBytes(StandardCharsets.UTF_8)));}
 static boolean ordinary(String name){if(name==null)return false;String lower=name.toLowerCase(Locale.ROOT);return (lower.endsWith(".jar")||lower.endsWith(".litemod"))&&!lower.matches(".*(?:^|[-_.])(?:sources?|dev|javadoc|signature)(?:[-_.]|$).*");}
 static boolean compatible(ModFile file,String version,String loader){return (version.isEmpty()||file.gameVersions().contains(version))&&(loader.isEmpty()||file.loaders().contains(loader));}
 static IOException invalid(RuntimeException error){return new IOException("官方模组数据格式无效",error);}
}
