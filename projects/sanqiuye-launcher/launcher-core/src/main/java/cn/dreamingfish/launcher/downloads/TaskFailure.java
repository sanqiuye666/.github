package cn.dreamingfish.launcher.downloads;
import java.io.IOException;
/** Safe structured failure shared by task handlers and local routes. */
public final class TaskFailure extends IOException {
 private final String code;
 public TaskFailure(String code,String message){super(message);if(code==null||!code.matches("[A-Z_]{1,64}"))throw new IllegalArgumentException("错误码无效");this.code=code;}
 public String code(){return code;}
}
