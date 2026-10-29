package cn.dreamingfish.launcher.packs;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** 三个平台共享的有界解析；不读取安装指令或执行HTML。 */
final class PackProtocol {
    static final int LIMIT=4*1024*1024;
    static String id(PackSource source,String value){
        String pattern=switch(source){case FTB->"[1-9][0-9]{0,9}";case ATLAUNCHER->"[a-zA-Z0-9]{1,128}";default->"[a-zA-Z0-9_-]{1,128}";};
        if(value==null||!value.matches(pattern)||source==PackSource.FTB&&Long.parseLong(value)>Integer.MAX_VALUE)throw new IllegalArgumentException("平台整合包标识无效");return value;
    }
    static JsonNode get(RemoteClient remote,RemoteService service,String url,Map<String,String> query,CancellationToken token)throws IOException {
        var parameters=new StringJoiner("&");query.forEach((key,value)->parameters.add(URLEncoder.encode(key,StandardCharsets.UTF_8)+"="+URLEncoder.encode(value,StandardCharsets.UTF_8)));
        return LauncherJson.readValue(remote.bytes(new RemoteRequest(service,URI.create(url+(parameters.length()==0?"":"?"+parameters)),Map.of("Accept","application/json")),LIMIT,token),LIMIT);
    }
    static List<JsonNode> array(JsonNode node,int maximum)throws IOException {
        if(!node.isArray()||node.size()>maximum)throw invalid();var result=new ArrayList<JsonNode>();node.forEach(result::add);return result;
    }
    static String required(JsonNode node,String key,int maximum)throws IOException {try{return LauncherJson.text(node,key,maximum);}catch(IllegalArgumentException bad){throw invalid();}}
    static String plain(JsonNode node,String key,int maximum)throws IOException {
        var value=node.get(key);if(value==null||value.isNull())return "";if(!value.isTextual()||value.textValue().length()>262144)throw invalid();
        String raw=value.textValue();var out=new StringBuilder();boolean space=false;
        for(int i=0;i<raw.length()&&out.length()<maximum;i++){char c=raw.charAt(i);if(Character.isWhitespace(c)){space=out.length()>0;continue;}if(Character.isISOControl(c))throw invalid();if(space){out.append(' ');space=false;}out.append(c);}
        return out.toString().strip();
    }
    static long number(JsonNode node,String key)throws IOException {var value=node.path(key);if(!value.isIntegralNumber()||!value.canConvertToLong()||value.longValue()<0)throw invalid();return value.longValue();}
    static List<String> tags(JsonNode node)throws IOException {
        if(node.isMissingNode()||node.isNull())return List.of();var result=new TreeSet<String>();for(var item:array(node,10000)){if(!item.isTextual()||!item.textValue().matches("[a-zA-Z0-9_.+\\-]{1,128}"))throw invalid();result.add(item.textValue());}return List.copyOf(result);
    }
    static Instant seconds(JsonNode node,String key)throws IOException {if(!node.hasNonNull(key))return null;try{return Instant.ofEpochSecond(number(node,key));}catch(RuntimeException bad){throw invalid();}}
    static List<String> distinctVersions(List<PackVersion> versions,boolean loaders){return versions.stream().flatMap(v->(loaders?v.loaders():v.gameVersions()).stream()).distinct().sorted().toList();}
    static IOException invalid(){return new TaskFailure("SOURCE_INVALID_RESPONSE","平台返回的整合包目录格式无效或超过上限");}
    static IOException absent(){return new TaskFailure("PACK_NOT_FOUND","该平台公开目录中没有此整合包");}
}
