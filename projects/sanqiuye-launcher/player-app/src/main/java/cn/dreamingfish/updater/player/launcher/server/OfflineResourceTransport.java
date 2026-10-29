package cn.dreamingfish.updater.player.launcher.server;
import cn.dreamingfish.launcher.catalog.ManifestTransport;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.updater.protocol.JsonCodec;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** 确定性演练目录；不持有HTTP客户端，任何未知请求均失败，不尝试外网回退。 */
public final class OfflineResourceTransport implements ManifestTransport,RemoteClient {
    private final JsonCodec json=new JsonCodec();
    @Override public byte[] fetch(){
        var versions=new ArrayList<Object>();String at="2024-01-01T00:00:00Z";
        for(String type:List.of("release","snapshot","old_beta","old_alpha"))versions.add(Map.of("id",type.equals("release")?"1.20.6":"mock-"+type,"type",type,"url","https://piston-meta.mojang.com/v1/packages/"+"0".repeat(40)+"/mock.json","sha1","0".repeat(40),"releaseTime",at,"time",at));
        return json.write(Map.of("latest",Map.of("release","1.20.6","snapshot","mock-snapshot"),"versions",versions));
    }
    @Override public void get(RemoteRequest request,long limit,CancellationToken token,ChunkConsumer sink)throws IOException{
        token.check();String path=request.uri().getPath();Object value;
        switch(request.service()){
            case MODRINTH_API->{
                if(path.equals("/v2/tag/loader"))value=List.of(Map.of("name","fabric","supported_project_types",List.of("mod")));
                else if(path.equals("/v2/search")){
                    boolean pack=Objects.toString(request.uri().getQuery(),"").contains("project_type:modpack");
                    value=Map.of("hits",List.of(Map.of("project_id",pack?"MockPack":"MockMods","project_type",pack?"modpack":"mod","title",pack?"离线演练整合包":"离线演练模组","author","三秋叶","description","MOCK · 本地示例，不代表平台真实项目","downloads",0,"versions",List.of("1.20.6"),"categories",List.of("fabric"))),"total_hits",1);
                }else if(path.endsWith("/version"))value=List.of(Map.of("id","MockVer1","project_id",path.contains("MockPack")?"MockPack":"MockMods","name","离线示例版本","status","listed","date_published","2024-01-01T00:00:00Z","game_versions",List.of("1.20.6"),"loaders",List.of("fabric"),"files",List.of()));
                else if(path.equals("/v2/project/MockPack")||path.equals("/v2/project/MockMods"))value=Map.of("id",path.endsWith("MockPack")?"MockPack":"MockMods","project_type",path.endsWith("MockPack")?"modpack":"mod","title","离线演练项目","author","三秋叶","description","MOCK · 无真实文件","downloads",0,"game_versions",List.of("1.20.6"),"loaders",List.of("fabric"));
                else throw unavailable();
            }
            case FTB_API->{if(path.endsWith("/popular/plays/20")||path.endsWith("/search/20"))value=Map.of("status","success","packs",List.of(1),"curseforge",List.of());else if(path.endsWith("/1"))value=Map.of("status","success","id",1,"name","FTB离线演练包","synopsis","MOCK · 不代表真实FTB内容","private",false,"versions",List.of(Map.of("id",1,"name","示例版本","private",false,"released",1704067200,"targets",List.of(Map.of("type","game","name","minecraft","version","1.20.6")))));else throw unavailable();}
            case ATLAUNCHER_API->{var pack=Map.of("safeName","MockPack","name","ATLauncher离线演练包","type","public","description","MOCK · 无真实文件","versions",List.of(Map.of("version","1.0","minecraft","1.20.6","published",1704067200)));if(path.endsWith("/packs/full/public"))value=Map.of("error",false,"code",200,"data",List.of(pack));else if(path.endsWith("/pack/MockPack"))value=Map.of("error",false,"code",200,"data",pack);else throw unavailable();}
            default->throw unavailable();
        }
        byte[] bytes=json.write(value);if(bytes.length>limit)throw new IOException("离线示例超过请求上限");token.check();sink.accept(ByteBuffer.wrap(bytes));
    }
    private static IOException unavailable(){return new IOException("MOCK离线演练不提供此文件或真实网络下载");}
}
