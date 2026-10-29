package cn.dreamingfish.launcher.packs;
import cn.dreamingfish.launcher.downloads.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.time.*;
import java.util.*;

/** FTB新官方根；搜索的FTB packs与CurseForge编号严格分离。 */
public final class FtbPackProvider implements PackProvider {
    private final RemoteClient remote;private final Clock clock;private final Map<String,Details> cache=new LinkedHashMap<>();
    public FtbPackProvider(RemoteClient remote,Clock clock){this.remote=Objects.requireNonNull(remote);this.clock=Objects.requireNonNull(clock);}
    private JsonNode get(String path,Map<String,String> query,CancellationToken token)throws IOException {
        var root=PackProtocol.get(remote,RemoteService.FTB_API,"https://api.feed-the-beast.com/v1/modpacks/public/modpack"+path,query,token);
        if(!"success".equals(root.path("status").asText()))throw PackProtocol.absent();return root;
    }
    @Override public Page search(PackQuery query,CancellationToken token)throws IOException {
        query.requireBasic();
        long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();
        var root=get(query.text().isEmpty()?"/popular/plays/20":"/search/20",query.text().isEmpty()?Map.of():Map.of("term",query.text()),token);
        var ids=new LinkedHashSet<String>();for(var id:PackProtocol.array(root.path("packs"),20)){
            if(!id.isIntegralNumber()||!id.canConvertToInt()||id.intValue()<1)throw PackProtocol.invalid();ids.add(Integer.toString(id.intValue()));
        }
        var projects=new ArrayList<PackProject>();
        for(String id:ids.stream().skip(query.offset()).limit(query.limit()).toList()){
            token.check();if(System.nanoTime()>=deadline)throw new TaskFailure("SOURCE_TIMEOUT","FTB目录读取超时，请缩小查询后重试");projects.add(details(id,token).project());
        }
        String warning=root.path("curseforge").isArray()&&!root.path("curseforge").isEmpty()?"仅列出来源本次返回的FTB整合包；CurseForge结果需单独接入": "仅列出来源本次返回的FTB整合包";
        return new Page(projects,query.offset(),query.limit(),ids.size(),"NETWORK",clock.instant(),warning);
    }
    private cn.dreamingfish.launcher.content.ProjectPresentation presentation(JsonNode root,List<PackVersion> versions)throws IOException {
        var images=new ArrayList<String>();String icon=null;var art=root.path("art");if(art.isArray()){if(art.size()>256)throw PackProtocol.invalid();for(var item:art){String url=cn.dreamingfish.launcher.content.ProjectPresentation.image(item.path("url").asText());if(url==null)continue;if("square".equals(item.path("type").asText())&&icon==null)icon=url;else images.add(url);}}
        var tags=new ArrayList<cn.dreamingfish.launcher.content.ContentCategory>();if(root.path("tags").isArray()){if(root.path("tags").size()>256)throw PackProtocol.invalid();for(var tag:root.path("tags"))tags.add(new cn.dreamingfish.launcher.content.ContentCategory(tag.path("id").asText(),PackProtocol.required(tag,"name",256),"tags"));}
        return new cn.dreamingfish.launcher.content.ProjectPresentation(icon,Objects.requireNonNullElse(cn.dreamingfish.launcher.content.ProjectPresentation.text(root,"description",262144),""),tags,images,PackProtocol.distinctVersions(versions,false),PackProtocol.distinctVersions(versions,true),root.hasNonNull("released")?PackProtocol.seconds(root,"released").toString():null,root.hasNonNull("updated")?PackProtocol.seconds(root,"updated").toString():null,null,null,Map.of());
    }
    @Override public Details details(String id,CancellationToken token)throws IOException {
        PackProtocol.id(PackSource.FTB,id);token.check();
        synchronized(cache){var previous=cache.get(id);if(previous!=null&&clock.instant().isBefore(previous.fetchedAt().plusSeconds(300)))return new Details(previous.project(),previous.versions(),"CACHE",previous.fetchedAt(),previous.warning());}
        var root=get("/"+id,Map.of(),token);
        if(!root.path("private").isBoolean())throw PackProtocol.invalid();
        if(!root.path("id").isIntegralNumber()||!root.path("id").asText().equals(id)||root.path("private").booleanValue())throw PackProtocol.absent();
        var versions=new ArrayList<PackVersion>();
        for(var item:PackProtocol.array(root.path("versions"),10000)){
            token.check();if(!item.path("private").isBoolean())throw PackProtocol.invalid();if(item.path("private").booleanValue())continue;
            var published=PackProtocol.seconds(item,"released");if(published!=null&&published.isAfter(clock.instant()))continue;
            var games=new TreeSet<String>();var loaders=new TreeSet<String>();
            for(var target:PackProtocol.array(item.path("targets"),100)){
                String type=target.path("type").asText();if(!type.equals("game")&&!type.equals("modloader"))continue;
                String name=PackProtocol.required(target,"name",128),version=PackProtocol.required(target,"version",128);
                if(!name.matches("[a-zA-Z0-9_.+\\-]{1,128}")||!version.matches("[a-zA-Z0-9_.+\\-]{1,128}"))throw PackProtocol.invalid();
                if(type.equals("game")&&name.equals("minecraft"))games.add(version);if(type.equals("modloader"))loaders.add(name.toLowerCase(Locale.ROOT));
            }
            long number=PackProtocol.number(item,"id");if(number<1||number>Integer.MAX_VALUE)throw PackProtocol.invalid();
            versions.add(new PackVersion(Long.toString(number),PackProtocol.required(item,"name",128),List.copyOf(games),List.copyOf(loaders),published));
        }
        versions.sort(Comparator.comparing(PackVersion::publishedAt,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(PackVersion::id));
        var project=new PackProject(PackSource.FTB,id,PackProtocol.required(root,"name",512),PackProtocol.plain(root,"synopsis",2048),PackProtocol.distinctVersions(versions,false),PackProtocol.distinctVersions(versions,true),presentation(root,versions));
        var result=new Details(project,versions,"NETWORK",clock.instant(),null);token.check();
        synchronized(cache){cache.put(id,result);while(cache.size()>64)cache.remove(cache.keySet().iterator().next());}return result;
    }
}
