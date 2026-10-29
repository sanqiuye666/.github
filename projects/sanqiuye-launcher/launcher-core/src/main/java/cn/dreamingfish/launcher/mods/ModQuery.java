package cn.dreamingfish.launcher.mods;
import java.util.Objects;
public record ModQuery(String query,String gameVersion,String loader,Sort sort,int offset,int limit,String category) {
 public ModQuery(String query,String gameVersion,String loader,Sort sort,int offset,int limit){this(query,gameVersion,loader,sort,offset,limit,"");}
 public enum Sort {RELEVANCE,DOWNLOADS,UPDATED}
 public ModQuery {category=text(category,64);if(!category.isEmpty()&&!category.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("分类标识无效");query=text(query,256);gameVersion=text(gameVersion,128);loader=text(loader,32);Objects.requireNonNull(sort);if(!loader.isEmpty()&&!loader.matches("[a-z0-9_-]{1,32}")||offset<0||limit<1||limit>50||offset>Integer.MAX_VALUE-limit)throw new IllegalArgumentException("模组筛选或分页无效");}
 private static String text(String value,int limit){if(value==null)return "";value=value.strip();if(value.length()>limit||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("模组查询文本无效");return value;}
}
