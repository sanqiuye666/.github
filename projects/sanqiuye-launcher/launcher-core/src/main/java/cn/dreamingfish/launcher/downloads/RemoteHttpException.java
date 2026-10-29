package cn.dreamingfish.launcher.downloads;
import java.io.IOException;
public final class RemoteHttpException extends IOException {
 private final int status;private final String code;private final Integer retry;
 public RemoteHttpException(int status,String code,Integer retry){super(code.equals("RATE_LIMITED")?"来源限流，请在 "+retry+" 秒后重试":"官方来源返回 HTTP "+status);this.status=status;this.code=code;this.retry=retry;}
 public int status(){return status;}public String code(){return code;}public Integer retryAfterSeconds(){return retry;}
}
