package cn.dreamingfish.launcher.content;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import java.io.IOException;
import java.net.URI;
import java.time.*;
import java.util.*;

/** 分类按官方项目类型隔离；仅缓存成功响应，失败不污染已有目录。 */
public final class ModrinthCategories {
 private final RemoteClient remote;private final Clock clock;private final String type;private List<ContentCategory> cached;private Instant at;
 public ModrinthCategories(RemoteClient remote,Clock clock,String type){this.remote=remote;this.clock=clock;this.type=type;}
 public synchronized List<ContentCategory> list(CancellationToken token)throws IOException{
  token.check();if(at!=null&&clock.instant().isBefore(at.plusSeconds(86400)))return cached;
  var root=LauncherJson.readValue(remote.bytes(new RemoteRequest(RemoteService.MODRINTH_API,URI.create("https://api.modrinth.com/v2/tag/category"),Map.of("Accept","application/json")),1024*1024,token),1024*1024);
  try{if(!root.isArray()||root.size()>2000)throw new IllegalArgumentException();var items=new LinkedHashMap<String,ContentCategory>();for(var item:root){if(type.equals(item.path("project_type").asText())){String name=LauncherJson.text(item,"name",64);items.put(name,new ContentCategory(name,name,Objects.requireNonNullElse(ProjectPresentation.text(item,"header",128),"categories")));}}token.check();cached=List.copyOf(items.values());at=clock.instant();return cached;}catch(IllegalArgumentException bad){throw new TaskFailure("SOURCE_INVALID_RESPONSE","官方分类目录格式无效");}
 }
 public void require(String category,CancellationToken token)throws IOException{if(!category.isEmpty()&&list(token).stream().noneMatch(c->c.id().equals(category)))throw new TaskFailure("UNKNOWN_CATEGORY","请选择此来源和资源类型提供的分类");}
}
