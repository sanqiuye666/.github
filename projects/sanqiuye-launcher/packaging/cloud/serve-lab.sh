#!/usr/bin/env bash
# 用户在服务器手动运行。只在本人HOME下的指定账号API目录操作，不改nginx/Minecraft。
set -euo pipefail
umask 077
root_arg=${1:?需要专用目录}
port=${2:?需要空闲内部端口}
jar_arg=${3:?需要已上传JAR}
protected=${4:?需要保护端口列表}
[[ "$port" =~ ^[0-9]+$ ]] && ((port>=1024&&port<=65535)) || { echo '内部端口无效'; exit 2; }
[[ ",${protected}," != *",${port},"* ]] || { echo '拒绝使用受保护端口'; exit 2; }
[[ "$root_arg" =~ ^[A-Za-z0-9_./-]+$ && "$jar_arg" =~ ^[A-Za-z0-9_./-]+$ ]] || { echo '请使用不含空格或特殊字符的专用路径'; exit 2; }
root=$(realpath -m -- "$root_arg")
user_home=$(realpath -- "$HOME")
[[ "$root" == "$user_home/"* && "$root" != "$user_home" && ! -L "$root_arg" ]] || { echo '目录必须在当前用户HOME内，且不能是HOME或符号链接'; exit 2; }
jar=$(realpath -- "$jar_arg")
[[ -f "$jar" && ! -L "$jar_arg" ]] || { echo '上传的JAR无效'; exit 2; }
java_bin=$(command -v java || true)
[[ -n "$java_bin" ]] && "$java_bin" -version 2>&1 | head -1 | grep -Eq 'version "21\.' || { echo '需要Java21；Ubuntu可先执行：sudo apt-get install openjdk-21-jre-headless'; exit 3; }
for part in backups private data logs releases service.pid; do
 [[ ! -L "$root/$part" ]] || { echo '专用路径含符号链接，拒绝写入'; exit 4; }
done
if [[ -e "$root/current" && ! -L "$root/current" ]]; then echo '当前版本标记不是受管链接，拒绝覆盖'; exit 4; fi
if [[ -f "$root/service.pid" ]]; then
 old_pid=$(cat "$root/service.pid")
 [[ "$old_pid" =~ ^[0-9]+$ ]] || { echo '已有PID记录格式不符'; exit 4; }
 if kill -0 "$old_pid" 2>/dev/null; then echo '已有进程仍在运行；请先核对专用服务并正常停止，脚本不会结束任何进程'; exit 4; fi
fi
python3 - "$port" <<'PY'
import socket,sys
with socket.socket() as s:s.bind(('localhost',int(sys.argv[1])))
PY
stamp=$(date -u +%Y%m%dT%H%M%SZ)
backup="$root/backups/$stamp"
mkdir -p -- "$backup" "$root/private" "$root/data" "$root/logs" "$root/releases"
for item in private data current service.pid; do
 if [[ -e "$root/$item" || -L "$root/$item" ]]; then cp -a -- "$root/$item" "$backup/"; fi
done
if [[ -L "$root/current" ]]; then
 previous=$(realpath -- "$root/current")
 [[ "$previous" == "$root/releases/"* ]] || { echo '当前版本归属无法确认，保留备份并停止'; exit 4; }
 cp -a -- "$previous" "$backup/release"
fi
digest=$(sha256sum -- "$jar" | cut -d' ' -f1)
release="$root/releases/${stamp}-${digest:0:12}"
mkdir -- "$release"
install -m 600 -- "$jar" "$release/dfs-admin.jar"
config="$root/private/server.env"
[[ ! -L "$config" ]] || { echo '配置文件不能是符号链接'; exit 4; }
if [[ ! -e "$config" ]]; then
 cat > "$config" <<EOF
SQY_SERVER_ENABLED=true
SQY_SERVER_MODE=real
SQY_ROLE=server
SQY_TARGET_NAME=account-api
SQY_ACCOUNTS_ENABLED=true
SQY_REGISTRATION_ENABLED=true
SQY_MANAGED_PACKS_ENABLED=false
SQY_PLAYER_REPORTS_ENABLED=false
SQY_SSH_ENABLED=false
SQY_RCON_ENABLED=false
SQY_LOG_DIR=$root/logs
SQY_HTTP_BIND_HOST=localhost
SQY_HTTP_BIND_PORT=$port
SQY_PROTECTED_PORTS=$protected
SQY_ACCOUNT_DB_PATH=$root/data/accounts.sqlite
SQY_CLOUD_ADMIN_TOKEN_FILE=$root/private/admin.token
SQY_SESSION_SECRET_FILE=$root/private/sessions.key
EOF
fi
if [[ ! -e "$root/private/admin.token" && ! -e "$root/private/sessions.key" ]]; then
 "$java_bin" -jar "$release/dfs-admin.jar" cloud --config "$config" --initialize-secrets
fi
nohup "$java_bin" -jar "$release/dfs-admin.jar" cloud --config "$config" > "$root/logs/service-start.log" 2>&1 < /dev/null &
pid=$!
printf '%s\n' "$pid" > "$root/service.pid"
ready=false
for _ in {1..40}; do
 if ! kill -0 "$pid" 2>/dev/null; then break; fi
 if python3 - "$port" <<'PY'
import urllib.request,json,sys
try:
 with urllib.request.urlopen('http://localhost:'+sys.argv[1]+'/api/v1/health',timeout=1) as r:assert json.load(r).get('status')=='ready'
except Exception:sys.exit(1)
PY
 then ready=true;break;fi
 sleep 0.5
done
if [[ "$ready" != true ]]; then
 python3 - "$pid" "$release/dfs-admin.jar" "$config" <<'PY'
import os,signal,sys
from pathlib import Path
pid=int(sys.argv[1]);proc=Path('/proc')/str(pid)
try:
 args=(proc/'cmdline').read_bytes().split(b'\0')
 if proc.stat().st_uid==os.getuid() and os.fsencode(sys.argv[2]) in args and os.fsencode(sys.argv[3]) in args and b'cloud' in args:os.kill(pid,signal.SIGTERM)
except (FileNotFoundError,ProcessLookupError):pass
PY
 echo '新服务未就绪，已按归属核对停止本次新进程；查看专用目录logs/service-start.log。当前入口未切换，备份保留。'; exit 5
fi
ln -s -- "$release" "$root/current-$stamp"
mv -Tf -- "$root/current-$stamp" "$root/current"
printf '账号API已就绪，仅回环；PID=%s；JAR SHA256=%s；备份=%s\n' "$pid" "$digest" "$backup"
echo '尚未开放公网，未更改nginx或Minecraft。请通过SSH隧道继续验证。'
