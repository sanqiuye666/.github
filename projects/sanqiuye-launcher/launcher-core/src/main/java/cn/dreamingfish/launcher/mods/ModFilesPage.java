package cn.dreamingfish.launcher.mods;
import java.util.List;
public record ModFilesPage(List<ModFile> items,int offset,int limit,long total,long accessibleTotal,boolean platformLimited){public ModFilesPage{items=List.copyOf(items);}}
