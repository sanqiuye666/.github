package cn.dreamingfish.launcher.catalog;

import java.io.IOException;

@FunctionalInterface
public interface ManifestTransport {
    byte[] fetch() throws IOException, InterruptedException;
}
