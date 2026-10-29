package cn.dreamingfish.launcher.mods;
import java.util.Objects;
public record ModFileRef(ModSource source,String projectId,String versionId,String fileKey){public ModFileRef{Objects.requireNonNull(source);ModProtocol.id(projectId);ModProtocol.id(versionId);if(fileKey==null||!fileKey.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("模组文件引用无效");}}
