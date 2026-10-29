package cn.dreamingfish.launcher.packs;
import java.util.*;
public record PackProject(PackSource source,String id,String name,String summary,List<String> gameVersions,List<String> loaders,cn.dreamingfish.launcher.content.ProjectPresentation presentation) {
    public PackProject(PackSource source,String id,String name,String summary,List<String> gameVersions,List<String> loaders){this(source,id,name,summary,gameVersions,loaders,cn.dreamingfish.launcher.content.ProjectPresentation.empty());}
    public PackProject {Objects.requireNonNull(presentation);Objects.requireNonNull(source);Objects.requireNonNull(id);Objects.requireNonNull(name);gameVersions=List.copyOf(gameVersions);loaders=List.copyOf(loaders);}
}
