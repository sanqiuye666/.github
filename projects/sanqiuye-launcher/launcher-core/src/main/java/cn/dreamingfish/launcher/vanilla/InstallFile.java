package cn.dreamingfish.launcher.vanilla;
import cn.dreamingfish.launcher.downloads.DownloadSpec;
import java.nio.file.Path;
import java.util.Objects;
public record InstallFile(Path relativePath,DownloadSpec download) {
 public InstallFile {Objects.requireNonNull(relativePath);Objects.requireNonNull(download);SafeInstallPath.parse(relativePath.toString().replace('\\','/'));}
}
