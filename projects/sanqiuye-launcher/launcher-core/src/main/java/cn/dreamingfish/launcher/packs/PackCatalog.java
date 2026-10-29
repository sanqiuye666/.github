package cn.dreamingfish.launcher.packs;
import cn.dreamingfish.launcher.downloads.*;
import java.io.IOException;
import java.util.*;

/** 目录能力按来源组合；不使用平台序号或把镜像当成内容作者。 */
public final class PackCatalog {
    private final Map<PackSource,PackProvider> providers;
    public PackCatalog(Map<PackSource,PackProvider> providers){this.providers=Map.copyOf(providers);}
    public Set<PackSource> sources(){return providers.keySet();}
    private PackProvider provider(PackSource source)throws IOException {var value=providers.get(Objects.requireNonNull(source));if(value==null)throw new TaskFailure("SOURCE_NOT_IMPLEMENTED","此平台的整合包目录尚未接入");return value;}
    public java.util.List<cn.dreamingfish.launcher.content.ContentCategory> categories(PackSource source,CancellationToken token)throws IOException{return provider(source).categories(token);}
    public PackProvider.Page search(PackSource source,PackQuery query,CancellationToken token)throws IOException {token.check();return provider(source).search(query,token);}
    public PackProvider.Details details(PackSource source,String id,CancellationToken token)throws IOException {token.check();return provider(source).details(id,token);}
}
