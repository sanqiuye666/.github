package cn.dreamingfish.launcher.resources;
import java.util.*;
public enum ResourceKind {
 RESOURCEPACKS("resourcepacks",Set.of("zip")),SHADERS("shaderpacks",Set.of("zip")),RECORDINGS("replay_recordings",Set.of("mcpr")),SCHEMATICS("schematics",Set.of("litematic","schem","schematic","nbt"));
 private final String directory;private final Set<String> extensions;
 ResourceKind(String directory,Set<String> extensions){this.directory=directory;this.extensions=extensions;}
 public String directory(){return directory;}
 public boolean accepts(String name){int dot=name.lastIndexOf('.');return dot>0&&extensions.contains(name.substring(dot+1).toLowerCase(Locale.ROOT));}
}
