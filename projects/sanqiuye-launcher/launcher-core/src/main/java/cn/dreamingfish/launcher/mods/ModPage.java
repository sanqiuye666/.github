package cn.dreamingfish.launcher.mods;
import java.util.List;
public record ModPage(List<ModProject> items,int offset,int limit,long total,long accessibleTotal,boolean platformLimited){public ModPage{items=List.copyOf(items);}}
