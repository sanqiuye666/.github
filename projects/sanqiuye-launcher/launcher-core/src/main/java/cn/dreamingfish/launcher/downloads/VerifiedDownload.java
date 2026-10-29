package cn.dreamingfish.launcher.downloads;
import java.nio.file.Path;
public record VerifiedDownload(Path relativeObject,long size,String sha256,FileDigest sourceDigest) {}
