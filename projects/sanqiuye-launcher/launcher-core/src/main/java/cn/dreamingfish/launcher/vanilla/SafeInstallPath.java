package cn.dreamingfish.launcher.vanilla;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
public final class SafeInstallPath {
 private SafeInstallPath(){}
 public static Path parse(String value){if(value==null||value.isEmpty()||value.length()>1024||value.contains("\\")||value.contains(":")||value.chars().anyMatch(c->Character.isISOControl(c)||"<>\"|?*".indexOf(c)>=0))throw new IllegalArgumentException("安装路径无效");
  for(String part:value.split("/",-1)){if(part.isEmpty()||part.equals(".")||part.equals("..")||part.endsWith(".")||part.endsWith(" ")||part.split("\\.",2)[0].matches("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))throw new IllegalArgumentException("安装路径含不安全名称");}
  Path path=Path.of(value);if(path.isAbsolute())throw new IllegalArgumentException("安装路径必须为相对路径");return path;
 }
 public static String key(Path path){return Normalizer.normalize(path.toString().replace('\\','/'),Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
 public static void checkDistinct(Collection<Path> paths){var keys=new HashSet<String>();for(Path path:paths){parse(path.toString().replace('\\','/'));String key=key(path);if(!keys.add(key))throw new IllegalArgumentException("安装文件路径碰撞");}for(String key:keys){int pos=key.indexOf('/');while(pos>=0){if(keys.contains(key.substring(0,pos)))throw new IllegalArgumentException("文件与目录路径碰撞");pos=key.indexOf('/',pos+1);}}}
}
