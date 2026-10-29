package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.vanilla.VanillaPlan;
import cn.dreamingfish.launcher.downloads.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public record LoaderPlan(VanillaPlan installation,String sourceSha256,String fingerprint) {
 public static String hash(byte[] bytes){return HexFormat.of().formatHex(new FileDigest(FileDigest.Algorithm.SHA256,"0".repeat(64)).newDigest().digest(bytes));}
 public static LoaderPlan fixed(VanillaPlan plan,String source){
  var value=new StringBuilder(plan.versionId()).append('\n').append(plan.component()).append('\n').append(source).append('\n');plan.files().stream().sorted(Comparator.comparing(f->f.relativePath().toString())).forEach(f->{var d=f.download();value.append(f.relativePath().toString().replace('\\','/')).append('\0').append(d.service()).append('\0').append(d.uri()).append('\0').append(d.expectedSize()).append('\0').append(d.digest()).append('\n');});return new LoaderPlan(plan,source,hash(value.toString().getBytes(StandardCharsets.UTF_8)));
 }
 public LoaderPlan requireFingerprint(String expected)throws TaskFailure{if(!fingerprint.equals(expected))throw new TaskFailure("PLAN_CHANGED","核心文件计划已变化，请重新生成计划");return this;}
}
