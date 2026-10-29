package cn.dreamingfish.launcher.downloads;
import java.security.*;
import java.util.*;
public record FileDigest(Algorithm algorithm,String hex) {
 public enum Algorithm {SHA1("SHA-1",40),SHA256("SHA-256",64),SHA512("SHA-512",128);final String jca;final int length;Algorithm(String jca,int length){this.jca=jca;this.length=length;}}
 public FileDigest {Objects.requireNonNull(algorithm);if(hex==null||!hex.matches("[0-9a-fA-F]{"+algorithm.length+"}"))throw new IllegalArgumentException("摘要格式无效");hex=hex.toLowerCase(Locale.ROOT);}
 public MessageDigest newDigest(){try{return MessageDigest.getInstance(algorithm.jca);}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
