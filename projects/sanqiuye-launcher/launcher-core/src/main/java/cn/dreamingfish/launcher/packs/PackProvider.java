package cn.dreamingfish.launcher.packs;
import cn.dreamingfish.launcher.downloads.CancellationToken;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

/** 只读目录能力；目录/版本信息不授予安装或执行权限。 */
public interface PackProvider {
    record Page(List<PackProject> items,int offset,int limit,int total,String provenance,Instant fetchedAt,String warning) {
        public Page {items=List.copyOf(items);}
    }
    record Details(PackProject project,List<PackVersion> versions,String provenance,Instant fetchedAt,String warning) {
        public Details {versions=List.copyOf(versions);}
    }
    default java.util.List<cn.dreamingfish.launcher.content.ContentCategory> categories(CancellationToken token)throws IOException{token.check();return java.util.List.of();}
    Page search(PackQuery query,CancellationToken token)throws IOException;
    Details details(String id,CancellationToken token)throws IOException;
}
