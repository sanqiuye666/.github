package cn.dreamingfish.launcher.vanilla;
import java.util.*;
public record Platform(String osName,String osVersion,String arch,int bits) {
 public Platform {if(!Set.of("windows","linux","osx").contains(osName)||osVersion==null||arch==null||bits!=32&&bits!=64)throw new IllegalArgumentException("系统平台无效");}
 public static Platform current(){String os=System.getProperty("os.name").toLowerCase(Locale.ROOT),arch=System.getProperty("os.arch");return new Platform(os.contains("win")?"windows":os.contains("mac")?"osx":"linux",System.getProperty("os.version"),arch,arch.contains("64")?64:32);}
}
