package cn.dreamingfish.launcher.packs;
import cn.dreamingfish.launcher.downloads.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.time.*;
import java.util.*;

/** v1公开目录，按safeName寻址；v2 GraphQL不按REST路径猜测。 */
public final class AtLauncherPackProvider implements PackProvider {
    private record Cached(List<PackProject> projects,Instant at){}
    private final RemoteClient remote;private final Clock clock;private volatile Cached cached;
    public AtLauncherPackProvider(RemoteClient remote,Clock clock){this.remote=Objects.requireNonNull(remote);this.clock=Objects.requireNonNull(clock);}
    private JsonNode get(String path,CancellationToken token)throws IOException {
        var root=PackProtocol.get(remote,RemoteService.ATLAUNCHER_API,"https://api.atlauncher.com/v1"+path,Map.of(),token);
        if(!root.path("error").isBoolean()||!root.path("code").isInt())throw PackProtocol.invalid();
        if(root.path("error").booleanValue()||root.path("code").intValue()!=200)throw PackProtocol.absent();return root.path("data");
    }
    @Override public Page search(PackQuery query,CancellationToken token)throws IOException {
        query.requireBasic();
        token.check();var value=cached;String provenance="CACHE";
        if(value==null||!clock.instant().isBefore(value.at.plusSeconds(300))){
            var projects=new ArrayList<PackProject>();
            for(var item:PackProtocol.array(get("/packs/full/public",token),2000)){
                token.check();if(!isPublic(item))continue;projects.add(project(item,versions(item,token)));
            }
            value=new Cached(List.copyOf(projects),clock.instant());cached=value;provenance="NETWORK";
        }
        token.check();String term=query.text().toLowerCase(Locale.ROOT);
        var filtered=value.projects.stream().filter(p->p.name().toLowerCase(Locale.ROOT).contains(term)||p.summary().toLowerCase(Locale.ROOT).contains(term)||p.id().toLowerCase(Locale.ROOT).contains(term)).toList();
        return new Page(filtered.stream().skip(query.offset()).limit(query.limit()).toList(),query.offset(),query.limit(),filtered.size(),provenance,value.at,null);
    }
    private List<PackVersion> versions(JsonNode pack,CancellationToken token)throws IOException {
        var versions=new ArrayList<PackVersion>();
        for(var item:PackProtocol.array(pack.path("versions"),10000)){
            token.check();var published=PackProtocol.seconds(item,"published");if(published!=null&&published.isAfter(clock.instant()))continue;
            String name=PackProtocol.required(item,"version",128),game=PackProtocol.required(item,"minecraft",128);
            if(!game.matches("[a-zA-Z0-9_.+\\-]{1,128}"))throw PackProtocol.invalid();
            versions.add(new PackVersion(name,name,List.of(game),List.of(),published));
        }
        versions.sort(Comparator.comparing(PackVersion::publishedAt,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(PackVersion::id));return List.copyOf(versions);
    }
    private PackProject project(JsonNode node,List<PackVersion> versions)throws IOException {
        String id=PackProtocol.required(node,"safeName",128);try{PackProtocol.id(PackSource.ATLAUNCHER,id);}catch(IllegalArgumentException bad){throw PackProtocol.invalid();}
        return new PackProject(PackSource.ATLAUNCHER,id,PackProtocol.required(node,"name",512),PackProtocol.plain(node,"description",2048),PackProtocol.distinctVersions(versions,false),List.of(),new cn.dreamingfish.launcher.content.ProjectPresentation(null,java.util.Objects.requireNonNullElse(cn.dreamingfish.launcher.content.ProjectPresentation.text(node,"description",262144),""),List.of(),List.of(),PackProtocol.distinctVersions(versions,false),List.of(),null,null,null,null,Map.of()));
    }
    private boolean isPublic(JsonNode node)throws IOException {
        return switch(PackProtocol.required(node,"type",32)){
            case "public"->true;
            case "private","semipublic"->false;
            default->throw PackProtocol.invalid();
        };
    }
    @Override public Details details(String id,CancellationToken token)throws IOException {
        PackProtocol.id(PackSource.ATLAUNCHER,id);var root=get("/pack/"+id,token);
        if(!isPublic(root)||!id.equals(root.path("safeName").asText()))throw PackProtocol.absent();
        var versions=versions(root,token);return new Details(project(root,versions),versions,"NETWORK",clock.instant(),null);
    }
}
