package cn.dreamingfish.launcher.packs;
public record PackQuery(String text,int offset,int limit,String category,String gameVersion,String loader,String sort) {
    public PackQuery(String text,int offset,int limit){this(text,offset,limit,"","","","RELEVANCE");}
    public void requireBasic(){if(!category.isEmpty()||!gameVersion.isEmpty()||!loader.isEmpty()||!sort.equals("RELEVANCE"))throw new IllegalArgumentException("此来源暂不支持分类、版本、加载器和排序筛选");}
    public PackQuery {
        category=category==null?"":category;gameVersion=gameVersion==null?"":gameVersion;loader=loader==null?"":loader;sort=sort==null?"RELEVANCE":sort;
        if(!category.isEmpty()&&!category.matches("[A-Za-z0-9_-]{1,64}")||!gameVersion.isEmpty()&&!gameVersion.matches("[A-Za-z0-9_.+\\-]{1,128}")||!loader.isEmpty()&&!loader.matches("[a-z0-9_-]{1,32}")||!java.util.Set.of("RELEVANCE","DOWNLOADS","UPDATED").contains(sort))throw new IllegalArgumentException("整合包筛选无效");
        if(text==null||text.length()>128||text.chars().anyMatch(Character::isISOControl)||offset<0||offset>10000||limit<1||limit>20)throw new IllegalArgumentException("搜索词、页码或数量超出允许范围");
        text=text.strip();
    }
}
