package cn.dreamingfish.launcher.options;

import cn.dreamingfish.launcher.launch.LaunchFailure;
import cn.dreamingfish.launcher.storage.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.security.*;
import java.util.*;

/** 全局游戏选项模板；与Java/JVM的每实例启动参数分开存放。 */
public final class GameOptionsStore {
    public record Snapshot(UUID revision, boolean enabled, Map<String,Object> values) {
        public Snapshot { Objects.requireNonNull(revision); values=GameOption.validate(values); }
    }
    private static final Path FILE=Path.of("game-options.json"), LOCK=Path.of(".game-options.lock");
    private static final int LIMIT=32768;
    private final LauncherStorage storage;
    public GameOptionsStore(Path root) { storage=new LauncherStorage(root); }
    public LauncherStorage.Lease lock() throws IOException { return storage.lock(LOCK); }
    public Snapshot read() throws IOException {
        if(!storage.exists(FILE))return new Snapshot(new UUID(0,0),false,Map.of());
        try {
            var envelope=LauncherJson.read(storage.read(FILE,LIMIT),LIMIT); keys(envelope,Set.of("schemaVersion","payload","sha256"));
            if(!envelope.path("schemaVersion").isInt()||envelope.path("schemaVersion").intValue()!=1)throw new IllegalArgumentException();
            var payload=envelope.path("payload");keys(payload,Set.of("revision","enabled","values"));
            if(!digest(LauncherJson.write(payload)).equals(LauncherJson.text(envelope,"sha256",64))||!payload.path("enabled").isBoolean())throw new IllegalArgumentException();
            return new Snapshot(UUID.fromString(LauncherJson.text(payload,"revision",36)),payload.path("enabled").booleanValue(),parseValues(payload.path("values")));
        } catch(IOException|RuntimeException invalid) { throw new LaunchFailure("OPTIONS_INVALID","默认游戏设置不可读取或校验失败，原记录已保留，请检查文件"); }
    }
    public Snapshot save(UUID expected, boolean enabled, Map<String,Object> values) throws IOException {
        Objects.requireNonNull(expected); var checked=GameOption.validate(values);
        try(var ignored=lock()) {
            if(!read().revision().equals(expected))throw new LaunchFailure("OPTIONS_CHANGED","默认游戏设置已变化，请重新读取后再保存");
            var result=new Snapshot(UUID.randomUUID(),enabled,checked);
            var payload=Map.of("revision",result.revision().toString(),"enabled",enabled,"values",checked);
            storage.writeAtomically(FILE,LauncherJson.write(Map.of("schemaVersion",1,"payload",payload,"sha256",digest(LauncherJson.write(payload)))));
            return result;
        } catch(LauncherBusyException busy) { throw new LaunchFailure("OPTIONS_BUSY","默认设置正在用于启动或保存，请稍后再试"); }
    }
    public static Map<String,Object> parseValues(JsonNode node) {
        if(!node.isObject()||node.size()>GameOption.values().length)throw new IllegalArgumentException("游戏设置列表无效");
        var result=new LinkedHashMap<String,Object>(); node.fields().forEachRemaining(e->{
            var v=e.getValue(); Object value;
            if(v.isBoolean())value=v.booleanValue();else if(v.isInt())value=v.intValue();else if(v.isTextual())value=v.textValue();else throw new IllegalArgumentException("游戏设置类型无效");
            result.put(e.getKey(),value);
        }); return GameOption.validate(result);
    }
    private static void keys(JsonNode node,Set<String> expected) { var keys=new HashSet<String>();node.fieldNames().forEachRemaining(keys::add);if(!node.isObject()||!keys.equals(expected))throw new IllegalArgumentException(); }
    private static String digest(byte[] bytes) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);} }
}
