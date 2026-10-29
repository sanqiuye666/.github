package cn.dreamingfish.launcher.vanilla;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.regex.*;
public final class LibraryRules {
 private LibraryRules(){}
 public static boolean allowed(JsonNode rules,Platform platform,Map<String,Boolean> features){
  if(rules==null||rules.isMissingNode())return true;if(!rules.isArray())throw new IllegalArgumentException("依赖规则必须为数组");boolean allowed=false;
  for(var rule:rules){if(!rule.isObject()||unknown(rule,Set.of("action","os","features")))return false;String action=rule.path("action").asText();if(!Set.of("allow","disallow").contains(action))throw new IllegalArgumentException("未知依赖动作");boolean match=true;
   if(rule.has("os")){var os=rule.path("os");if(!os.isObject()||unknown(os,Set.of("name","version","arch")))return false;if(os.has("name"))match&=os.path("name").isTextual()&&os.path("name").asText().equals(platform.osName());if(os.has("arch"))match&=regex(os.path("arch"),platform.arch());if(os.has("version"))match&=regex(os.path("version"),platform.osVersion());}
   if(rule.has("features")){var f=rule.path("features");if(!f.isObject())return false;var keys=f.fieldNames();while(keys.hasNext()){String key=keys.next();if(!features.containsKey(key)||!f.path(key).isBoolean())return false;match&=features.get(key)==f.path(key).booleanValue();}}
   if(match)allowed=action.equals("allow");
  }return allowed;
 }
 private static boolean unknown(JsonNode n,Set<String> keys){var names=n.fieldNames();while(names.hasNext())if(!keys.contains(names.next()))return true;return false;}
 private static boolean regex(JsonNode regex,String value){if(!regex.isTextual()||regex.textValue().length()>256)throw new IllegalArgumentException("系统匹配规则无效");try{return Pattern.matches(regex.textValue(),value);}catch(PatternSyntaxException error){throw new IllegalArgumentException("系统匹配表达式无效");}}
}
