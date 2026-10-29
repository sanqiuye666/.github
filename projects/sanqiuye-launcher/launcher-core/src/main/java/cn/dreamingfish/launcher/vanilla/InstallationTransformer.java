package cn.dreamingfish.launcher.vanilla;
import cn.dreamingfish.launcher.downloads.CancellationToken;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
@FunctionalInterface
public interface InstallationTransformer {
 /** 只返回暂存安装内生成的相对文件路径；原下载文件随后再次复验。 */
 List<Path> apply(Path staging,CancellationToken token)throws IOException;
}
