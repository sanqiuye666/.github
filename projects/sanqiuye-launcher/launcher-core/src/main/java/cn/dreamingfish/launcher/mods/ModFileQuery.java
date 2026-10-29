package cn.dreamingfish.launcher.mods;
public record ModFileQuery(String gameVersion,String loader,int offset,int limit){public ModFileQuery{var query=new ModQuery("",gameVersion,loader,ModQuery.Sort.RELEVANCE,offset,limit);gameVersion=query.gameVersion();loader=query.loader();}}
