package cn.dreamingfish.launcher.packs;
import cn.dreamingfish.launcher.downloads.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.time.*;
import java.util.*;

/** 官方v2整合包目录；不借普通JAR模组入库执行整合包安装。 */
public final class ModrinthPackProvider implements PackProvider {
    private final RemoteClient remote;private final Clock clock;private final cn.dreamingfish.launcher.content.ModrinthCategories categories;
    public ModrinthPackProvider(RemoteClient remote,Clock clock){this.remote=Objects.requireNonNull(remote);this.clock=Objects.requireNonNull(clock);this.categories=new cn.dreamingfish.launcher.content.ModrinthCategories(remote,clock,"modpack");}
    @Override public java.util.List<cn.dreamingfish.launcher.content.ContentCategory> categories(CancellationToken token)throws IOException{return categories.list(token);}
    private JsonNode get(String path,Map<String,String> query,CancellationToken token)throws IOException{return PackProtocol.get(remote,RemoteService.MODRINTH_API,"https://api.modrinth.com/v2"+path,query,token);}
    @Override public Page search(PackQuery query,CancellationToken token)throws IOException {
        categories.require(query.category(),token);var facets=new ArrayList<List<String>>();facets.add(List.of("project_type:modpack"));if(!query.category().isEmpty())facets.add(List.of("categories:"+query.category()));if(!query.gameVersion().isEmpty())facets.add(List.of("versions:"+query.gameVersion()));if(!query.loader().isEmpty())facets.add(List.of("categories:"+query.loader()));
        var root=get("/search",Map.of("query",query.text(),"facets",new String(cn.dreamingfish.launcher.storage.LauncherJson.write(facets),java.nio.charset.StandardCharsets.UTF_8),"index",query.sort().toLowerCase(Locale.ROOT),"offset",Integer.toString(query.offset()),"limit",Integer.toString(query.limit())),token);
        var projects=new ArrayList<PackProject>();
        for(var item:PackProtocol.array(root.path("hits"),query.limit())){
            if(!item.path("project_type").asText().equals("modpack"))throw PackProtocol.invalid();
            projects.add(project(item,"project_id","versions"));
        }
        long total=PackProtocol.number(root,"total_hits");if(total>Integer.MAX_VALUE)throw PackProtocol.invalid();
        return new Page(projects,query.offset(),query.limit(),(int)total,"NETWORK",clock.instant(),null);
    }
    private PackProject project(JsonNode node,String idKey,String versionsKey)throws IOException {
        String id=PackProtocol.required(node,idKey,128);try{PackProtocol.id(PackSource.MODRINTH,id);}catch(IllegalArgumentException bad){throw PackProtocol.invalid();}
        var loaders=PackProtocol.tags(node.path("loaders"));
        if(loaders.isEmpty())loaders=PackProtocol.tags(node.path("categories")).stream().filter(Set.of("fabric","forge","neoforge","quilt","vanilla")::contains).toList();
        return new PackProject(PackSource.MODRINTH,id,PackProtocol.required(node,"title",512),PackProtocol.plain(node,"description",2048),PackProtocol.tags(node.path(versionsKey)),loaders,cn.dreamingfish.launcher.content.ProjectPresentation.modrinth(node));
    }
    @Override public Details details(String id,CancellationToken token)throws IOException {
        PackProtocol.id(PackSource.MODRINTH,id);var root=get("/project/"+id,Map.of(),token);
        if(!root.path("project_type").asText().equals("modpack")||!root.path("id").asText().equals(id)&&!root.path("slug").asText().equals(id))throw PackProtocol.absent();
        if(root.hasNonNull("status")&&!Set.of("approved","archived","unlisted").contains(root.path("status").asText()))throw PackProtocol.absent();
        var project=project(root,"id","game_versions");var versions=new ArrayList<PackVersion>();
        for(var value:PackProtocol.array(get("/project/"+project.id()+"/version",Map.of("include_changelog","false"),token),20000)){
            token.check();if(!value.path("project_id").asText().equals(project.id()))throw PackProtocol.invalid();
            if(!Set.of("listed","archived","unlisted").contains(value.path("status").asText()))continue;
            Instant published;try{published=Instant.parse(PackProtocol.required(value,"date_published",64));}catch(RuntimeException bad){throw PackProtocol.invalid();}
            if(published.isAfter(clock.instant()))continue;
            String versionId=PackProtocol.required(value,"id",128);try{PackProtocol.id(PackSource.MODRINTH,versionId);}catch(IllegalArgumentException bad){throw PackProtocol.invalid();}
            versions.add(new PackVersion(versionId,PackProtocol.required(value,"name",512),PackProtocol.tags(value.path("game_versions")),PackProtocol.tags(value.path("loaders")),published));
        }
        versions.sort(Comparator.comparing(PackVersion::publishedAt).reversed().thenComparing(PackVersion::id));
        return new Details(project,versions,"NETWORK",clock.instant(),null);
    }
}
