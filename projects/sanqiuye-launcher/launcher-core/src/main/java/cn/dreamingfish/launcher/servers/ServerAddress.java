package cn.dreamingfish.launcher.servers;
import cn.dreamingfish.launcher.launch.LaunchFailure;
import java.net.*;import java.util.*;
/** 结构化地址始终作为单个参数传入进程，不解析命令、URL或凭据。 */
public record ServerAddress(String host,int port) {
 public ServerAddress {
  if(host==null||host.isBlank()||host.length()>253||!host.equals(host.trim())||port<1||port>65535||host.chars().anyMatch(c->Character.isISOControl(c)||Character.isWhitespace(c)||"/\\?#@[]%\"'".indexOf(c)>=0))throw new IllegalArgumentException("请填写有效主机名/IP和1–65535端口，不要填URL或命令");
  if(host.contains(":")){if(!host.matches("[0-9a-fA-F:.]+"))throw new IllegalArgumentException("IPv6地址无效");try{if(!(InetAddress.getByName(host) instanceof Inet6Address))throw new IllegalArgumentException("IPv6地址无效");}catch(UnknownHostException invalid){throw new IllegalArgumentException("IPv6地址无效");}}
  else {host=IDN.toASCII(host,IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);if(host.isEmpty()||host.length()>253||host.startsWith("-")||host.endsWith("."))throw new IllegalArgumentException("主机名无效");}
 }
 public String authority(){return (host.contains(":")?"["+host+"]":host)+":"+port;}
 public List<String> command(List<String> command,String version)throws LaunchFailure{
  if(!Set.of("1.20.6","1.21.1").contains(version))throw new LaunchFailure("DIRECT_CONNECT_UNSUPPORTED","本批启动直连支持1.20.6/1.21.1，其余版本请从游戏内加入");
  if(command.stream().anyMatch(arg->arg.equals("--quickPlayMultiplayer")||arg.equals("--server")))throw new LaunchFailure("DIRECT_CONNECT_UNSUPPORTED","启动计划已有直连参数，拒绝重复注入");
  var result=new ArrayList<>(command);result.add("--quickPlayMultiplayer");result.add(authority());return List.copyOf(result);
 }
}
