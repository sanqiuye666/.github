package cn.dreamingfish.launcher.content;
import java.util.Objects;

/** 来源分类标识与展示名称；不从项目标题推断。 */
public record ContentCategory(String id,String name,String group) {
 public ContentCategory {
  if(id==null||!id.matches("[A-Za-z0-9_-]{1,64}")||name==null||name.isBlank()||name.length()>256)throw new IllegalArgumentException("来源分类格式无效");
  Objects.requireNonNull(group);
 }
}
