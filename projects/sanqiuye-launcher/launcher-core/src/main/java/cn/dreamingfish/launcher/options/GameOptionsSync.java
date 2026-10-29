package cn.dreamingfish.launcher.options;

import cn.dreamingfish.launcher.downloads.*;
import cn.dreamingfish.launcher.launch.LaunchFailure;
import cn.dreamingfish.launcher.storage.LauncherStorage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;

/** 调用方持有实例运行锁和模板锁；只在创建游戏进程前合并选中项。 */
public final class GameOptionsSync {
    public static final Map<String,Integer> SUPPORTED=Map.of("1.20.6",3839,"1.21.1",3955);
    private static final int LIMIT=1024*1024;
    private final LauncherStorage storage;
    public GameOptionsSync(Path root) { storage=new LauncherStorage(root); }
    public static void requireCompatible(String version,GameOptionsStore.Snapshot template) throws LaunchFailure {
        if(template.enabled()&&!template.values().isEmpty()&&!SUPPORTED.containsKey(version))throw new LaunchFailure("OPTIONS_UNSUPPORTED","默认游戏设置首批支持原版1.20.6、1.21.1；请关闭启动同步后再启动此版本");
    }
    public int apply(String instanceId,String version,UUID launchId,GameOptionsStore.Snapshot template,CancellationToken token) throws IOException {
        TaskRequest.uuid(instanceId);Objects.requireNonNull(launchId);token.check();requireCompatible(version,template);
        if(!template.enabled()||template.values().isEmpty())return 0;
        Path base=Path.of("instances",instanceId),file=base.resolve(".minecraft/options.txt");
        try {
            storage.requireDirectory(base.resolve(".minecraft"));boolean existed=storage.exists(file);byte[] original=existed?storage.read(file,LIMIT):new byte[0];
            String text=existed?StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(original)).toString():"version:"+SUPPORTED.get(version)+"\n";
            byte[] merged=merge(text,version,template.values());if(merged.length>LIMIT)throw invalid();
            if(existed&&Arrays.equals(original,merged))return 0;
            token.check();
            if(existed)try(var stage=storage.beginStagedWrite(base.resolve("options-backups"))) { stage.write(ByteBuffer.wrap(original));stage.publishNew(base.resolve("options-backups").resolve(launchId+"-options.txt")); }
            token.check();
            if(storage.exists(file)!=existed||existed&&!Arrays.equals(original,storage.read(file,LIMIT)))throw new LaunchFailure("OPTIONS_CHANGED","游戏配置在备份后发生变化，请重新启动检查；备份已保留");
            storage.writeAtomically(file,merged);return template.values().size();
        } catch(InterruptedIOException|LaunchFailure known) {throw known;} catch(IOException error) {throw new LaunchFailure("OPTIONS_SYNC_FAILED","游戏设置同步失败，请检查配置与备份目录；已有备份保留，未创建游戏进程");}
    }
    private static byte[] merge(String text,String version,Map<String,Object> values) throws LaunchFailure {
        String withoutCrLf=text.replace("\r\n","");
        if(text.indexOf('\0')>=0||withoutCrLf.indexOf('\r')>=0||text.contains("\r\n")&&withoutCrLf.indexOf('\n')>=0)throw invalid();
        String newline=text.contains("\r\n")?"\r\n":"\n";boolean trailing=text.endsWith("\n");
        String[] lines=text.split("\r?\n",-1);int length=lines.length-(trailing?1:0);var output=new ArrayList<String>();var written=new HashSet<String>();int versions=0;
        var encoded=new LinkedHashMap<String,String>();for(var f:GameOption.values())if(values.containsKey(f.key()))encoded.put(f.key(),f.encode(values.get(f.key())));
        for(int i=0;i<length;i++) {
            String line=lines[i];if(line.isEmpty()){output.add(line);continue;}int colon=line.indexOf(':');if(colon<1)throw invalid();String key=line.substring(0,colon);
            if(key.equals("version")){versions++;if(!line.substring(colon+1).equals(SUPPORTED.get(version).toString()))throw invalid();}
            if(encoded.containsKey(key)){if(written.add(key))output.add(key+":"+encoded.get(key));}else output.add(line);
        }
        if(versions!=1)throw invalid();
        encoded.forEach((key,value)->{if(written.add(key))output.add(key+":"+value);});
        return (String.join(newline,output)+(trailing?newline:"")).getBytes(StandardCharsets.UTF_8);
    }
    private static LaunchFailure invalid() {return new LaunchFailure("OPTIONS_INVALID","游戏options.txt格式或版本与当前实例不符；未覆盖原文件，请检查后再启动");}
}
