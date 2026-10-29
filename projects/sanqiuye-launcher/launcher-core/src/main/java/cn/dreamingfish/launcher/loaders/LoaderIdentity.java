package cn.dreamingfish.launcher.loaders;
import cn.dreamingfish.launcher.GameInstance.LoaderKind;

/** v3收据明确记录核心身份；原版/Fabric继续使用原有v1/v2格式。 */
public record LoaderIdentity(LoaderKind kind,String version) {
 public LoaderIdentity {if(kind==null||!java.util.Set.of(LoaderKind.QUILT,LoaderKind.FORGE,LoaderKind.NEOFORGE).contains(kind))throw new IllegalArgumentException("不支持的核心身份");validateVersion(version);}
 public static void validateVersion(String value){if(value==null||!value.matches("[A-Za-z0-9][A-Za-z0-9_.+\\-]{0,127}")||value.contains(".."))throw new IllegalArgumentException("核心版本标识无效");}
 public String profilePath(){return kind==LoaderKind.QUILT?"versions/quilt.json":"versions/loader.json";}
}
