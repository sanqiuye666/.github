package cn.dreamingfish.updater.management.cli.cloud;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;import org.bouncycastle.crypto.params.Argon2Parameters;
import java.security.*;import java.util.*;
/** Argon2id v1：19MiB、2次、并行度1；独立盐，不保存可逆口令。 */
final class AccountPasswords {
 private AccountPasswords(){}
 static byte[] random(int count){byte[] b=new byte[count];new SecureRandom().nextBytes(b);return b;}
 static byte[] derive(String password,byte[] salt){char[] chars=password.toCharArray();try{var parameters=new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(19456).withIterations(2).withParallelism(1).withSalt(salt).build();var generator=new Argon2BytesGenerator();generator.init(parameters);byte[] result=new byte[32];generator.generateBytes(chars,result);return result;}finally{Arrays.fill(chars,Character.MIN_VALUE);}}
 static String token(){return Base64.getUrlEncoder().withoutPadding().encodeToString(random(32));}
 static boolean equal(byte[] a,byte[] b){return MessageDigest.isEqual(a,b);}
}
