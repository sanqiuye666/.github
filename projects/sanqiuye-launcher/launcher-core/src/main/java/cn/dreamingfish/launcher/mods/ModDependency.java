package cn.dreamingfish.launcher.mods;
import java.util.Objects;
public record ModDependency(Kind kind,String projectId,String versionId,String fileName){public enum Kind {REQUIRED,OPTIONAL,INCOMPATIBLE,EMBEDDED,TOOL,INCLUDE,UNKNOWN}public ModDependency{Objects.requireNonNull(kind);if(projectId!=null)ModProtocol.id(projectId);if(versionId!=null)ModProtocol.id(versionId);}}
