package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.instances.*;
import cn.dreamingfish.launcher.storage.LauncherBusyException;
import cn.dreamingfish.launcher.vanilla.VanillaInstaller;
import java.io.IOException;
import java.util.*;

public final class InstanceRoutes implements LauncherRoutes {
    private final FileInstanceCatalog instances;
    private final CatalogSession catalog;
    private final VanillaInstaller installer;
    public InstanceRoutes(FileInstanceCatalog instances,CatalogSession catalog) { this(instances,catalog,null); }
    public InstanceRoutes(FileInstanceCatalog instances,CatalogSession catalog,VanillaInstaller installer) { this.instances=instances;this.catalog=catalog;this.installer=installer; }
    @Override public Optional<ApiResponse> handle(ApiRequest request) throws IOException {
        if (!request.path().equals("/instances")) return Optional.empty();
        request.onlyQuery();
        if (request.method().equals("GET")) {
            var listing=instances.listing();
            var views=new ArrayList<Map<String,Object>>();for(var instance:listing.instances()){var dto=view(instance);if(installer!=null){var installations=installer.listing(instance.instance().id());dto.put("installation",installations.installations().isEmpty()?null:InstallationRoutes.view(installations.installations().getFirst()));dto.put("installationProblems",installations.problems());if(!installations.installations().isEmpty())dto.put("loader",installations.installations().getFirst().loaderKind().name());}views.add(dto);}
            return Optional.of(new ApiResponse(200,Map.of("instances",views,
                    "problems",listing.problems().stream().map(p->Map.of("directoryId",p.directoryId(),"reason",p.reason())).toList())));
        }
        request.requireMethod("POST");request.onlyFields(Set.of("name","versionId","snapshotId"));
        String name=request.text("name",128),version=request.text("versionId",128);
        var snapshot=catalog.require(request.text("snapshotId",64));
        if (snapshot.value().manifest().versions().stream().noneMatch(v->v.id().equals(version))) throw new LauncherApiException(404,"UNKNOWN_VERSION","请选择官方目录中的版本");
        try { return Optional.of(new ApiResponse(201,Map.of("instance",view(instances.create(name,version,snapshot.value().manifest()))))); }
        catch (LauncherBusyException busy) { throw new LauncherApiException(409,"BUSY",busy.getMessage()); }
    }
    static Map<String,Object> view(PreparedInstance prepared) {
        var game=prepared.instance();var result=new LinkedHashMap<String,Object>();
        result.put("id",game.id());result.put("name",game.name());result.put("gameVersion",game.gameVersion());
        result.put("loader",game.loader().name());result.put("createdAt",prepared.createdAt().toString());result.put("state",prepared.state().name());
        result.put("installation",null);
        return result;
    }
}
