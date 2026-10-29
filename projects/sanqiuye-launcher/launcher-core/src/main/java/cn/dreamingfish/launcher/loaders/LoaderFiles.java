package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.vanilla.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** 有限Maven描述统一变成原下载器的固定文件，不新增HTTP实现。 */
final class LoaderFiles {
 private final DownloadStore store;private final RemoteClient remote;
 LoaderFiles(DownloadStore store,RemoteClient remote){this.store=store;this.remote=remote;}
 InstallFile resolve(MavenLibrary library,CancellationToken token)throws IOException{
  var digest=library.digest();if(digest==null){byte[] checksum=remote.bytes(new RemoteRequest(library.service(),URI.create(library.uri()+".sha1"),Map.of()),256,token);try{digest=new FileDigest(FileDigest.Algorithm.SHA1,new String(checksum,StandardCharsets.US_ASCII).strip());}catch(IllegalArgumentException invalid){throw new TaskFailure("SOURCE_INVALID_RESPONSE","核心官方Maven摘要无效");}}
  long size=library.size()==null?-1:library.size();long limit=library.service()==RemoteService.FABRIC_LIBRARIES?64L*1024*1024:128L*1024*1024;var spec=new DownloadSpec(library.service(),library.uri(),size,limit,digest,"核心依赖");if(size<0){var object=store.fetch(spec,token,n->{});spec=new DownloadSpec(spec.service(),spec.uri(),object.size(),spec.maxBytes(),spec.digest(),spec.displayName());}return new InstallFile(Path.of("libraries/"+library.path()),spec);
 }
 static void add(Map<Path,InstallFile> files,InstallFile value)throws TaskFailure{var old=files.putIfAbsent(value.relativePath(),value);if(old!=null&&(!old.download().digest().equals(value.download().digest())||old.download().expectedSize()!=value.download().expectedSize()))throw new TaskFailure("LIBRARY_CONFLICT","核心与现有依赖路径冲突，未改动实例");}
}
