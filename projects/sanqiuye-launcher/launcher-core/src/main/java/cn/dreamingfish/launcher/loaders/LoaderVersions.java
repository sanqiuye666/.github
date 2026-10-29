package cn.dreamingfish.launcher.loaders;
import java.math.BigInteger;
import java.util.*;
public final class LoaderVersions {
 private LoaderVersions(){}
 public static int newestFirst(String a,String b){String[] x=a.split("[.\\-+]"),y=b.split("[.\\-+]");for(int i=0;i<Math.min(x.length,y.length);i++){int c=x[i].matches("[0-9]+")&&y[i].matches("[0-9]+")?new BigInteger(y[i]).compareTo(new BigInteger(x[i])):y[i].compareToIgnoreCase(x[i]);if(c!=0)return c;}return Integer.compare(y.length,x.length);}
 public record Version(String version,boolean stable){}
}
