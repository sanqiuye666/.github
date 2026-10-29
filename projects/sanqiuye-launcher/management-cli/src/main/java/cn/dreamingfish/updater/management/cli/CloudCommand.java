package cn.dreamingfish.updater.management.cli;
import cn.dreamingfish.dispatch.server.config.ServerConfig;import cn.dreamingfish.dispatch.server.model.RemoteFailure;import cn.dreamingfish.dispatch.server.real.ServerSecrets;
import cn.dreamingfish.updater.management.cli.cloud.CloudAccountServer;import picocli.CommandLine;import java.nio.file.*;import java.nio.file.attribute.*;import java.security.*;import java.util.*;import java.util.concurrent.CountDownLatch;
/** 独立账号API入口，不初始化或迁移原管理工作区。 */
@CommandLine.Command(name="cloud",description="运行三秋叶云账号API（配置文件外置，仅绑定回环）")
final class CloudCommand implements Runnable {
 @CommandLine.ParentCommand ManagementCli root;
 @CommandLine.Option(names="--config",required=true,description="私有server.env绝对路径") Path file;
 @CommandLine.Option(names="--initialize-secrets",description="只初始化尚不存在的账号服务密钥文件，不回显内容") boolean initialize;
 @CommandLine.Option(names="--stdin-lifetime",hidden=true) boolean ownedPipe;
 public void run(){var config=ServerConfig.load(file,System.getenv());if(!config.enabled()||!config.mode().equals("real")||!config.role().equals("server"))throw RemoteFailure.of("CONFIG_INVALID");if(initialize){initialize(config);root.out().println("三秋叶账号密钥已生成，未显示内容。请核对文件权限后启动服务。");return;}
  var stopped=new CountDownLatch(1);try(var server=new CloudAccountServer(config)){Thread hook=new Thread(()->{server.close();stopped.countDown();},"sqy-cloud-shutdown");Runtime.getRuntime().addShutdownHook(hook);server.start();root.out().println("三秋叶云账号API已就绪（仅回环）；请通过安全通道验证后再配置公网HTTPS。");try{if(ownedPipe){while(root.input().readLine()!=null){/* 专用父进程断开才结束，不执行输入内容。 */}}else stopped.await();}catch(java.io.IOException e){throw new RemoteFailure("CONNECT_FAILED","专用服务管道已断开");}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){}}}
 }
 private void writeSecret(Path path,byte[] bytes)throws java.io.IOException{java.nio.channels.SeekableByteChannel channel;var options=Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);try{channel=Files.newByteChannel(path,options,PosixFilePermissions.asFileAttribute(Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE)));}catch(UnsupportedOperationException e){root.err().println("KEY_PERMISSIONS_UNVERIFIED：请手动限制秘密文件的访问权限。");channel=Files.newByteChannel(path,options);}try(var output=channel){var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())output.write(buffer);}}
 private void initialize(ServerConfig config){var files=List.of(Path.of(config.value("CLOUD_ADMIN_TOKEN_FILE")),Path.of(config.value("SESSION_SECRET_FILE")));if(files.getFirst().equals(files.getLast()))throw RemoteFailure.of("CONFIG_INVALID");for(var path:files){ServerSecrets.safePath(path);if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new RemoteFailure("CONFLICT","秘密文件已存在，不会覆盖");}
  try{for(var path:files){Files.createDirectories(path.getParent());byte[] random=new byte[32];new SecureRandom().nextBytes(random);byte[] text=Base64.getUrlEncoder().withoutPadding().encode(random);writeSecret(path,text);Arrays.fill(random,(byte)0);Arrays.fill(text,(byte)0);}}
  catch(Exception e){throw new RemoteFailure("CONFIG_INVALID","秘密文件初始化未完成；请检查私有目录，未覆盖已有文件");}
 }
}
