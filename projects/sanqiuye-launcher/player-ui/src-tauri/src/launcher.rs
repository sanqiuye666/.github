use reqwest::blocking::Client;
use serde::Serialize;
use serde_json::Value;
use std::{collections::VecDeque,io::{BufRead,BufReader,Read},path::{Path,PathBuf},process::{Child,ChildStdin,Command,Stdio},sync::{Arc,Mutex,atomic::{AtomicBool,AtomicUsize,Ordering},mpsc},time::{Duration,Instant}};
use tauri::State;

const API:&str="api/launcher/v1";
const RESPONSE_LIMIT:u64=2*1024*1024;

fn validate_request(method:&str,path:&str,body:&Option<Value>)->Result<(),String> {
    let route=path.split('?').next().unwrap_or("");
    if !matches!(method,"GET"|"POST")||path.len()>4096||!route.starts_with('/')||route.contains("//")||route.contains('%')||path.contains(['\\','#'])||path.chars().any(char::is_control)
        ||route.split('/').any(|s|matches!(s,"."|".."))
        ||!matches!(route.split('/').nth(1),Some("catalog"|"instances"|"installations"|"tasks"|"mods"|"packs"|"resources"|"player-servers"|"runtimes"|"identities"|"launch-checks"|"launches"|"game-options" | "server")) {
        return Err("本机请求路径或操作无效".into());
    }
    if method=="GET"&&body.as_ref().is_some_and(|v|!v.is_null()){return Err("读取操作不能携带正文".into());}
    if body.as_ref().is_some_and(|v|v.to_string().len()>131072){return Err("本机请求正文过大".into());}
    Ok(())
}
fn ready_url(line:&str)->Result<tauri::Url,String> {
    let value:Value=serde_json::from_str(line).map_err(|_|"本机核心没有返回有效就绪信息")?;
    let url=tauri::Url::parse(value["url"].as_str().ok_or("缺少本机核心地址")?).map_err(|_|"本机核心地址无效")?;
    if value["mode"]!="LOCAL"||url.scheme()!="http"||url.host_str()!=Some("127.0.0.1")||url.port().is_none_or(|p|p==0)||url.path()!="/"||url.query().is_some()||url.fragment().is_some()||!url.username().is_empty()||url.password().is_some(){return Err("拒绝非本次本机核心的地址".into());}
    Ok(url)
}
fn idle_tasks(value:&Value)->Result<bool,String> {
    let tasks=value["tasks"].as_array().ok_or("任务状态未能读取，请稍后关闭")?;
    if value["total"].as_u64().is_none(){return Err("任务计数无效，请稍后关闭".into());}
    for task in tasks {match task["state"].as_str(){Some("QUEUED"|"RESOLVING"|"DOWNLOADING"|"VERIFYING")=>return Ok(false),Some("COMPLETED"|"FAILED"|"CANCELLED"|"INTERRUPTED")=>{},_=>return Err("任务状态未知，请先检查任务".into())}}
    Ok(true)
}
fn idle_launches(value:&Value)->Result<bool,String> {
    value["canClose"].as_bool().ok_or_else(||"游戏状态未能确认，请检查运行记录后再关闭".into())
}
#[derive(Serialize)]
pub struct NativeReply {status:u16,body:Value}
#[derive(Clone)]
struct Endpoint {client:Client,base:tauri::Url,token:String}
impl Endpoint {
    fn request(&self,method:&str,path:&str,body:Option<Value>,timeout:Duration)->Result<NativeReply,String> {
        let url=self.base.join(&format!("{API}{path}")).map_err(|_|"本机请求地址无效")?;
        if url.origin()!=self.base.origin()||!url.path().starts_with("/api/launcher/v1/"){return Err("拒绝跨来源本机请求".into());}
        let mut request=self.client.request(if method=="POST"{reqwest::Method::POST}else{reqwest::Method::GET},url)
            .timeout(timeout).header("X-SQY-Client","launcher").header("Accept","application/json");
        if !self.token.is_empty(){request=request.header("X-SQY-Session",&self.token);}
        if method=="POST"{request=request.header("Content-Type","application/json").body(body.unwrap_or_else(||serde_json::json!({})).to_string());}
        let response=request.send().map_err(|_|"本机核心连接中断或超时，请重新连接")?;
        let status=response.status().as_u16();
        if !response.headers().get("Content-Type").and_then(|v|v.to_str().ok()).is_some_and(|v|v.starts_with("application/json")){return Err("本机核心响应格式无效".into());}
        let mut bytes=Vec::new();response.take(RESPONSE_LIMIT+1).read_to_end(&mut bytes).map_err(|_|"本机核心响应未读完")?;
        if bytes.len() as u64>RESPONSE_LIMIT{return Err("本机核心响应过大".into());}
        let body=serde_json::from_slice(&bytes).map_err(|_|"本机核心返回了无效JSON")?;
        Ok(NativeReply{status,body})
    }
    fn get(&self,path:&str)->Result<Value,String> {
        let reply=self.request("GET",path,None,Duration::from_secs(3))?;
        if reply.status!=200{return Err("本机状态未能读取，请稍后再关闭".into());}Ok(reply.body)
    }
}
struct Backend {child:Child,input:Option<ChildStdin>,endpoint:Endpoint,session:Value}
impl Backend {
    fn start(base:&Path)->Result<Self,String> {
        let java=base.join("runtime/bin/java.exe");let jar=base.join("player-sidecar.jar");let root=base.join("launcher-data");
        if !java.is_file(){return Err("未找到随包Java运行时，请重新解压完整启动器目录".into());}
        if !jar.is_file(){return Err("未找到本机核心player-sidecar.jar，请重新解压完整目录".into());}
        std::fs::create_dir_all(&root).map_err(|_|"无法创建启动器数据目录，请检查程序目录写入权限")?;
        let client=Client::builder().no_proxy().redirect(reqwest::redirect::Policy::none()).connect_timeout(Duration::from_secs(3)).timeout(Duration::from_secs(40)).build().map_err(|_|"本机连接初始化失败")?;
        let mut command=Command::new(java);
        command.current_dir(base).args(["-Xms32m","-Xmx512m","-Djava.net.useSystemProxies=true","-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Djava.awt.headless=true","-cp"])
            .arg(jar).arg("cn.dreamingfish.updater.player.launcher.LauncherWebMain").arg("--data-root").arg(&root).args(["--stdin-lifetime","true"])
            .stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::piped());
        #[cfg(windows)] {use std::os::windows::process::CommandExt;command.creation_flags(0x08000000);}
        let mut child=command.spawn().map_err(|error|format!("本机核心无法启动：{error}"))?;
        let input=child.stdin.take();let output=child.stdout.take().ok_or("无法读取本机核心就绪状态")?;let errors=Arc::new(Mutex::new(VecDeque::<String>::new()));
        if let Some(stderr)=child.stderr.take(){let captured=errors.clone();std::thread::spawn(move||{
            let mut reader=BufReader::new(stderr);
            loop {let mut line=String::new();match reader.by_ref().take(2049).read_line(&mut line){Ok(0)|Err(_)=>break,_=>{if let Ok(mut lines)=captured.lock(){lines.push_back(line.chars().take(1024).collect());while lines.len()>8{lines.pop_front();}}}}}
        });}
        let mut backend=Self{child,input,endpoint:Endpoint{client,base:tauri::Url::parse("http://127.0.0.1:1/").unwrap(),token:String::new()},session:Value::Null};
        let (sender,receiver)=mpsc::sync_channel(1);
        std::thread::spawn(move||{let mut line=String::new();let result=BufReader::new(output).take(8193).read_line(&mut line).map(|_|line);let _=sender.send(result);});
        let initialized=(||{
            let line=receiver.recv_timeout(Duration::from_secs(20)).map_err(|_|"本机核心启动超时，请重试")?.map_err(|_|"本机核心启动输出无法读取")?;
            if line.len()>8192{return Err("本机核心就绪信息过大".to_string());}
            backend.endpoint.base=ready_url(&line)?;
            let reply=backend.endpoint.request("GET","/session",None,Duration::from_secs(3))?;
            let token=reply.body["sessionToken"].as_str().ok_or("本机核心会话无效")?;
            if reply.status!=200||reply.body["schemaVersion"]!=1||reply.body["mode"]!="LOCAL"||!reply.body["capabilities"].is_object()||token.len()!=64||!token.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b)){return Err("本机核心会话无效".into());}
            backend.endpoint.token=token.into();backend.session=reply.body;Ok(())
        })();
        if let Err(error)=initialized {
            std::thread::sleep(Duration::from_millis(50));
            let detail=errors.lock().map(|lines|lines.iter().cloned().collect::<String>()).unwrap_or_default();
            let message=if detail.trim().is_empty(){error}else{format!("{error}\n{}",detail.trim())};
            log_status(&root,&message);return Err(message);
        }
        log_status(&root,"本机核心已就绪");Ok(backend)
    }
    fn running(&mut self)->bool {matches!(self.child.try_wait(),Ok(None))}
}
impl Drop for Backend {
    fn drop(&mut self) {
        self.input.take();let deadline=Instant::now()+Duration::from_secs(8);
        while Instant::now()<deadline {if !self.running(){return;}std::thread::sleep(Duration::from_millis(50));}
        let _=self.child.kill();let _=self.child.wait();
    }
}
fn log_status(root:&Path,message:&str) {
    use std::io::Write;
    let directory=root.join("logs");if std::fs::create_dir_all(&directory).is_err(){return;}
    let file=directory.join("native-shell.log");if file.metadata().is_ok_and(|m|m.len()>512*1024){return;}
    if let Ok(mut output)=std::fs::OpenOptions::new().create(true).append(true).open(file){let _=writeln!(output,"{} | {}",super::utc_timestamp(),message);}
}
#[derive(Default)]
struct Inner {backend:Mutex<Option<Backend>>,active:AtomicUsize,closing:AtomicBool}
#[derive(Clone,Default)]
pub struct NativeState(Arc<Inner>);
struct ActiveRequest(Arc<Inner>);
impl Drop for ActiveRequest {fn drop(&mut self){self.0.active.fetch_sub(1,Ordering::SeqCst);}}
impl NativeState {
    fn begin(&self)->Result<ActiveRequest,String> {
        if self.0.closing.load(Ordering::SeqCst){return Err("窗口正在检查关闭条件，请稍后操作".into());}
        self.0.active.fetch_update(Ordering::SeqCst,Ordering::SeqCst,|n|(n<8).then_some(n+1)).map_err(|_|"本机操作繁忙，请稍后重试")?;
        let permit=ActiveRequest(self.0.clone());
        if self.0.closing.load(Ordering::SeqCst){return Err("窗口正在关闭，请稍后操作".into());}Ok(permit)
    }
    fn session(&self,base:PathBuf)->Result<Value,String> {
        let mut backend=self.0.backend.lock().map_err(|_|"本机核心状态不可用")?;
        if backend.as_mut().is_some_and(|value|!value.running()){backend.take();}
        if backend.is_none(){*backend=Some(Backend::start(&base)?);}
        Ok(backend.as_ref().ok_or("本机核心未启动")?.session.clone())
    }
    fn endpoint(&self)->Result<Endpoint,String> {
        let mut guard=self.0.backend.lock().map_err(|_|"本机核心状态不可用")?;
        let backend=guard.as_mut().ok_or("本机核心未启动，请重新连接")?;
        if !backend.running(){return Err("本机核心已退出，请重新连接".into());}Ok(backend.endpoint.clone())
    }
    pub fn prepare_close(&self)->Result<(),String> {
        if self.0.closing.swap(true,Ordering::SeqCst){return Err("正在检查关闭条件，请稍候".into());}
        let result=(||{
            let deadline=Instant::now()+Duration::from_secs(8);
            while self.0.active.load(Ordering::SeqCst)>0 {if Instant::now()>=deadline{return Err("本机操作尚未结束，请稍后再关闭".into());}std::thread::sleep(Duration::from_millis(25));}
            let endpoint={let mut guard=self.0.backend.lock().map_err(|_|"本机核心状态不可用")?;let Some(backend)=guard.as_mut() else{return Ok(());};if !backend.running(){return Ok(());}backend.endpoint.clone()};
            let mut offset=0u64;
            loop {
                if Instant::now()>=deadline{return Err("关闭检查尚未完成，请稍后重试".into());}
                let tasks=endpoint.get(&format!("/tasks?offset={offset}&limit=100"))?;
                if !idle_tasks(&tasks)?{return Err("文件任务仍在进行，请先完成或取消任务，再关闭启动器".into());}
                offset+=100;if offset>=tasks["total"].as_u64().ok_or("任务计数无效")?{break;}
            }
            if !idle_launches(&endpoint.get("/launches/close-status")?)?{return Err("游戏仍在运行或状态待确认，请先正常退出游戏并检查运行记录，再关闭启动器".into());}
            Ok(())
        })();
        if result.is_err(){self.0.closing.store(false,Ordering::SeqCst);}result
    }
    pub fn shutdown(&self){if let Ok(mut guard)=self.0.backend.lock(){guard.take();}}
}
fn require_launcher(window:&tauri::WebviewWindow)->Result<(),String> {
    super::require_player(window)?;
    if super::entry::classify_entry_args(&std::env::args().skip(1).collect::<Vec<_>>())?!=super::entry::EntryMode::Launcher{return Err("此入口不能使用本机启动器服务".into());}Ok(())
}
fn resource_extensions(kind:&str)->Result<&'static [&'static str],String>{
    match kind {"RESOURCEPACKS"|"SHADERS"=>Ok(&["zip"]),"RECORDINGS"=>Ok(&["mcpr"]),"SCHEMATICS"=>Ok(&["litematic","schem","schematic","nbt"]),_=>Err("资源类型无效".into())}
}
static RESOURCE_PICKER:AtomicBool=AtomicBool::new(false);
fn resource_directory(base:&Path,instance_id:&str,kind:&str)->Result<PathBuf,String>{
    resource_extensions(kind)?;
    if instance_id.len()!=36||!instance_id.bytes().enumerate().all(|(i,b)|if [8,13,18,23].contains(&i){b==b'-'}else{b.is_ascii_hexdigit()&& !b.is_ascii_uppercase()}){return Err("实例ID无效".into());}
    let directory=match kind{"RESOURCEPACKS"=>"resourcepacks","SHADERS"=>"shaderpacks","RECORDINGS"=>"replay_recordings",_=>"schematics"};
    let root=base.canonicalize().map_err(|_|"程序目录不可用")?;
    let mut current=root.clone();
    for component in ["launcher-data","instances",instance_id,".minecraft",directory]{
        current.push(component);
        let metadata=std::fs::symlink_metadata(&current).map_err(|_|"资源目录尚未创建或不可读，请先导入资源")?;
        #[cfg(windows)]{use std::os::windows::fs::MetadataExt;if metadata.file_attributes()&0x400!=0{return Err("不能打开重解析资源目录".into());}}
        if metadata.file_type().is_symlink()||!metadata.is_dir(){return Err("资源路径必须是普通目录".into());}
    }
    let record=root.join("launcher-data/instances").join(instance_id).join("instance.json");
    let metadata=std::fs::symlink_metadata(record).map_err(|_|"实例记录不可读")?;
    #[cfg(windows)]{use std::os::windows::fs::MetadataExt;if metadata.file_attributes()&0x400!=0{return Err("实例记录不能是重解析点".into());}}
    if !metadata.is_file()||metadata.file_type().is_symlink(){return Err("实例记录必须是普通文件".into());}
    let canonical=current.canonicalize().map_err(|_|"资源目录不可读")?;
    if !canonical.starts_with(&root){return Err("资源目录超出程序范围".into());}Ok(canonical)
}
#[tauri::command]
pub fn launcher_open_resource_directory(window:tauri::WebviewWindow,instance_id:String,kind:String)->Result<(),String>{
    require_launcher(&window)?;
    let directory=resource_directory(&super::exe_dir()?,&instance_id,&kind)?;
    open::that(directory).map_err(|_|"无法打开资源文件夹，请检查系统文件管理器".into())
}
#[tauri::command]
pub async fn launcher_choose_resource(window:tauri::WebviewWindow,kind:String)->Result<Option<String>,String>{
    require_launcher(&window)?;let extensions=resource_extensions(&kind)?;
    if RESOURCE_PICKER.compare_exchange(false,true,Ordering::SeqCst,Ordering::SeqCst).is_err(){return Err("已有文件选择窗口，请先完成或取消".into());}
    tauri::async_runtime::spawn_blocking(move||{
        struct Reset;impl Drop for Reset{fn drop(&mut self){RESOURCE_PICKER.store(false,Ordering::SeqCst);}}let _reset=Reset;
        #[cfg(windows)]{Ok(rfd::FileDialog::new().set_title("选择要导入到三秋叶实例的资源").add_filter("游戏资源",extensions).pick_file().map(|p|p.to_string_lossy().into_owned()))}
        #[cfg(not(windows))]{let _=extensions;Err("此平台暂未接文件选择，请填写源文件路径".into())}
    }).await.map_err(|_|"文件选择未完成")?
}
#[cfg(feature="custom-protocol")]
const BUILD_IDENTITY:&str=concat!("SQY_NATIVE_BUILD:",env!("DFS_BUILD_ID"));
#[cfg(not(feature="custom-protocol"))]
const BUILD_IDENTITY:&str="SQY_NATIVE_BUILD:development";
#[tauri::command]
pub fn launcher_build_identity(window:tauri::WebviewWindow)->Result<String,String> {
    require_launcher(&window)?;Ok(BUILD_IDENTITY.into())
}
#[tauri::command]
pub async fn launcher_local_session(window:tauri::WebviewWindow,state:State<'_,NativeState>)->Result<Value,String> {
    require_launcher(&window)?;let permit=state.begin()?;let native=state.inner().clone();let base=super::exe_dir()?;
    tauri::async_runtime::spawn_blocking(move||{let _permit=permit;native.session(base)}).await.map_err(|_|"本机核心初始化失败")?
}
#[tauri::command]
pub async fn launcher_local_request(window:tauri::WebviewWindow,state:State<'_,NativeState>,method:String,path:String,body:Option<Value>)->Result<NativeReply,String> {
    require_launcher(&window)?;validate_request(&method,&path,&body)?;let permit=state.begin()?;let native=state.inner().clone();
    tauri::async_runtime::spawn_blocking(move||{let _permit=permit;native.endpoint()?.request(&method,&path,body,Duration::from_secs(40))}).await.map_err(|_|"本机操作未能完成")?
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn resource_directory_is_bound_to_own_instance_and_known_kind() {
        let base=std::env::temp_dir().join(format!("sqy-resource-open-{}",std::process::id()));
        let id="11111111-1111-4111-8111-111111111111";
        let instance=base.join("launcher-data/instances").join(id);
        std::fs::create_dir_all(instance.join(".minecraft/resourcepacks")).unwrap();
        std::fs::write(instance.join("instance.json"),b"{}").unwrap();
        assert_eq!(resource_directory(&base,id,"RESOURCEPACKS").unwrap(),instance.join(".minecraft/resourcepacks").canonicalize().unwrap());
        assert!(resource_directory(&base,"../other","RESOURCEPACKS").is_err());
        assert!(resource_directory(&base,id,"MODS").is_err());
        assert!(resource_directory(&base,id,"SCHEMATICS").is_err());
        std::fs::remove_dir_all(base).unwrap();
    }
    #[test]
    fn resource_picker_accepts_only_the_four_resource_types() {
        assert_eq!(resource_extensions("RESOURCEPACKS").unwrap(), &["zip"]);
        assert!(resource_extensions("SCHEMATICS").unwrap().contains(&"litematic"));
        assert!(resource_extensions("MODS").is_err());
    }
    #[test]
    fn ipc_accepts_only_relative_launcher_routes_and_bounded_json() {
        assert!(validate_request("GET","/instances",&None).is_ok());
        assert!(validate_request("GET","/game-options",&None).is_ok());
        assert!(validate_request("POST","/game-options",&Some(serde_json::json!({"revision":"00000000-0000-0000-0000-000000000000","enabled":false,"values":{}}))).is_ok());
        assert!(validate_request("GET","/mods/search?q=%E4%B8%89%E7%A7%8B",&None).is_ok());
        assert!(validate_request("GET","/packs/search?source=FTB&q=test",&None).is_ok());
        assert!(validate_request("GET","/packs/MODRINTH/Pack1234",&None).is_ok());
        assert!(validate_request("POST","/identities",&Some(serde_json::json!({"name":"测试"}))).is_ok());
        for path in ["https://example.test/","//127.0.0.1/","/../session","/instances/%2e%2e/x","/instances/../tasks","/instances\\x","/instances#token","/session","/other"] {
            assert!(validate_request("GET",path,&None).is_err(),"{path}");
        }
        assert!(validate_request("DELETE","/instances",&None).is_err());
        assert!(validate_request("POST","/identities",&Some(serde_json::json!({"name":"x".repeat(131072)}))).is_err());
    }
    #[test]
    fn readiness_rejects_nonlocal_or_credential_bearing_endpoints() {
        assert_eq!(ready_url(r#"{"url":"http://127.0.0.1:12345/","mode":"LOCAL"}"#).unwrap().port(),Some(12345));
        for url in ["http://localhost:12/","http://192.168.1.2:12/","https://example.test/","http://user@127.0.0.1:12/","http://127.0.0.1:12/other","http://127.0.0.1:12/?x=y"] {
            assert!(ready_url(&serde_json::json!({"url":url,"mode":"LOCAL"}).to_string()).is_err());
        }
    }
    #[test]
    fn close_guard_distinguishes_active_work_from_history_and_invalid_data() {
        assert!(idle_tasks(&serde_json::json!({"tasks":[{"state":"COMPLETED"}],"total":1})).unwrap());
        for state in ["QUEUED","RESOLVING","DOWNLOADING","VERIFYING"] {
            assert!(!idle_tasks(&serde_json::json!({"tasks":[{"state":state}],"total":1})).unwrap());
        }
        assert!(idle_tasks(&serde_json::json!({"wrong":true})).is_err());
        assert!(idle_launches(&serde_json::json!({"canClose":true})).unwrap());
        assert!(!idle_launches(&serde_json::json!({"canClose":false})).unwrap());
        assert!(idle_launches(&serde_json::json!({"launches":[{"state":"UNCERTAIN"}]})).is_err());
    }
}
