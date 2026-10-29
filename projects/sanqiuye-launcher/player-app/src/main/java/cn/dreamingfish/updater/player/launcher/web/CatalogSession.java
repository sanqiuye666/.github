package cn.dreamingfish.updater.player.launcher.web;

import cn.dreamingfish.launcher.catalog.*;
import cn.dreamingfish.launcher.storage.LauncherJson;
import java.io.IOException;
import java.security.*;
import java.util.*;

/** Serializes refreshes and gives all page consumers the same content identity. */
public final class CatalogSession {
    public record Snapshot(String id,VersionCatalogSnapshot value) {}
    private final MojangVersionCatalog catalog;
    public CatalogSession(MojangVersionCatalog catalog) { this.catalog=Objects.requireNonNull(catalog); }
    public synchronized Snapshot snapshot(boolean refresh) throws IOException {
        var value=catalog.load(refresh);
        var entries=value.manifest().versions().stream().map(v->List.of(v.id(),v.type().wireName(),v.sha1(),v.releaseTime().toString())).toList();
        try {
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(LauncherJson.write(entries)));
            return new Snapshot(digest,value);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public Snapshot require(String id) throws IOException {
        if (id==null || !id.matches("[0-9a-f]{64}")) throw ApiRequest.invalid("版本目录快照标识无效");
        Snapshot current=snapshot(false);
        if (!current.id().equals(id)) throw new LauncherApiException(409,"CATALOG_CHANGED","官方目录已更新，请重新选择版本");
        return current;
    }
}
