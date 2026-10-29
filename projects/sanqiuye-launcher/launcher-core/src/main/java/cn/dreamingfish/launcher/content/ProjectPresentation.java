package cn.dreamingfish.launcher.content;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;

/** 只读项目展示。图片由浏览器普通显示，不附加启动器认证头；不授予下载或安装权限。 */
public record ProjectPresentation(String iconUrl,String description,List<ContentCategory> categories,List<String> gallery,List<String> gameVersions,List<String> loaders,String publishedAt,String updatedAt,String clientSide,String serverSide,Map<String,String> links) {
 /** 展示预算低于通用JSON字符串上限；超出仅省略正文，不放宽协议解析。 */
 public static final int MAX_DESCRIPTION=64*1024;
 private static final Set<String> IMAGE_HOSTS=Set.of("cdn.modrinth.com","media.forgecdn.net","mediafilez.forgecdn.net","cdn.feed-the-beast.com","cdn.atlcdn.net");
 public ProjectPresentation {categories=List.copyOf(categories);gallery=List.copyOf(gallery);gameVersions=List.copyOf(gameVersions);loaders=List.copyOf(loaders);links=Map.copyOf(links);}
 public static ProjectPresentation empty(){return new ProjectPresentation(null,"",List.of(),List.of(),List.of(),List.of(),null,null,null,null,Map.of());}
 public static String image(String raw){String value=web(raw);if(value==null)return null;return IMAGE_HOSTS.contains(URI.create(value).getHost().toLowerCase(Locale.ROOT))?value:null;}
 public static String web(String raw){try{if(raw==null||raw.length()>4096||raw.chars().anyMatch(Character::isISOControl))return null;var uri=URI.create(raw);return "https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getRawUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443)?uri.toASCIIString():null;}catch(IllegalArgumentException bad){return null;}}
 public static String text(JsonNode n,String key,int limit){var value=n.path(key);if(!value.isTextual())return null;String raw=value.textValue();if(raw.length()>limit)return null;return raw;}
 public static List<String> strings(JsonNode n){if(n.isMissingNode()||n.isNull())return List.of();if(!n.isArray()||n.size()>10000)return List.of();var out=new LinkedHashSet<String>();for(var v:n){if(!v.isTextual()||v.textValue().length()>128)continue;out.add(v.textValue());}return List.copyOf(out);}
 public static List<ContentCategory> categories(JsonNode n,boolean numeric){if(n.isMissingNode()||n.isNull())return List.of();if(!n.isArray()||n.size()>256)return List.of();var out=new LinkedHashMap<String,ContentCategory>();for(var v:n){String id=numeric?v.path("id").asText():v.asText();if(Set.of("fabric","forge","quilt","neoforge","vanilla","liteloader","rift","modloader").contains(id))continue;try{out.put(id,new ContentCategory(id,numeric?text(v,"name",256):id,"categories"));}catch(IllegalArgumentException invalid){/* 可选展示分类损坏时跳过，不影响文件校验。 */}}return List.copyOf(out.values());}
 public static ProjectPresentation modrinth(JsonNode n){
  var images=new ArrayList<String>();if(n.path("gallery").isArray()&&n.path("gallery").size()<=256){for(var item:n.path("gallery")){String url=image(item.isTextual()?item.asText():text(item,"url",4096));if(url!=null&&!images.contains(url))images.add(url);}}
  var links=new LinkedHashMap<String,String>();for(String key:List.of("wiki_url","source_url","issues_url","discord_url")){String value=web(text(n,key,4096));if(value!=null)links.put(key,value);}
  var tags=new LinkedHashMap<String,ContentCategory>();for(var key:List.of("categories","additional_categories"))for(var tag:categories(n.path(key),false))tags.put(tag.id(),tag);
  var loaders=strings(n.path("loaders"));if(loaders.isEmpty())loaders=strings(n.path("categories")).stream().filter(Set.of("fabric","forge","quilt","neoforge","vanilla")::contains).toList();
  return new ProjectPresentation(image(text(n,"icon_url",4096)),Objects.requireNonNullElse(text(n,"body",MAX_DESCRIPTION),""),List.copyOf(tags.values()),images,strings(n.has("game_versions")?n.path("game_versions"):n.path("versions")),loaders,text(n,n.has("published")?"published":"date_created",64),text(n,n.has("updated")?"updated":"date_modified",64),text(n,"client_side",32),text(n,"server_side",32),links);
 }
 public static ProjectPresentation curseforge(JsonNode n){
  var images=new ArrayList<String>();if(n.path("screenshots").isArray()&&n.path("screenshots").size()<=256){for(var item:n.path("screenshots")){String url=image(text(item,"url",4096));if(url!=null)images.add(url);}}
  var links=new LinkedHashMap<String,String>();for(String key:List.of("websiteUrl","wikiUrl","issuesUrl","sourceUrl")){String value=web(text(n.path("links"),key,4096));if(value!=null)links.put(key,value);}
  return new ProjectPresentation(image(text(n.path("logo"),"url",4096)),"",categories(n.path("categories"),true),images,List.of(),List.of(),text(n,"dateCreated",64),text(n,"dateModified",64),null,null,links);
 }
}
