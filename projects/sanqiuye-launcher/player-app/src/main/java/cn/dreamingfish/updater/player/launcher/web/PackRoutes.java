package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.packs.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 只读公开整合包目录；整次查询预算覆盖平台的多个HTTP请求。 */
public final class PackRoutes implements LauncherRoutes,AutoCloseable {
    private final PackCatalog catalog;
    private final Duration budget;
    private String provenance;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("sqy-pack-timeout").factory());
    private final Set<CancellationToken> active=ConcurrentHashMap.newKeySet();
    private final Semaphore permits=new Semaphore(3);
    private final Map<String,CancellationToken> requests=new HashMap<>();
    private final Map<String,Long> stopped=new LinkedHashMap<>();
    public PackRoutes(PackCatalog catalog){this(catalog,Duration.ofSeconds(30));}
    public PackRoutes(PackCatalog catalog,String provenance){this(catalog,Duration.ofSeconds(30));this.provenance=provenance;}
    PackRoutes(PackCatalog catalog,Duration budget){this.catalog=Objects.requireNonNull(catalog);this.budget=budget;}

    @Override public Optional<ApiResponse> handle(ApiRequest request)throws IOException {
        if(!request.path().startsWith("/packs/"))return Optional.empty();
        if(request.path().equals("/packs/cancel")){
            request.requireMethod("POST");request.onlyQuery();request.onlyFields(Set.of("requestId"));
            String id=requestId(request.text("requestId",36));CancellationToken previous;
            synchronized(requests){rememberStopped(id);previous=requests.get(id);}
            if(previous!=null)previous.cancel();
            return Optional.of(new ApiResponse(200,Map.of("cancelRequested",true)));
        }
        request.requireMethod("GET");
        if(request.path().equals("/packs/sources")){
            request.onlyQuery();
            return Optional.of(new ApiResponse(200,Map.of("sources",Arrays.stream(PackSource.values()).map(source->Map.of("source",source.name(),"name",name(source),"state",catalog.sources().contains(source)?"AVAILABLE":"PENDING","reason",catalog.sources().contains(source)?"已接入官方目录，网络以本次查询结果为准":"整合包适配尚未接入")).toList())));
        }
        PackSource source;PackQuery query=null;String id=null;boolean categories=request.path().equals("/packs/categories");
        try{
            if(categories){request.onlyQuery("source","requestId");source=PackSource.valueOf(request.query().getOrDefault("source","MODRINTH"));}else if(request.path().equals("/packs/search")){
                request.onlyQuery("source","q","offset","limit","requestId","category","gameVersion","loader","sort");
                source=PackSource.valueOf(request.query().getOrDefault("source","MODRINTH"));
                query=new PackQuery(request.query().getOrDefault("q",""),CatalogRoutes.number(request.query(),"offset",0,0,10000),CatalogRoutes.number(request.query(),"limit",20,1,20),request.query().getOrDefault("category",""),request.query().getOrDefault("gameVersion",""),request.query().getOrDefault("loader",""),request.query().getOrDefault("sort","RELEVANCE"));
            }else{
                request.onlyQuery("requestId");String[] parts=request.path().split("/",-1);
                if(parts.length!=4&&!(parts.length==5&&parts[4].equals("versions")))throw new LauncherApiException(404,"NOT_FOUND","整合包目录操作不存在");
                source=PackSource.valueOf(parts[2]);id=parts[3];
                if(!id.matches(source==PackSource.FTB?"[1-9][0-9]{0,9}":source==PackSource.ATLAUNCHER?"[a-zA-Z0-9]{1,128}":"[a-zA-Z0-9_-]{1,128}"))throw ApiRequest.invalid("整合包标识无效");
            }
        }catch(IllegalArgumentException invalid){throw ApiRequest.invalid("整合包来源、分页或搜索条件无效");}
        String requestId=requestId(request.query().getOrDefault("requestId",UUID.randomUUID().toString()));
        if(!permits.tryAcquire())throw new LauncherApiException(503,"PACK_BUSY","整合包查询繁忙，请稍后重试");
        var token=new CancellationToken();var expired=new AtomicBoolean();active.add(token);
        ScheduledFuture<?> alarm=null;
        try{
            synchronized(requests){
                pruneStopped();
                if(requests.containsKey(requestId))throw new LauncherApiException(409,"QUERY_EXISTS","此整合包查询正在执行");
                requests.put(requestId,token);if(stopped.containsKey(requestId))token.cancel();
            }
            token.check();
            alarm=timer.schedule(()->{expired.set(true);token.cancel();},budget.toMillis(),TimeUnit.MILLISECONDS);
            Map<String,Object> result;
            if(categories){result=Map.of("categories",catalog.categories(source,token));}else if(query!=null){var page=catalog.search(source,query,token);var body=meta(page.provenance(),page.fetchedAt().toString(),page.warning());body.put("items",page.items().stream().map(PackRoutes::project).toList());body.put("offset",page.offset());body.put("limit",page.limit());body.put("total",page.total());result=body;}
            else{var detail=catalog.details(source,id,token);var body=meta(detail.provenance(),detail.fetchedAt().toString(),detail.warning());body.put("project",project(detail.project()));body.put("versions",detail.versions().stream().map(PackRoutes::version).toList());result=body;}
            if(provenance!=null){result=new LinkedHashMap<>(result);result.put("provenance",provenance);result.put("warning","MOCK · 本地目录示例，没有访问平台或提供真实内容文件");}
            token.check();return Optional.of(new ApiResponse(200,result));
        }catch(RemoteHttpException failure){
            if(failure.status()==429)return Optional.of(new ApiResponse(429,Map.of("error",Map.of("code","RATE_LIMITED","message","平台暂时限流，请等待后重试","retryAfterSeconds",failure.retryAfterSeconds()==null?60:failure.retryAfterSeconds()))));
            throw new LauncherApiException(502,"SOURCE_UNAVAILABLE","平台公开目录暂时不可用，请稍后重试");
        }catch(TaskFailure failure){throw new LauncherApiException(failure.code().equals("UNKNOWN_CATEGORY")?400:failure.code().equals("PACK_NOT_FOUND")?404:failure.code().equals("SOURCE_NOT_IMPLEMENTED")?503:502,failure.code(),failure.getMessage());}
        catch(IOException failure){if(expired.get())throw new LauncherApiException(504,"SOURCE_TIMEOUT","整合包查询超时，请缩小范围或稍后重试");if(token.isCancelled())throw new LauncherApiException(409,"QUERY_CANCELLED","整合包查询已取消");throw failure;}
        catch(IllegalArgumentException invalid){throw ApiRequest.invalid("整合包标识或平台响应无效");}
        catch(RejectedExecutionException closed){throw new LauncherApiException(503,"SERVICE_CLOSED","整合包目录已关闭，请重新打开启动器");}
        finally{if(alarm!=null)alarm.cancel(false);synchronized(requests){if(requests.remove(requestId,token))rememberStopped(requestId);}active.remove(token);token.cancel();permits.release();}
    }
    private static String requestId(String id){if(id==null||!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw ApiRequest.invalid("查询标识无效");return id;}
    private void pruneStopped(){long now=System.nanoTime();stopped.entrySet().removeIf(entry->now-entry.getValue()>TimeUnit.MINUTES.toNanos(1));}
    private void rememberStopped(String id){pruneStopped();stopped.put(id,System.nanoTime());while(stopped.size()>256)stopped.remove(stopped.keySet().iterator().next());}
    private static String name(PackSource source){return switch(source){case MODRINTH->"Modrinth";case FTB->"FTB";case ATLAUNCHER->"ATLauncher";case CURSEFORGE->"CurseForge";case TECHNIC->"Technic";case BBSMC->"BBSMC";};}
    private static LinkedHashMap<String,Object> meta(String provenance,String time,String warning){var body=new LinkedHashMap<String,Object>();body.put("provenance",provenance);body.put("fetchedAt",time);body.put("warning",warning);return body;}
    private static Map<String,Object> project(PackProject project){return Map.of("source",project.source().name(),"id",project.id(),"name",project.name(),"summary",project.summary(),"gameVersions",project.gameVersions(),"loaders",project.loaders(),"presentation",project.presentation());}
    private static Map<String,Object> version(PackVersion version){var body=new LinkedHashMap<String,Object>();body.put("id",version.id());body.put("name",version.name());body.put("gameVersions",version.gameVersions());body.put("loaders",version.loaders());body.put("publishedAt",version.publishedAt()==null?null:version.publishedAt().toString());return body;}
    @Override public void close(){for(var token:active)token.cancel();timer.shutdownNow();}
}
