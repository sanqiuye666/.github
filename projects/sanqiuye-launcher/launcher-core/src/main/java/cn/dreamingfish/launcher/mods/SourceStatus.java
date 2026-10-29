package cn.dreamingfish.launcher.mods;
import java.util.*;
public record SourceStatus(ModSource source,String state,String reason,List<String> loaders,String warning){public SourceStatus{Objects.requireNonNull(source);if(!Set.of("AVAILABLE","UNCONFIGURED","UNAVAILABLE").contains(state))throw new IllegalArgumentException("来源状态无效");loaders=List.copyOf(loaders);}}
