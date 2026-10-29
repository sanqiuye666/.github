package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.catalog.*;
import java.io.IOException;
import java.util.*;

public final class CatalogRoutes implements LauncherRoutes {
    private final CatalogSession catalog;
    private final String provenance;
    public CatalogRoutes(CatalogSession catalog) { this(catalog,null); }
    public CatalogRoutes(CatalogSession catalog,String provenance) { this.catalog=catalog;this.provenance=provenance; }
    @Override public Optional<ApiResponse> handle(ApiRequest request) throws IOException {
        if (!Set.of("/catalog","/catalog/refresh").contains(request.path())) return Optional.empty();
        boolean refresh=request.path().endsWith("/refresh");
        request.requireMethod(refresh?"POST":"GET");
        if (refresh) { request.onlyQuery();request.onlyFields(Set.of()); }
        else request.onlyQuery("q","type","offset","limit","snapshotId");
        int offset=number(request.query(),"offset",0,0,Integer.MAX_VALUE);
        int limit=number(request.query(),"limit",50,1,100);
        if (offset>0 && !request.query().containsKey("snapshotId")) throw ApiRequest.invalid("翻页需要同一目录快照");
        String query=request.query().getOrDefault("q","");
        if (query.length()>256 || query.chars().anyMatch(Character::isISOControl)) throw ApiRequest.invalid("查询过长或含控制字符");
        OfficialVersion.Type type=null;
        if (request.query().containsKey("type") && !request.query().get("type").isEmpty()) {
            try { type=OfficialVersion.Type.fromWire(request.query().get("type")); }
            catch (IllegalArgumentException invalid) { throw ApiRequest.invalid("不支持的版本类型"); }
        }
        var snapshot=refresh?catalog.snapshot(true):request.query().containsKey("snapshotId")?catalog.require(request.query().get("snapshotId")):catalog.snapshot(false);
        var manifest=snapshot.value().manifest();
        var filtered=manifest.search(query,type);
        var counts=new LinkedHashMap<String,Long>();
        for(var kind:OfficialVersion.Type.values()) counts.put(kind.wireName(),manifest.versions().stream().filter(v->v.type()==kind).count());
        var items=filtered.stream().skip(offset).limit(limit).map(v->Map.of("id",v.id(),"type",v.type().wireName(),"releaseTime",v.releaseTime().toString())).toList();
        var body=new LinkedHashMap<String,Object>();
        body.put("snapshotId",snapshot.id());body.put("total",manifest.versions().size());body.put("filteredTotal",filtered.size());
        body.put("typeCounts",counts);body.put("offset",offset);body.put("limit",limit);body.put("items",items);
        body.put("source",snapshot.value().source().name());body.put("fetchedAt",snapshot.value().fetchedAt().toString());body.put("warning",snapshot.value().warning());
        if(provenance!=null){body.put("source",provenance);body.put("warning","MOCK · 本地演练目录，仅四个示例，不是完整官方清单");}
        return Optional.of(new ApiResponse(200,body));
    }
    static int number(Map<String,String> query,String key,int fallback,int min,int max) {
        try {
            int value=query.containsKey(key)?Integer.parseInt(query.get(key)):fallback;
            if (value<min || value>max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) { throw ApiRequest.invalid("分页参数无效"); }
    }
}
