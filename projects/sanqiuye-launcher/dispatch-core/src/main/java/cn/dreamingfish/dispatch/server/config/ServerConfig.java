package cn.dreamingfish.dispatch.server.config;

import cn.dreamingfish.dispatch.server.model.RemoteFailure;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** 唯一外置配置加载器。解析不读取秘密文件、不创建目录、不访问网络。 */
public final class ServerConfig {
    private static final Map<String,String> DEFAULTS=defaults();
    private static final Set<String> PATHS=Set.of("MOCK_STATE_DIR","LOG_DIR","SSH_KEY_PATH","SSH_KNOWN_HOSTS_PATH","ACCOUNT_DB_PATH","SESSION_SECRET_FILE","CREDENTIAL_STORE_PATH","BACKUP_DIR","PACK_STORAGE_ROOT","SIGNING_KEY_FILE","RCON_PASSWORD_FILE","CLOUD_ADMIN_TOKEN_FILE");
    private static final Set<String> STRINGS=Set.of("SERVER_MODE","ROLE","TARGET_NAME","API_BASE_URL","SSH_HOST","SSH_PORT","SSH_USER","REMOTE_ROOT","REMOTE_SERVICE_NAME","REMOTE_JAVA_HOME","HTTP_BIND_HOST","HTTP_BIND_PORT","RCON_HOST","RCON_PORT","PROTECTED_PORTS","DEFAULT_GROUP_ID","MOCK_SCENARIO");
    private final Map<String,String> values;
    private ServerConfig(Map<String,String> values){this.values=Map.copyOf(values);}
    public static ServerConfig fromEnvironment(){return loadForApplication(null,System.getenv());}
    public static ServerConfig fromEnvironment(Path ownedFile){return loadForApplication(ownedFile,System.getenv());}
    /** 只读取启动入口明确传入的私有数据目录文件，不扫描用户目录。 */
    public static ServerConfig loadForApplication(Path ownedFile,Map<String,String> environment){
        String file=environment.get("SQY_CONFIG_FILE");
        if(file!=null&&file.isBlank())throw invalid("SQY_CONFIG_FILE");
        try{return load(file!=null?Path.of(file):ownedFile!=null&&Files.exists(ownedFile,LinkOption.NOFOLLOW_LINKS)?ownedFile:null,environment);}
        catch(InvalidPathException e){throw invalid("SQY_CONFIG_FILE");}
    }
    public static ServerConfig load(Path file,Map<String,String> environment){
        var values=new HashMap<>(DEFAULTS);
        if(file!=null){
            if(!file.isAbsolute())throw invalid("SQY_CONFIG_FILE");
            try {
                if(Files.size(file)>65536||Files.isSymbolicLink(file))throw invalid("SQY_CONFIG_FILE");
                var seen=new HashSet<String>();
                for(String line:Files.readAllLines(file)){
                    line=line.trim();if(line.isEmpty()||line.startsWith("#"))continue;int at=line.indexOf('=');
                    if(at<1)throw invalid("SQY_CONFIG_FILE");String key=line.substring(0,at).trim(),value=line.substring(at+1).trim();
                    if(!seen.add(key)||!known(key))throw invalid(key.startsWith("SQY_")?"SQY_CONFIG_FILE":"SQY_CONFIG_FILE");
                    if(value.length()>1&&((value.startsWith("\"")&&value.endsWith("\""))||(value.startsWith("'")&&value.endsWith("'"))))value=value.substring(1,value.length()-1);
                    values.put(key,value);
                }
            }catch(IOException|InvalidPathException e){throw invalid("SQY_CONFIG_FILE");}
        }
        for(var entry:environment.entrySet()){
            String key=entry.getKey();if(!key.startsWith("SQY_")||key.equals("SQY_CONFIG_FILE")||key.equals("SQY_CURSEFORGE_API_KEY"))continue;
            if(!known(key))throw invalid("SQY_CONFIG_FILE");values.put(key,entry.getValue());
        }
        for(var entry:values.entrySet()){
            String key=entry.getKey(),value=entry.getValue();
            if(value.isBlank()){if(DEFAULTS.containsKey(key)||key.endsWith("_PORT"))throw invalid(key);continue;}
            if(value.length()>4096||value.chars().anyMatch(c->c<32))throw invalid(key);
            if((key.endsWith("_ENABLED")||key.equals("SQY_ALLOW_LOOPBACK_HTTP"))&&!Set.of("true","false").contains(value))throw invalid(key);
            if(key.endsWith("_MS")||key.endsWith("_BYTES")||key.endsWith("_SECONDS")||key.endsWith("_DAYS")||key.endsWith("_ENTRIES")||key.endsWith("_PORT")||key.equals("SQY_RETRY_MAX")){
                long max=key.endsWith("_PORT")?65535:key.equals("SQY_RETRY_MAX")?2:key.endsWith("_MS")?3600000:1099511627776L;
                try{long number=Long.parseLong(value);if(number<(key.equals("SQY_RETRY_MAX")?0:1)||number>max)throw invalid(key);}catch(NumberFormatException e){throw invalid(key);}
            }
            if(PATHS.contains(key.substring(4))){
                try{if(value.startsWith("~/")||value.startsWith("~\\"))value=Path.of(System.getProperty("user.home")).resolve(value.substring(2)).toString();
                    Path path=Path.of(value);if(!path.isAbsolute())throw invalid(key);entry.setValue(path.normalize().toString());
                }catch(InvalidPathException e){throw invalid(key);}
            }
        }
        if(!Set.of("mock","real","").contains(values.getOrDefault("SQY_SERVER_MODE","")))throw invalid("SQY_SERVER_MODE");
        if(!Set.of("player","admin","server","").contains(values.getOrDefault("SQY_ROLE","")))throw invalid("SQY_ROLE");
        if(!Set.of("success","connect-failed","auth-failed","server-error","invalid-data","conflict").contains(values.get("SQY_MOCK_SCENARIO")))throw invalid("SQY_MOCK_SCENARIO");
        if(Long.parseLong(values.get("SQY_RETRY_BASE_MS"))>Long.parseLong(values.get("SQY_RETRY_MAX_MS")))throw invalid("SQY_RETRY_BASE_MS");
        if(Boolean.parseBoolean(values.get("SQY_SERVER_ENABLED"))){
            var required=new TreeSet<>(Set.of("SQY_SERVER_MODE","SQY_ROLE","SQY_LOG_DIR"));
            if("mock".equals(values.get("SQY_SERVER_MODE")))required.add("SQY_MOCK_STATE_DIR");
            else if("real".equals(values.get("SQY_SERVER_MODE"))){
                required.add("SQY_TARGET_NAME");if(!"server".equals(values.get("SQY_ROLE")))required.add("SQY_API_BASE_URL");
                if(Set.of("admin","server").contains(values.getOrDefault("SQY_ROLE",""))&&Boolean.parseBoolean(values.get("SQY_ACCOUNTS_ENABLED")))required.add("SQY_CLOUD_ADMIN_TOKEN_FILE");
                if(Boolean.parseBoolean(values.get("SQY_SSH_ENABLED")))required.addAll(Set.of("SQY_SSH_HOST","SQY_SSH_PORT","SQY_SSH_USER","SQY_SSH_KEY_PATH","SQY_SSH_KNOWN_HOSTS_PATH","SQY_REMOTE_ROOT","SQY_REMOTE_SERVICE_NAME","SQY_BACKUP_DIR"));
                if("server".equals(values.get("SQY_ROLE"))){
                    required.addAll(Set.of("SQY_HTTP_BIND_HOST","SQY_HTTP_BIND_PORT"));
                    if(Boolean.parseBoolean(values.get("SQY_ACCOUNTS_ENABLED")))required.addAll(Set.of("SQY_ACCOUNT_DB_PATH","SQY_SESSION_SECRET_FILE","SQY_DEFAULT_GROUP_ID"));
                    if(Boolean.parseBoolean(values.get("SQY_MANAGED_PACKS_ENABLED")))required.addAll(Set.of("SQY_PACK_STORAGE_ROOT","SQY_SIGNING_KEY_FILE"));
                    if(Boolean.parseBoolean(values.get("SQY_RCON_ENABLED")))required.addAll(Set.of("SQY_RCON_HOST","SQY_RCON_PORT","SQY_RCON_PASSWORD_FILE"));
                }
            }
            required.removeIf(key->!values.getOrDefault(key,"").isBlank());
            if(!required.isEmpty())throw new RemoteFailure("CONFIG_MISSING","缺少配置项："+String.join(", ",required));
        }
        String url=values.getOrDefault("SQY_API_BASE_URL","");
        if(!url.isBlank())try{var uri=URI.create(url);if(!("https".equals(uri.getScheme())||"http".equals(uri.getScheme())&&Boolean.parseBoolean(values.get("SQY_ALLOW_LOOPBACK_HTTP"))&&Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getFragment()!=null)throw invalid("SQY_API_BASE_URL");}catch(IllegalArgumentException e){throw invalid("SQY_API_BASE_URL");}
        if(!"server".equals(values.get("SQY_ROLE"))&&Boolean.parseBoolean(values.get("SQY_RCON_ENABLED")))throw invalid("SQY_RCON_ENABLED");
        if("player".equals(values.get("SQY_ROLE"))&&Boolean.parseBoolean(values.get("SQY_SSH_ENABLED")))throw invalid("SQY_SSH_ENABLED");
        for(String pair:List.of("MOCK_STATE_DIR:LOG_DIR","REMOTE_ROOT:BACKUP_DIR","ACCOUNT_DB_PATH:LOG_DIR","PACK_STORAGE_ROOT:LOG_DIR")){
            String[] keys=pair.split(":");String left=values.get("SQY_"+keys[0]),right=values.get("SQY_"+keys[1]);
            if(left!=null&&right!=null&&!left.isBlank()&&!right.isBlank()){var a=Path.of(left).toAbsolutePath().normalize();var b=Path.of(right).toAbsolutePath().normalize();if(a.startsWith(b)||b.startsWith(a))throw invalid("SQY_"+keys[1]);}
        }
        String ports=values.getOrDefault("SQY_PROTECTED_PORTS","");
        for(String port:ports.split(",")){if(port.isBlank())continue;try{int p=Integer.parseInt(port.trim());if(p<1||p>65535||port.trim().equals(values.get("SQY_HTTP_BIND_PORT")))throw invalid("SQY_PROTECTED_PORTS");}catch(NumberFormatException e){throw invalid("SQY_PROTECTED_PORTS");}}
        return new ServerConfig(values);
    }
    private static boolean known(String key){return key.startsWith("SQY_")&&(DEFAULTS.containsKey(key)||PATHS.contains(key.substring(4))||STRINGS.contains(key.substring(4)));}
    private static RemoteFailure invalid(String key){return new RemoteFailure("CONFIG_INVALID","配置无效："+key);}
    private static Map<String,String> defaults(){
        var map=new HashMap<String,String>();
        for(String key:List.of("ALLOW_LOOPBACK_HTTP","SERVER_ENABLED","SSH_ENABLED","ACCOUNTS_ENABLED","RCON_ENABLED","MANAGED_PACKS_ENABLED","REGISTRATION_ENABLED","PLAYER_REPORTS_ENABLED"))map.put("SQY_"+key,"false");
        map.put("SQY_AUTO_ROLLBACK_ENABLED","true");map.put("SQY_RETRY_MAX","1");map.put("SQY_RETRY_BASE_MS","500");map.put("SQY_RETRY_MAX_MS","2000");
        for(String key:List.of("CONNECT","RCON"))map.put("SQY_TIMEOUT_"+key+"_MS","10000");
        for(String key:List.of("RUNTIME","SESSION","DIRECTORY"))map.put("SQY_TIMEOUT_"+key+"_MS","15000");
        for(String key:List.of("SERVICE","PUBLISH"))map.put("SQY_TIMEOUT_"+key+"_MS","60000");
        for(String key:List.of("CONTENT","FILE"))map.put("SQY_TIMEOUT_"+key+"_MS","120000");
        map.put("SQY_TIMEOUT_WORLD_SAVE_MS","30000");map.put("SQY_TIMEOUT_DB_MS","5000");map.put("SQY_TIMEOUT_BACKUP_MS","180000");map.put("SQY_TIMEOUT_DEPLOY_MS","90000");map.put("SQY_TIMEOUT_UPLOAD_MS","600000");
        map.put("SQY_IDEMPOTENCY_TTL_SECONDS","86400");map.put("SQY_MAX_UPLOAD_BYTES","1073741824");map.put("SQY_MAX_EXTRACTED_BYTES","4294967296");map.put("SQY_MAX_ARCHIVE_ENTRIES","100000");map.put("SQY_PACK_DELETE_RETENTION_DAYS","7");
        map.put("SQY_DEFAULT_GROUP_ID","default");map.put("SQY_MOCK_SCENARIO","success");return Map.copyOf(map);
    }
    public boolean enabled(){return flag("SERVER_ENABLED");}
    public boolean flag(String name){return Boolean.parseBoolean(values.get("SQY_"+name));}
    public String value(String name){return values.getOrDefault("SQY_"+name,"");}
    public long number(String name){return Long.parseLong(value(name));}
    public String mode(){return value("SERVER_MODE");}public String role(){return value("ROLE");}public String scenario(){return value("MOCK_SCENARIO");}
    public Path stateRoot(){return Path.of(value("MOCK_STATE_DIR"));}public Path logRoot(){return Path.of(value("LOG_DIR"));}
    public int retryMax(){return (int)number("RETRY_MAX");}
    @Override public String toString(){return "ServerConfig[enabled="+enabled()+",mode="+mode()+",role="+role()+"]";}
}
