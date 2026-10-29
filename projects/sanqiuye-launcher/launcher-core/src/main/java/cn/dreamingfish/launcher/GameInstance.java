package cn.dreamingfish.launcher;

import java.nio.file.Path;

/** An isolated instance description. Constructing it never touches game files. */
public record GameInstance(String id, String name, String gameVersion, LoaderKind loader, Path directory) {
    public enum LoaderKind { VANILLA, FORGE, FABRIC, NEOFORGE, QUILT, OPTIFINE }

    public GameInstance {
        if (id == null || id.isBlank() || name == null || name.isBlank()
                || gameVersion == null || gameVersion.isBlank() || loader == null
                || directory == null || !directory.isAbsolute()) {
            throw new IllegalArgumentException("An instance requires complete metadata and an absolute directory");
        }
        directory = directory.normalize();
    }
}
