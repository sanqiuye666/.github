package cn.dreamingfish.launcher.servers;
import cn.dreamingfish.launcher.storage.*;import cn.dreamingfish.launcher.downloads.TaskRequest;import cn.dreamingfish.launcher.launch.LaunchFailure;
import java.io.*;import java.nio.file.*;import java.util.*;
/** 玩家自选收藏；与云端服务器配置、面板账号无关。 */
public final class ServerStore {
 public record Favorite(String id,String name,ServerAddress address){}
 public record Snapshot(String revision,List<Favorite> servers){public Snapshot{servers=List.copyOf(servers);}}
 private final LauncherStorage storage;private static final Path FILE=Path.of("player-servers/servers.json");
 public ServerStore(Path root){storage=new LauncherStorage(root);}
 public Snapshot read()throws IOException{try(var lock=storage.lock(Path.of("player-servers/.store.lock"))){return load();}}
 private Snapshot load()throws IOException{
  if(!storage.exists(FILE)){var first=new Snapshot(UUID.randomUUID().toString(),List.of());storage.writeAtomically(FILE,LauncherJson.write(first));return first;}
  var n=LauncherJson.read(storage.read(FILE,65536),65536);keys(n,Set.of("revision","servers"));String revision=LauncherJson.text(n,"revision",36);TaskRequest.uuid(revision);if(!n.path("servers").isArray()||n.path("servers").size()>64)throw new IOException("收藏记录无效");var list=new ArrayList<Favorite>();var ids=new HashSet<String>();
  for(var v:n.path("servers")){keys(v,Set.of("id","name","address"));keys(v.path("address"),Set.of("host","port"));String id=LauncherJson.text(v,"id",36);TaskRequest.uuid(id);if(!ids.add(id)||!v.path("address").path("port").isInt())throw new IOException("收藏ID或端口无效");list.add(new Favorite(id,label(LauncherJson.text(v,"name",64)),new ServerAddress(LauncherJson.text(v.path("address"),"host",253),v.path("address").path("port").intValue())));}
  return new Snapshot(revision,list);
 }
 public Favorite require(String id)throws IOException{TaskRequest.uuid(id);return read().servers().stream().filter(s->s.id().equals(id)).findFirst().orElseThrow(()->new LaunchFailure("SERVER_NOT_FOUND","服务器收藏不存在，请刷新"));}
 public Favorite require(String revision,String id)throws IOException{var snapshot=read();same(snapshot,revision);return snapshot.servers().stream().filter(s->s.id().equals(id)).findFirst().orElseThrow(()->new LaunchFailure("SERVER_NOT_FOUND","服务器收藏不存在，请刷新"));}
 public Snapshot save(String revision,String id,String name,ServerAddress address)throws IOException{
  TaskRequest.uuid(revision);if(id!=null)TaskRequest.uuid(id);String valid=label(name);Objects.requireNonNull(address);
  try(var lock=storage.lock(Path.of("player-servers/.store.lock"))){var current=load();same(current,revision);var entries=new ArrayList<>(current.servers());if(id!=null&&!entries.removeIf(e->e.id().equals(id)))throw new LaunchFailure("SERVER_NOT_FOUND","收藏不存在，请刷新");if(entries.size()>=64)throw new LaunchFailure("SERVER_LIMIT","最多保存64个服务器收藏");entries.add(new Favorite(id==null?UUID.randomUUID().toString():id,valid,address));return commit(entries);}
 }
 public Snapshot remove(String revision,String id)throws IOException{TaskRequest.uuid(revision);TaskRequest.uuid(id);try(var lock=storage.lock(Path.of("player-servers/.store.lock"))){var current=load();same(current,revision);var entries=new ArrayList<>(current.servers());if(!entries.removeIf(e->e.id().equals(id)))throw new LaunchFailure("SERVER_NOT_FOUND","收藏不存在，请刷新");return commit(entries);}}
 private Snapshot commit(List<Favorite> list)throws IOException{var next=new Snapshot(UUID.randomUUID().toString(),list);storage.writeAtomically(Path.of("player-servers/previous.json"),storage.read(FILE,65536));storage.writeAtomically(FILE,LauncherJson.write(next));return next;}
 private static void same(Snapshot s,String revision)throws LaunchFailure{if(!s.revision().equals(revision))throw new LaunchFailure("SERVER_CHANGED","收藏在其他操作中变化，请刷新后再修改");}
 private static String label(String name){if(name==null||name.isBlank()||name.length()>64||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("名称需要1–64个字符");return name.trim();}
 private static void keys(com.fasterxml.jackson.databind.JsonNode n,Set<String> expected)throws IOException{var keys=new HashSet<String>();n.fieldNames().forEachRemaining(keys::add);if(!n.isObject()||!keys.equals(expected))throw new IOException("收藏记录字段无效");}
}
