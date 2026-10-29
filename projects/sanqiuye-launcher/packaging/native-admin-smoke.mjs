// 使用真实Tauri窗口和专用数据根验收，不读取用户已有连接或管理账户。
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import {spawn,spawnSync} from 'node:child_process';
import {createRequire} from 'node:module';
import {createHash,randomBytes,randomUUID} from 'node:crypto';
import {waitForNativeExit} from './native-exit.mjs';
const args=process.argv.slice(2);assert.equal(args.length%2,0);
const options=Object.fromEntries(Array.from({length:args.length/2},(_,i)=>[args[i*2],args[i*2+1]]));
const source=options['--exe'],output=options['--output-dir'],playwright=options['--playwright'];
assert.ok([source,output,playwright].every(p=>p&&path.isAbsolute(p)));assert.ok(!fs.existsSync(output),'保留旧验收输出');
const {chromium}=createRequire(import.meta.url)(playwright);
const python='python',pwsh='pwsh';
const sleep=ms=>new Promise(r=>setTimeout(r,ms)),checks=[],errors=[],consoleErrors=[];
const build=JSON.parse(fs.readFileSync(path.join(path.dirname(source),'native-build.json'),'utf8'));
fs.mkdirSync(output);const write=(name,value)=>fs.writeFileSync(path.join(output,name),JSON.stringify(value,null,2)+'\n');
write('.sqy-native-admin.json',{plan:'admin-exe',buildId:build.buildId});
function copyBundle(name,exclude=[]){
 const root=path.join(output,name);fs.mkdirSync(root);
 for(const [relative,digest] of Object.entries(build.files)){
  assert.ok(!path.isAbsolute(relative)&&!relative.split(/[\\/]/).some(p=>p==='..'||p==='data'));
  const input=path.join(path.dirname(source),relative);assert.ok(!fs.lstatSync(input).isSymbolicLink());
  assert.equal(createHash('sha256').update(fs.readFileSync(input)).digest('hex'),digest,relative);
  if(exclude.some(p=>relative===p||relative.startsWith(p+'/')))continue;
  const target=path.join(root,relative);fs.mkdirSync(path.dirname(target),{recursive:true});fs.copyFileSync(input,target);
 }
 return root;
}
function helper(args){const r=spawnSync(python,['-X','utf8','packaging/tests/windows-game-window.py',...args],{encoding:'utf8',windowsHide:true,timeout:20000});assert.equal(r.status,0,r.stderr||r.stdout);return JSON.parse(r.stdout);}
function processQuery(filter){
 assert.match(filter,/^[A-Za-z0-9 =]+$/);
 const r=spawnSync(pwsh,['-NoProfile','-Command',`[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); @(Get-CimInstance Win32_Process -Filter '${filter}' | Select-Object ProcessId,ParentProcessId,Name,CreationDate) | ConvertTo-Json -Depth 3 -Compress`],{encoding:'utf8',windowsHide:true,timeout:20000});
 assert.equal(r.status,0,r.stderr);const result=JSON.parse(r.stdout||'[]');return result?Array.isArray(result)?result:[result]:[];
}
const rpc=(page,command,args={})=>page.evaluate(({command,args})=>window.__TAURI_INTERNALS__.invoke(command,args),{command,args});
let active,closeCounter=0;
async function open(root,name,portable=false){
 const home=portable?path.join(root,'data'):path.join(output,name+'-home'),profile=path.join(output,name+'-webview');
 const port=await new Promise(resolve=>{const server=net.createServer();server.listen(0,'127.0.0.1',()=>{const p=server.address().port;server.close(()=>resolve(p));});});
 // 默认便携路径另验；旧版迁移来源限定到空测试根，避免读取真实AppData账户。
 const legacy=path.join(output,name+'-legacy-empty');if(portable)fs.mkdirSync(legacy);
 const child=spawn(path.join(root,path.basename(source)),portable?['--legacy-home',legacy]:['--desktop-home',home],{cwd:root,windowsHide:false,stdio:'ignore',env:{...process.env,WEBVIEW2_USER_DATA_FOLDER:profile,WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS:`--remote-debugging-port=${port} --remote-debugging-address=127.0.0.1`}});
 const app={child,home,root,exit:new Promise(r=>child.once('exit',r)),browser:null,main:null,commands:[]};active=app;
 let debug;
 for(let i=0;i<100;i++){assert.equal(child.exitCode,null,'管理EXE提前退出');try{debug=await(await fetch(`http://127.0.0.1:${port}/json/version`,{signal:AbortSignal.timeout(500)})).json();break;}catch{await sleep(400);}}
 assert.ok(debug,'管理WebView调试通道未启动');app.browser=await chromium.connectOverCDP(debug.webSocketDebuggerUrl);
 app.browser.contexts()[0].on('page',watch);for(const page of app.browser.contexts()[0].pages())watch(page);
 function watch(page){page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')consoleErrors.push(m.text());});page.on('request',r=>{if(r.url().startsWith('http://ipc.localhost/'))app.commands.push(new URL(r.url()).pathname.slice(1));});}
 app.main=app.browser.contexts()[0].pages().find(p=>p.url().includes('tauri.localhost'))??app.browser.contexts()[0].pages()[0];
 await app.main.locator('.connection-form').waitFor({timeout:20000});app.ownership=helper(['--pid',String(child.pid),'--action','inspect']);
 const children=processQuery(`ParentProcessId=${child.pid}`);assert.ok(children.some(p=>p.Name==='msedgewebview2.exe'));assert.ok(!children.some(p=>/^(cmd|powershell|pwsh)\.exe$/i.test(p.Name)));write(name+'-owned.json',{...app.ownership,children});
 return app;
}
async function capture(app,page,name){
 await page.screenshot({path:path.join(output,name+'-dom.png'),fullPage:true});
 const args=['--pid',String(app.child.pid),'--started-at',app.ownership.processStartedAt,'--title','三秋叶更新管理'];
 try{const capture=helper([...args,'--keep-visible','--capture-screen','--action','capture','--output',path.join(output,name+'-window.png')]);write(name+'-window.json',capture);}
 catch(error){if(!error.message.includes('本次窗口不在前台'))throw error;const info=helper([...args,'--action','window-info']);write(name+'-window.json',{...info,physicalScreenshot:false,reason:'前台归属保护拒绝截图；保留该原生WebView截图，不计物理截图通过'});}
}
async function workspace(app){
 for(let i=0;i<180;i++){const page=app.browser.contexts()[0].pages().find(p=>p.url().includes('workspace.html'));if(page){await page.locator('.sign-in,.shell').waitFor({timeout:25000});return page;}await sleep(250);}
 throw Error('实际管理工作区未打开');
}
async function quit(app,requestExit=()=>rpc(app.main,'quit_application')){
 const attempt=++closeCounter;
 const owned=processQuery(`ParentProcessId=${app.child.pid}`).filter(p=>p.Name==='java.exe');
 const started=Date.now();
 const code=await waitForNativeExit(async()=>{
  const response=await requestExit().then(()=>({resolved:true}),e=>({resolved:false,reason:String(e)}));
  write('quit-response-'+attempt+'.json',response);
 },app.exit,20000,()=>write('quit-timeout-'+attempt+'.json',{pid:app.child.pid,exitCode:app.child.exitCode,signal:app.child.signalCode,pageClosed:app.main.isClosed(),processes:processQuery(`ProcessId=${app.child.pid} OR ParentProcessId=${app.child.pid}`)}));
 write('quit-duration-'+attempt+'.json',{elapsedMs:Date.now()-started,budgetMs:20000,exitCode:code});
 assert.equal(code,0);
 for(const p of owned){const after=processQuery(`ProcessId=${p.ProcessId}`);assert.ok(!after.some(v=>v.CreationDate===p.CreationDate),'管理端遗留所属Java服务');}
 await app.browser.close().catch(()=>{});checks.push('normal-exit-and-owned-service-stopped');active=null;
}
const username='native_admin',password='Sqy!'+randomBytes(18).toString('hex');
async function signIn(page,register){
 await page.getByLabel('用户名',{exact:true}).fill(username);await page.getByLabel('密码',{exact:true}).fill(password);
 if(register)await page.getByLabel('确认密码',{exact:true}).fill(password);
 await page.getByRole('button',{name:'进入工作区',exact:true}).click();await page.locator('.shell').waitFor({timeout:25000});
}
try{
 const root=copyBundle('三秋叶 管理程序');active=await open(root,'main');
 const snapshot=await rpc(active.main,'load_desktop');assert.equal(snapshot.selfUpdateConfigured,false);assert.equal(snapshot.profiles.length,0);assert.equal(path.resolve(snapshot.dataRoot),path.resolve(active.home));
 await active.main.waitForTimeout(1800);await active.main.getByTitle('检查更新',{exact:true}).click();await active.main.getByRole('status').filter({hasText:'自更新服务尚未配置'}).waitFor();
 assert.ok(!active.commands.includes('check_update'),'未配置时自动/手动检查不应向Rust发出更新查询');checks.push('unconfigured-frontend-does-not-query-update');
 const updateGuards=[];for(const command of ['check_update','install_update','open_release_page']){const result=await rpc(active.main,command).then(()=>({rejected:false}),e=>({rejected:true,reason:String(e)}));assert.equal(result.rejected,true);assert.match(result.reason,/自更新服务尚未配置/);updateGuards.push({command,...result});}
 write('update-guards.json',updateGuards);checks.push('unconfigured-update-visible-and-three-native-guards');
 await rpc(active.main,'desktop_window_action',{action:'maximize'});assert.equal((await rpc(active.main,'desktop_window_action',{action:'status'})).maximized,true);
 await rpc(active.main,'desktop_window_action',{action:'maximize'});assert.equal((await rpc(active.main,'desktop_window_action',{action:'status'})).maximized,false);checks.push('real-maximize-and-restore');
 await rpc(active.main,'desktop_window_action',{action:'minimize'});assert.equal(helper(['--pid',String(active.child.pid),'--started-at',active.ownership.processStartedAt,'--title','三秋叶更新管理','--action','window-info']).minimized,true);checks.push('real-minimize');
 await capture(active,active.main,'connection');await active.main.getByRole('button',{name:'打开工作区',exact:true}).click();let page=await workspace(active);
 const connectionScroll=await active.main.evaluate(()=>[...document.querySelectorAll('body,body *')].filter(el=>getComputedStyle(el).scrollbarWidth!=='none').map(el=>el.tagName+'.'+el.className));assert.deepEqual(connectionScroll,[]);write('connection-hidden-scrollbars.json',{mismatched:connectionScroll});checks.push('native-connection-scrollbars-hidden');
 await page.getByRole('heading',{name:'创建管理账户',exact:true}).waitFor();await capture(active,page,'register');await signIn(page,true);checks.push('fresh-workspace-and-real-local-registration');
 await page.getByRole('button',{name:'创建项目',exact:true}).click();await page.getByRole('dialog',{name:'创建项目',exact:true}).waitFor();await page.getByRole('button',{name:'关闭弹窗',exact:true}).click();checks.push('existing-project-entry-preserved');
 await page.getByRole('button',{name:'玩家与账号',exact:true}).click();await page.getByText('调度服务待接入',{exact:true}).waitFor();
 for(const tab of ['账号管理','分组与下发','玩家看板'])await page.getByRole('navigation',{name:'玩家运营分区',exact:true}).getByRole('button',{name:tab,exact:true}).click();
 await capture(active,page,'players-pending');checks.push('cloud-player-capabilities-explicitly-pending');
 const workspaceScroll=await page.evaluate(()=>[...document.querySelectorAll('body,body *')].filter(el=>getComputedStyle(el).scrollbarWidth!=='none').map(el=>el.tagName+'.'+el.className));assert.deepEqual(workspaceScroll,[]);write('workspace-hidden-scrollbars.json',{mismatched:workspaceScroll});checks.push('native-admin-workspace-scrollbars-hidden');
 const denied=await rpc(page,'load_desktop').then(()=>false,()=>true);assert.ok(denied,'工作区不得调用连接页管理命令');checks.push('workspace-cannot-bypass-native-origin-gate');
 await page.getByRole('button',{name:'账户菜单',exact:true}).click();await page.getByRole('menuitem',{name:'退出账户',exact:true}).click();await page.getByRole('heading',{name:'登录管理端',exact:true}).waitFor();checks.push('real-logout');await signIn(page,false);checks.push('real-login');
 await rpc(page,'desktop_window_action',{action:'close'});await active.main.bringToFront();assert.equal(active.child.exitCode,null);assert.ok((await rpc(active.main,'load_desktop')).active);await rpc(active.main,'show_workspace');await page.locator('.shell').waitFor();checks.push('workspace-close-hides-and-can-reopen');
 await capture(active,page,'workspace');await quit(active);
 active=await open(root,'main');assert.equal((await rpc(active.main,'load_desktop')).profiles.length,1);await active.main.locator('.connection-pick').first().click();await active.main.getByRole('button',{name:'打开工作区',exact:true}).click();page=await workspace(active);await signIn(page,false);assert.equal((await rpc(active.main,'load_desktop')).profiles.length,1);checks.push('reopen-preserves-connection-and-account');await quit(active);
 if(options['--cloud-config']){
  assert.ok(path.isAbsolute(options['--cloud-config'])&&path.isAbsolute(options['--cloud-fixture']));const data=path.join(output,'main-home','management');assert.ok(fs.existsSync(path.join(data,'management-web-auth.json')));assert.ok(!fs.existsSync(path.join(data,'server.env')));fs.copyFileSync(options['--cloud-config'],path.join(data,'server.env'));
  active=await open(root,'main');await active.main.locator('.connection-pick').first().click();await active.main.getByRole('button',{name:'打开工作区',exact:true}).click();page=await workspace(active);await signIn(page,false);await page.getByRole('button',{name:'玩家与账号',exact:true}).click();const fixture=JSON.parse(fs.readFileSync(options['--cloud-fixture'],'utf8'));const cloud=page.getByRole('region',{name:'云端玩家账号',exact:true});await cloud.waitFor();await page.locator('[data-cloud-player="'+fixture.username+'"]').waitFor({timeout:20000});assert.ok((await cloud.innerText()).includes(fixture.displayName));assert.ok(!(await cloud.innerText()).includes(fixture.password));await capture(active,page,'cloud-player-accounts');checks.push('real-cloud-account-auto-visible-after-player-registration');
  const api=new URL(fixture.apiBaseUrl);assert.ok(['127.0.0.1','localhost','[::1]'].includes(api.hostname)&&api.protocol==='http:');const later=fixture.username+'_live';const registration=await fetch(new URL('account/register',api),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({operationId:randomUUID(),username:later,displayName:'自动同步验收',password:fixture.password}),signal:AbortSignal.timeout(15000)});assert.equal(registration.status,200);await page.locator('[data-cloud-player="'+later+'"]').waitFor({timeout:20000});await capture(active,page,'cloud-auto-refresh-new-account');checks.push('real-cloud-new-account-visible-without-manual-refresh');
  const typography=await cloud.evaluate(el=>{const s=getComputedStyle(el),body=getComputedStyle(document.body),root=getComputedStyle(document.documentElement);return {font:s.fontFamily,fontVariable:s.getPropertyValue('--font'),body:body.fontFamily,root:root.fontFamily,padding:Number.parseFloat(s.paddingLeft),width:el.clientWidth,scroll:el.scrollWidth};});write('cloud-account-layout.json',typography);assert.ok(typography.padding>=20,'账号页面缺少边缘留白');assert.ok(typography.font.includes('Segoe'),'账号页面没有统一界面字体');assert.ok(typography.scroll<=typography.width,'账号页横向溢出');checks.push('cloud-account-page-spacing-and-font');await cloud.locator('[data-cloud-admin-refresh]').click();await cloud.getByRole('status').waitFor();checks.push('cloud-account-list-refresh-and-private-fields-excluded');await quit(active);
 }
 for(const [name,exclude] of [['missing-runtime',['core/runtime']],['missing-core',['core/dfs-admin.jar']]]){
  const broken=copyBundle(name,exclude);active=await open(broken,name);await active.main.getByRole('button',{name:'打开工作区',exact:true}).click();await active.main.locator('.connection-status.error').waitFor({timeout:20000});const reason=await active.main.locator('.connection-status.error').innerText();assert.match(reason,/Java|java|core|核心|JAR|jar|运行/);write(name+'-reason.json',{reason});await capture(active,active.main,name);checks.push(name+'-visible-error');await quit(active);
 }
 const portableRoot=copyBundle('portable-default');active=await open(portableRoot,'portable-default',true);
 const portable=await rpc(active.main,'load_desktop');assert.equal(path.resolve(portable.dataRoot),path.resolve(portableRoot,'data'));assert.equal(portable.dataBesideProgram,true);assert.equal(portable.profiles.length,0);
 await capture(active,active.main,'portable-default');checks.push('default-portable-data-path-without-real-legacy-migration');
 await quit(active,()=>active.main.getByRole('button',{name:'退出桌面端',exact:true}).click());checks.push('real-footer-quit-button');
 assert.deepEqual(errors,[]);assert.deepEqual(consoleErrors,[]);write('results.json',{passed:true,buildId:build.buildId,checks,pageErrors:errors,consoleErrors});console.log(JSON.stringify({passed:true,checks:checks.length,buildId:build.buildId}));
}catch(error){
 write('results.json',{passed:false,checks,pageErrors:errors,consoleErrors,error:String(error)});
 if(active?.main)await active.main.screenshot({path:path.join(output,'failure-dom.png'),fullPage:true}).catch(()=>{});
 if(active?.browser)await quit(active).catch(()=>{if(active.child.exitCode===null){const now=helper(['--pid',String(active.child.pid),'--action','inspect']);assert.equal(now.processStartedAt,active.ownership.processStartedAt);active.child.kill();write('cleanup-owned-process.json',{forced:true,pid:active.child.pid,processStartedAt:now.processStartedAt});}});
 if(active?.browser)await active.browser.close().catch(()=>{});
 console.error(String(error));process.exitCode=1;
}
