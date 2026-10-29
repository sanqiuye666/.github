import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import {spawn,spawnSync} from 'node:child_process';
import {createRequire} from 'node:module';
import {createHash,randomUUID} from 'node:crypto';
import {checkModInstallation,confirmModGame} from './native-mod-checks.mjs';
import {checkNativeContent} from './native-content-checks.mjs';
import {checkNativeVersionNavigation} from './native-version-navigation-checks.mjs';
import {checkAutoCottage} from './native-auto-cottage-checks.mjs';
import {importCustomIntro,customIntroReopened,restoreCustomIntro,checkNativeCloudAccount} from './native-custom-intro-checks.mjs';
import {prepareNativeResources,checkNativeResources,checkNativeResourcesReopened} from './native-resource-checks.mjs';
import {checkNativeUi} from './native-ui-checks.mjs';
import {checkFabricInstallation,confirmFabricGame} from './native-fabric-checks.mjs';
import {checkLoaderInstallation,confirmLoaderGame} from './native-loader-checks.mjs';

const argv=process.argv.slice(2);assert.equal(argv.length%2,0);
const options=Object.fromEntries(Array.from({length:argv.length/2},(_,i)=>[argv[i*2],argv[i*2+1]]));
const sourceExe=options['--exe'],out=options['--output-dir'],playwright=options['--playwright'],mode=options['--mode']??'basic';
assert.ok([sourceExe,out,playwright].every(v=>v&&path.isAbsolute(v)));assert.ok(['basic','game','packs','options','ui','fabric','loader','mods','content','version-navigation','resources','auto-cottage','custom-intro','cloud-account'].includes(mode));assert.ok(!fs.existsSync(out),'验收输出必须是新目录');
const require=createRequire(import.meta.url),{chromium}=require(playwright);
const python='python',pwsh='pwsh';
const write=(name,value)=>fs.writeFileSync(path.join(out,name),JSON.stringify(value,null,2)+'\n');
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const build=JSON.parse(fs.readFileSync(path.join(path.dirname(sourceExe),'native-build.json'),'utf8'));
fs.mkdirSync(out,{recursive:false});write('.sqy-native-smoke.json',{plan:'native-player',mode,buildId:build.buildId});
function copyBundle(name,excluded=[]){
 const target=path.join(out,name);fs.mkdirSync(target);
 for(const [relative,expected] of Object.entries(build.files)){
  assert.ok(!path.isAbsolute(relative)&&!relative.split(/[\\/]/).some(p=>p==='..'||p==='launcher-data'));
  if(excluded.some(prefix=>relative===prefix||relative.startsWith(prefix+'/')))continue;
  const source=path.join(path.dirname(sourceExe),relative),destination=path.join(target,relative);
  assert.ok(!fs.lstatSync(source).isSymbolicLink());assert.equal(hash(fs.readFileSync(source)),expected,relative);
  fs.mkdirSync(path.dirname(destination),{recursive:true});fs.copyFileSync(source,destination);
 }
 fs.copyFileSync(path.join(path.dirname(sourceExe),'native-build.json'),path.join(target,'native-build.json'));return target;
}
const appRoot=copyBundle('三秋叶 程序');
if(mode==='cloud-account'){assert.ok(options['--server-config']&&path.isAbsolute(options['--server-config']));fs.mkdirSync(path.join(appRoot,'launcher-data'),{recursive:true});fs.copyFileSync(options['--server-config'],path.join(appRoot,'launcher-data/server.env'));}
if(options['--fixture']){
 const fixture=path.resolve(options['--fixture']);assert.equal(JSON.parse(fs.readFileSync(path.join(fixture,'.sqy-workspace-smoke.json'),'utf8')).plan,'launcher-workspace');
 fs.cpSync(fixture,path.join(appRoot,'launcher-data'),{recursive:true,dereference:false,filter:source=>{assert.ok(!fs.lstatSync(source).isSymbolicLink());return true;}});
}
const resourceEvidence=mode==='resources'?prepareNativeResources(appRoot,out):null;
const errors=[],consoleErrors=[],checks=[],ownedTasks=new Set();let active,gameRecord,ownedLaunch;
let optionsEvidence,fabricEvidence,loaderEvidence,modEvidence;
function helper(args){const r=spawnSync(python,['-X','utf8','packaging/tests/windows-game-window.py',...args],{encoding:'utf8',windowsHide:true,timeout:210000});assert.equal(r.status,0,r.stderr||r.stdout);return JSON.parse(r.stdout);}
async function open(root,profileName){
 const port=await new Promise(resolve=>{const server=net.createServer();server.listen(0,'127.0.0.1',()=>{const port=server.address().port;server.close(()=>resolve(port));});});
 const profile=path.join(out,profileName),exe=path.join(root,path.basename(sourceExe));
 const child=spawn(exe,[],{cwd:root,stdio:'ignore',windowsHide:false,env:{...process.env,WEBVIEW2_USER_DATA_FOLDER:profile,WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS:`--remote-debugging-port=${port} --remote-debugging-address=127.0.0.1`}});
 const exit=new Promise(resolve=>child.once('exit',resolve));let debug;
 for(let i=0;i<120;i++){
  assert.equal(child.exitCode,null,'原生程序提前退出');
  try{debug=await(await fetch(`http://127.0.0.1:${port}/json/version`,{signal:AbortSignal.timeout(500)})).json();break;}catch{await sleep(500);}
 }
 assert.ok(debug,'原生WebView调试通道未就绪');
 const browser=await chromium.connectOverCDP(debug.webSocketDebuggerUrl);
 const page=browser.contexts()[0].pages().find(p=>p.url().includes('tauri.localhost'))??browser.contexts()[0].pages()[0];assert.ok(page);
 const childQuery=spawnSync(pwsh,['-NoProfile','-Command',`[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); Get-CimInstance Win32_Process -Filter 'ParentProcessId=${child.pid}' | Select-Object ProcessId,Name,CommandLine | ConvertTo-Json -Depth 2`],{encoding:'utf8',windowsHide:true});assert.equal(childQuery.status,0,childQuery.stderr);
 const children=JSON.parse(childQuery.stdout),list=Array.isArray(children)?children:[children];
 assert.ok(list.some(p=>p.Name==='msedgewebview2.exe'&&p.CommandLine.replaceAll('\\','/').toLowerCase().includes(profile.replaceAll('\\','/').toLowerCase())),'必须使用隔离WebView数据目录');
 assert.ok(!list.some(p=>/^(cmd|powershell|pwsh)\.exe$/i.test(p.Name)),'不能用命令窗启动面板');
 const ownership=helper(['--pid',String(child.pid),'--action','inspect']);
 page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')consoleErrors.push(m.text());});
 await page.waitForFunction(()=>window.__TAURI_INTERNALS__,null,{timeout:20000});
 assert.equal(await page.evaluate(()=>window.__TAURI_INTERNALS__.invoke('launcher_build_identity')),'SQY_NATIVE_BUILD:'+build.buildId);
 // Tauri 的 invoke 是只读属性；只旁观真实 IPC 响应，不修改传输或返回值。
 const calls=[],sent=[];
 page.on('request',request=>{if(request.url()!=='http://ipc.localhost/launcher_local_request')return;try{const args=request.postDataJSON();sent.push({method:args.method,path:args.path,requestId:args.body?.requestId});}catch{}});
 page.on('response',async response=>{
  if(response.url()!=='http://ipc.localhost/launcher_local_request')return;
  try{const args=response.request().postDataJSON(),result=await response.json();calls.push({method:args.method,path:args.path,status:result.status,error:result.body?.error?.code??null,id:result.body?.task?.id??result.body?.check?.id??result.body?.launch?.id??result.body?.discovery?.id});}catch{}
 });
 return {child,exit,browser,page,root,ownership,children:list,calls,sent};
}
async function rpc(app,route,method='GET',body=null){
 const result=await app.page.evaluate(({route,method,body})=>window.__TAURI_INTERNALS__.invoke('launcher_local_request',{path:route,method,body}),{route,method,body});
 assert.ok(result.status>=200&&result.status<300,JSON.stringify(result.body));return result.body;
}
async function close(app){
 await app.page.evaluate(()=>window.__TAURI_INTERNALS__.invoke('window_close'));
 const code=await Promise.race([app.exit,sleep(20000).then(()=>{throw Error('原生窗口没有正常退出');})]);assert.equal(code,0);
 await app.browser.close().catch(()=>{});checks.push('normal-close');
}
async function screenshot(app,name){
 await app.page.screenshot({path:path.join(out,name+'-dom.png'),fullPage:true});
 const args=['--pid',String(app.child.pid),'--started-at',app.ownership.processStartedAt,'--title','三秋叶启动器'];
 const info=helper([...args,'--action','window-info']);
 try{const capture=helper([...args,'--keep-visible','--capture-screen','--action','capture','--output',path.join(out,name+'-window.png')]);write(name+'-window.json',capture);}
 catch(error){if(!error.message.includes('本次窗口不在前台'))throw error;write(name+'-window.json',{...info,physicalScreenshot:false,reason:'前台归属保护拒绝截图；保留实际原生WebView截图，不计物理截图通过'});}
}
async function ready(app){
 await app.page.locator('.launcher-sidebar').waitFor({timeout:45000});if(await app.page.locator('[data-guide-skip]').count())await app.page.locator('[data-guide-skip]').click();
}
async function preset(app,name){await app.page.locator('[data-quick-preset]').selectOption('preset:'+name);await app.page.locator('.workspace-'+name).waitFor();}
async function directQuitRejected(app){
 const rejected=await app.page.evaluate(async()=>{try{await window.__TAURI_INTERNALS__.invoke('quit_application');return false;}catch(error){return String(error).includes('关闭按钮');}});
 assert.equal(rejected,true);assert.equal(app.child.exitCode,null);
}
function windowInfo(app){return helper(['--pid',String(app.child.pid),'--started-at',app.ownership.processStartedAt,'--title','三秋叶启动器','--action','window-info']);}
function interruptOwnCore(app){
 const script=`$ErrorActionPreference='Stop'; [Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); $sqyRoot=$args[0]; $sqyExe=$args[1]; $sqyParent=[int]$args[2]; $matches=@(Get-CimInstance Win32_Process -Filter "ParentProcessId=$sqyParent" | Where-Object { $_.ExecutablePath -eq $sqyExe -and $_.CommandLine.Contains('cn.dreamingfish.updater.player.launcher.LauncherWebMain') -and $_.CommandLine.Contains($sqyRoot) }); if($matches.Count -ne 1){throw '后台归属无法唯一确认'}; $owned=$matches[0]; $process=Get-Process -Id $owned.ProcessId; if([Math]::Abs(($process.StartTime.ToUniversalTime() - $owned.CreationDate.ToUniversalTime()).TotalMilliseconds) -gt 2){throw '后台创建时间不匹配'}; $evidence=@{pid=$owned.ProcessId;createdAt=$owned.CreationDate;executable=$owned.ExecutablePath;parent=$sqyParent;processStartedAt=$process.StartTime.ToUniversalTime();timeDifferenceMs=($process.StartTime.ToUniversalTime() - $owned.CreationDate.ToUniversalTime()).TotalMilliseconds}; $process | Stop-Process -Force; $evidence | ConvertTo-Json`;
 const scriptFile=path.join(out,'interrupt-own-core.ps1');fs.writeFileSync(scriptFile,script);
 const result=spawnSync(pwsh,['-NoProfile','-File',scriptFile,path.join(app.root,'launcher-data'),path.join(app.root,'runtime/bin/java.exe'),String(app.child.pid)],{encoding:'utf8',windowsHide:true});assert.equal(result.status,0,result.stderr);write('interrupted-core.json',JSON.parse(result.stdout));
}
try {
 active=await open(appRoot,'profile');await ready(active);checks.push('native-ui-and-session');
 const instances=(await rpc(active,'/instances')).instances;
 if(mode==='custom-intro'){
  await importCustomIntro({app:active,screenshot,checks,out,write});await close(active);active=null;
  active=await open(appRoot,'profile');await ready(active);await customIntroReopened({app:active,screenshot,checks});await close(active);active=null;
  active=await open(appRoot,'profile');await ready(active);await restoreCustomIntro({app:active,screenshot,checks});await close(active);active=null;
 }else if(mode==='cloud-account'){
  await checkNativeCloudAccount({app:active,screenshot,checks,fixtureFile:options['--cloud-fixture'],write,helper});await close(active);active=null;
 }else if(mode==='auto-cottage'){
  await checkAutoCottage({app:active,preset,screenshot,helper,write,checks,out});await close(active);active=null;
  active=await open(appRoot,'profile');await ready(active);await active.page.locator('[data-launcher-page="mods"]').click();await active.page.locator('[data-mod-project]').first().waitFor({timeout:60000});assert.equal(active.sent.filter(c=>c.path.startsWith('/mods/search?')).length,1);checks.push('native-reopened-auto-directory');await close(active);active=null;
 }else if(mode==='resources'){
  await checkNativeResources({app:active,rpc,preset,screenshot,helper,write,checks,out,evidence:resourceEvidence});await close(active);active=null;
  active=await open(appRoot,'profile');await ready(active);await checkNativeResourcesReopened({app:active,rpc,screenshot,checks,evidence:resourceEvidence});await close(active);active=null;
 }else if(mode==='version-navigation'){
  await checkNativeVersionNavigation({app:active,rpc,preset,screenshot,helper,write,checks});await close(active);active=null;
  active=await open(appRoot,'profile');await ready(active);assert.equal(await active.page.locator('[data-resource-toggle]').getAttribute('aria-expanded'),'false');await active.page.locator('.theme-forest').waitFor();checks.push('reopen-resource-preference-and-theme');await screenshot(active,'version-navigation-reopened');await close(active);active=null;
 }else if(mode==='content'){
  await checkNativeContent({app:active,rpc,screenshot,helper,write,checks});await close(active);active=null;
 }else if(mode==='ui'){
  await checkNativeUi({app:active,instances,rpc,preset,screenshot,helper,write,checks});await close(active);active=null;
  active=await open(appRoot,'profile');await ready(active);await active.page.locator('.theme-forest').waitFor();assert.equal(await active.page.locator('[data-quick-preset]').inputValue(),'preset:cards');assert.equal(await active.page.locator('[data-startup-intro]').count(),0);checks.push('native-reopen-theme-layout-and-intro-persisted');await screenshot(active,'ui-reopened');await close(active);active=null;
 }else if(mode==='basic'){
  await active.page.getByRole('button',{name:'最大化或还原',exact:true}).click();assert.equal(windowInfo(active).maximized,true);await active.page.getByRole('button',{name:'最大化或还原',exact:true}).click();assert.equal(windowInfo(active).maximized,false);await active.page.getByRole('button',{name:'最小化',exact:true}).click();assert.equal(windowInfo(active).minimized,true);await active.page.evaluate(()=>window.__TAURI_INTERNALS__.invoke('window_toggle_maximize'));await active.page.evaluate(()=>window.__TAURI_INTERNALS__.invoke('window_toggle_maximize'));assert.equal(windowInfo(active).minimized,false);checks.push('native-minimize-maximize-restore');
  for(const name of ['workbench','simple','cards','immersive'])await preset(active,name);checks.push('four-presets');
  await active.page.locator('[data-workspace-settings]').click();await active.page.getByRole('dialog',{name:'工作台外观',exact:true}).waitFor();await active.page.keyboard.press('Escape');checks.push('appearance');
  const bad=await active.page.evaluate(async()=>{try{await window.__TAURI_INTERNALS__.invoke('launcher_local_request',{method:'GET',path:'https://example.test/',body:null});return false;}catch{return true;}});assert.ok(bad);checks.push('arbitrary-target-rejected');
  const duplicate=await open(appRoot,'duplicate-profile');
  await duplicate.page.locator('[data-retry-local]').waitFor({timeout:45000});assert.ok((await duplicate.page.locator('[role="alert"]').innerText()).includes('另一个三秋叶启动器'));await close(duplicate);
  assert.equal((await rpc(active,'/instances')).instances.length,instances.length);checks.push('duplicate-root-rejected');
  const instance=instances.find(i=>i.installation);
  if(instance){
   const task=(await rpc(active,'/installations/verify','POST',{instanceId:instance.id})).task;ownedTasks.add(task.id);
   await active.page.evaluate(()=>window.__TAURI_INTERNALS__.invoke('window_close'));
   await active.page.locator('.launcher-operation-notice').filter({hasText:'文件任务'}).waitFor({timeout:15000});assert.equal(active.child.exitCode,null);
   await directQuitRejected(active);checks.push('direct-quit-cannot-bypass-task');
   let current;for(let i=0;i<240;i++){current=(await rpc(active,'/tasks/'+task.id)).task;if(current.state==='COMPLETED')break;assert.ok(!['FAILED','CANCELLED','INTERRUPTED'].includes(current.state));await sleep(500);}assert.equal(current.state,'COMPLETED');ownedTasks.delete(task.id);checks.push('active-task-blocks-close');
  }
  await preset(active,'cards');await screenshot(active,'workspace');write('first-owned.json',{...active.ownership,children:active.children});write('basic-calls.json',active.calls);await close(active);active=null;
  active=await open(appRoot,'profile');await ready(active);assert.equal(await active.page.locator('[data-quick-preset]').inputValue(),'preset:cards');assert.equal((await rpc(active,'/instances')).instances.length,instances.length);checks.push('reopen-and-persistence');
  const catalog=await rpc(active,'/catalog?offset=0&limit=1');write('catalog-summary.json',{...catalog,versions:undefined});assert.ok(catalog.total>0);checks.push('real-catalog-through-native');
  await active.page.locator('[data-launcher-page="instances"]').click();interruptOwnCore(active);await active.page.locator('[data-refresh-instances]').click();await active.page.getByText('本机核心已退出，请重新连接',{exact:false}).first().waitFor();await screenshot(active,'core-exited');checks.push('abnormal-core-exit-visible');
  await active.page.getByRole('button',{name:'退出本机模式',exact:false}).click();await ready(active);assert.equal((await rpc(active,'/instances')).instances.length,instances.length);checks.push('core-reconnect-after-abnormal-exit');await close(active);active=null;
  for(const [name,excluded,text] of [['missing-runtime',['runtime'],'随包Java'],['missing-core',['player-sidecar.jar'],'player-sidecar.jar']]){
   const broken=copyBundle(name,excluded);active=await open(broken,name+'-profile');await active.page.locator('[data-retry-local]').waitFor({timeout:30000});assert.ok((await active.page.locator('[role="alert"]').innerText()).includes(text));await active.page.locator('[role="alert"]').scrollIntoViewIfNeeded();await screenshot(active,name);await close(active);active=null;checks.push(name+'-visible-error');
  }
 }else if(mode==='packs'){
  await active.page.evaluate(()=>window.__TAURI_INTERNALS__.invoke('window_minimize'));
  await active.page.locator('[data-launcher-page="packs"]').click();
  await active.page.locator('[data-pack-source] option[value="ATLAUNCHER"]').waitFor({state:'attached'});
  await active.page.locator('[data-pack-source]').selectOption('FTB');await active.page.locator('[data-pack-query]').fill('evolution');await active.page.locator('[data-pack-search]:not([disabled])').click();
  let cancelling;for(let i=0;i<100;i++){cancelling=active.sent.find(c=>c.method==='GET'&&c.path.startsWith('/packs/search?'));if(cancelling)break;await sleep(10);}assert.ok(cancelling);
  await active.page.locator('[data-pack-source]').selectOption('MODRINTH');
  for(let i=0;i<100;i++){if(active.calls.some(c=>c.path===cancelling.path&&c.error==='QUERY_CANCELLED'))break;await sleep(100);}
  assert.ok(active.calls.some(c=>c.path===cancelling.path&&c.status===409&&c.error==='QUERY_CANCELLED'),'Java必须确认取消旧查询，不能只丢弃界面结果');assert.ok(active.calls.some(c=>c.path==='/packs/cancel'&&c.status===200));checks.push('native-source-switch-cancels-java-query');
  const results=[];
  for(const [source,query] of [['MODRINTH','Fabulously Optimized'],['FTB','evolution'],['ATLAUNCHER','']]){
   await active.page.locator('[data-pack-source]').selectOption(source);await active.page.locator('[data-pack-query]').fill(query);await active.page.locator('[data-pack-search]:not([disabled])').click();
   const project=active.page.locator('[data-pack-project]').first();await project.waitFor({timeout:38000});
   const title=await project.locator('h2').innerText();await project.click();const details=active.page.locator('[aria-label="整合包版本"]');await details.waitFor({timeout:38000});
   const count=await details.locator('article').count();assert.ok(count>0,'公开版本必须来自真实核心');assert.ok((await details.innerText()).includes('安装待接入'));
   const selected=await active.page.locator('[data-pack-source]').inputValue();
   for(const name of ['workbench','simple','cards','immersive']){await preset(active,name);assert.equal(await active.page.locator('[data-pack-source]').inputValue(),selected);assert.equal(await details.locator('article').count(),count);}
   await screenshot(active,'packs-'+source.toLowerCase());await details.scrollIntoViewIfNeeded();await screenshot(active,'pack-versions-'+source.toLowerCase());results.push({source,query,title,versions:count});checks.push('native-real-'+source.toLowerCase()+'-directory-versions');
   if(source==='MODRINTH'){
    await active.page.locator('[data-pack-query]').fill('');await active.page.locator('[data-pack-search]:not([disabled])').click();await active.page.locator('[data-pack-next]:not([disabled])').waitFor({timeout:38000});await active.page.locator('[data-pack-next]').click();
    await active.page.locator('.launcher-task-pagination').getByText(/^21–40 \/ /).waitFor({timeout:38000});checks.push('native-real-modrinth-next-page');
   }
   if(source==='ATLAUNCHER'){
    await active.page.locator('[data-pack-search]:not([disabled])').click();await active.page.locator('.launcher-local-meta').filter({hasText:'缓存目录'}).waitFor({timeout:38000});checks.push('native-atlauncher-cache-labelled');
   }
  }
  const before=active.calls.filter(c=>c.path.startsWith('/packs/search')).length;
  for(const source of ['CURSEFORGE','TECHNIC','BBSMC']){await active.page.locator('[data-pack-source]').selectOption(source);assert.equal(await active.page.locator('[data-pack-search]').isDisabled(),true);assert.ok((await active.page.locator('.launcher-pack-center').innerText()).includes('尚未接入'));}
  assert.equal(active.calls.filter(c=>c.path.startsWith('/packs/search')).length,before);assert.ok(active.calls.every(c=>c.method==='GET'||c.method==='POST'&&c.path==='/packs/cancel'));checks.push('unsupported-pack-sources-make-no-requests');
  const statuses=await rpc(active,'/mods/sources');assert.equal(statuses.sources.find(item=>item.source==='CURSEFORGE').state,'UNCONFIGURED');checks.push('curseforge-mod-key-unconfigured');
  await active.page.locator('[data-pack-source]').selectOption('MODRINTH');interruptOwnCore(active);await active.page.locator('[data-pack-search]:not([disabled])').click();await active.page.locator('.launcher-pack-center [role="alert"]').filter({hasText:'本机核心已退出'}).waitFor({timeout:15000});await screenshot(active,'packs-core-failure');checks.push('native-real-query-failure-visible');
  write('pack-results.json',results);write('pack-calls.json',active.calls);write('pack-sent.json',active.sent);write('pack-owned.json',{...active.ownership,children:active.children});await close(active);active=null;
 }else{
  if(mode==='loader'){const kind=options['--loader'],version=options['--loader-version'];assert.ok(['QUILT','FORGE','NEOFORGE'].includes(kind)&&/^[A-Za-z0-9_.+-]{1,128}$/.test(version));const label={QUILT:'Quilt',FORGE:'Forge',NEOFORGE:'NeoForge'}[kind];loaderEvidence=await checkLoaderInstallation({app:active,instances,rpc,screenshot,write,checks,ownedTasks,helper,kind,version,label});}
  if(mode==='mods')modEvidence=await checkModInstallation({app:active,instances,rpc,screenshot,write,checks,ownedTasks,helper});
  if(mode==='fabric')fabricEvidence=await checkFabricInstallation({app:active,instances,rpc,screenshot,write,checks,ownedTasks,helper});
  if(mode==='options'){
   write('tools-window-size.json',helper(['--pid',String(active.child.pid),'--started-at',active.ownership.processStartedAt,'--title','三秋叶启动器','--action','resize','--width','1600','--height','1000']));
   await active.page.locator('[data-launcher-page="tools"]').click();await active.page.locator('[data-tool-open="color"]').click();await active.page.locator('[data-gradient-text]').fill('秋🍁<green>');const output=await active.page.locator('[data-gradient-output]').inputValue();assert.equal(JSON.parse(output.slice('/tellraw @s '.length)).map(c=>c.text).join(''),'秋🍁<green>');await active.page.locator('[data-gradient-format]').selectOption('minimessage');assert.ok((await active.page.locator('[data-gradient-output]').inputValue()).includes('\\<'));await screenshot(active,'color-text');checks.push('native-unicode-color-and-safe-formats');
   await active.page.locator('[data-tools-back]').click();await active.page.locator('[data-tool-open="skin"]').click();assert.ok(options['--skin-file']&&path.isAbsolute(options['--skin-file']));await active.page.locator('[data-skin-file]').setInputFiles(options['--skin-file']);await active.page.getByText('已读入本机副本；原文件不会被改写。',{exact:true}).waitFor();
   await active.page.waitForFunction(()=>{const c=document.querySelector('[data-skin-preview]');const gl=c?.getContext('webgl2')??c?.getContext('webgl');return c.height>300&&gl&&!gl.isContextLost();});
   const canvas=active.page.locator('[data-skin-canvas]'),readPixel=()=>canvas.evaluate(c=>Array.from(c.getContext('2d').getImageData(0,0,1,1).data));const originalPixel=await readPixel();await active.page.locator('[data-skin-color]').evaluate(e=>{e.value='#12ab34';e.dispatchEvent(new Event('input',{bubbles:true}));});await canvas.click({position:{x:2,y:2}});assert.deepEqual(await readPixel(),[18,171,52,255]);await active.page.locator('[data-skin-undo]').click();assert.deepEqual(await readPixel(),originalPixel);await active.page.locator('[data-skin-redo]').click();assert.deepEqual(await readPixel(),[18,171,52,255]);checks.push('native-png-import-webgl-paint-undo-redo');
   await active.page.locator('[data-skin-file]').setInputFiles({name:'bad.png',mimeType:'image/png',buffer:Buffer.from('not a PNG')});await active.page.getByRole('alert').filter({hasText:'PNG皮肤'}).waitFor();assert.deepEqual(await readPixel(),[18,171,52,255]);checks.push('bad-png-keeps-current-skin');
   const downloads=path.join(out,'downloads');fs.mkdirSync(downloads);const browserSession=await active.browser.newBrowserCDPSession();await browserSession.send('Browser.setDownloadBehavior',{behavior:'allow',downloadPath:downloads,eventsEnabled:true});const [download]=await Promise.all([active.page.waitForEvent('download'),active.page.locator('[data-skin-export]').click()]);assert.equal(await download.failure(),null);
   // WebView使用显式下载目录；Playwright的saveAs仍尝试默认临时路径，直接核对真实下载文件。
   const downloadedName=download.suggestedFilename();assert.equal(path.basename(downloadedName),downloadedName);const downloadedFile=path.join(downloads,downloadedName);assert.ok(fs.statSync(downloadedFile).isFile());assert.ok(fs.statSync(downloadedFile).size>33);fs.copyFileSync(downloadedFile,path.join(out,'edited-skin.png'));write('skin-download.json',{suggestedFilename:downloadedName,actualFile:downloadedFile,sha256:hash(fs.readFileSync(downloadedFile))});await active.page.locator('.skin-workspace').scrollIntoViewIfNeeded();await screenshot(active,'skin-editor');checks.push('native-actual-png-export');
   await active.page.locator('[data-launcher-page="defaults"]').click();await active.page.locator('[data-options-preview]').selectOption('1.20.6');await active.page.locator('[data-options-preview-result]').filter({hasText:'18 项均支持'}).waitFor();await active.page.locator('[data-options-enabled]').check();for(const [key,value] of Object.entries({fov:90,mouseSensitivity:100,maxFps:120,renderDistance:8})){await active.page.locator(`[data-option-select="${key}"]`).check();await active.page.locator(`[data-option-value="${key}"]`).fill(String(value));}await active.page.locator('[data-option-select="mainHand"]').check();await active.page.locator('[data-option-value="mainHand"]').selectOption('left');await active.page.locator('[data-save-game-options]').click();await active.page.getByRole('status').filter({hasText:'已保存'}).waitFor();const template=await rpc(active,'/game-options');assert.equal(template.enabled,true);assert.equal(template.values.fov,90);assert.equal(template.values.mainHand,'left');await screenshot(active,'game-defaults');
   const target=instances.find(i=>i.gameVersion==='1.20.6'&&i.installation);assert.ok(target);const targetFile=path.join(appRoot,'launcher-data/instances',target.id,'.minecraft/options.txt');assert.ok(fs.existsSync(targetFile));const before=fs.readFileSync(targetFile),otherId=randomUUID(),otherRoot=path.join(appRoot,'launcher-data/instances',otherId);fs.mkdirSync(path.join(otherRoot,'.minecraft/saves/keep'),{recursive:true});fs.writeFileSync(path.join(otherRoot,'instance.json'),JSON.stringify({schemaVersion:1,id:otherId,loader:'VANILLA',createdAt:new Date().toISOString(),gameVersion:'1.21.1',name:'保留设置的另一个实例',state:'PREPARED'}));const otherFile=path.join(otherRoot,'.minecraft/options.txt'),saveFile=path.join(otherRoot,'.minecraft/saves/keep/level.dat');fs.writeFileSync(otherFile,'version:3955\nfov:0.0\n');fs.writeFileSync(saveFile,Buffer.from('isolated test save sentinel'));optionsEvidence={template,targetFile,beforeSha256:hash(before),otherFile,otherSha256:hash(fs.readFileSync(otherFile)),saveFile,saveSha256:hash(fs.readFileSync(saveFile))};write('options-before.json',optionsEvidence);checks.push('native-default-template-saved');
  }
  // 验收期间收起自有面板，避免其他前台点击改变专用测试会话。
  await active.page.evaluate(()=>window.__TAURI_INTERNALS__.invoke('window_minimize'));
  const instance=instances.find(i=>i.gameVersion==='1.20.6'&&i.installation);assert.ok(instance,'需要带1.20.6安装的专用夹具');
  if(mode==='game'&&instance.loader==='FABRIC'){
   const root=path.join(appRoot,'launcher-data/instances',instance.id),protectedFiles=[];
   for(const name of fs.readdirSync(path.join(root,'installations'))){const file=path.join(root,'installations',name,'installation.json');if(fs.existsSync(file)&&JSON.parse(fs.readFileSync(file,'utf8')).schemaVersion===1)protectedFiles.push({file,sha256:hash(fs.readFileSync(file))});}
   const save=path.join(root,'.minecraft/saves/fabric-check/level.dat');assert.ok(fs.existsSync(save));protectedFiles.push({file:save,sha256:hash(fs.readFileSync(save))});
   for(const other of instances.filter(i=>i.id!==instance.id)){const file=path.join(appRoot,'launcher-data/instances',other.id,'.minecraft/options.txt');if(fs.existsSync(file))protectedFiles.push({file,sha256:hash(fs.readFileSync(file))});}
   fabricEvidence={root,protectedFiles,instanceId:instance.id};
  }
  if(mode==='game'&&['QUILT','FORGE','NEOFORGE'].includes(instance.loader)){
   const root=path.join(appRoot,'launcher-data/instances',instance.id),protectedFiles=[];
   for(const name of fs.readdirSync(path.join(root,'installations'))){const file=path.join(root,'installations',name,'installation.json');if(fs.existsSync(file)&&JSON.parse(fs.readFileSync(file,'utf8')).schemaVersion===1)protectedFiles.push({file,sha256:hash(fs.readFileSync(file))});}
   const save=path.join(root,'.minecraft/saves/loader-check/level.dat');assert.ok(fs.existsSync(save));protectedFiles.push({file:save,sha256:hash(fs.readFileSync(save))});
   for(const other of instances.filter(i=>i.id!==instance.id)){const file=path.join(appRoot,'launcher-data/instances',other.id,'.minecraft/options.txt');if(fs.existsSync(file))protectedFiles.push({file,sha256:hash(fs.readFileSync(file))});}
   loaderEvidence={kind:instance.loader,version:instance.installation.loaderVersion,root,protectedFiles,instanceId:instance.id};
  }
  await active.page.locator('[data-launcher-page="accounts"]').click();await active.page.locator('[data-offline-name]').fill('SQY_Native');await active.page.locator('.launcher-account-form button').click();await active.page.locator('[data-selected-identity]').filter({hasText:'当前选择 · SQY_Native'}).waitFor();
  await active.page.locator('[data-launcher-page="settings"]').click();await active.page.locator('[data-discover-java]').click();
  let discoveryId;for(let i=0;i<100;i++){discoveryId=active.calls.find(c=>c.method==='POST'&&c.path==='/runtimes/discover')?.id;if(discoveryId)break;await sleep(100);}assert.ok(discoveryId);
  let listing;for(let i=0;i<120;i++){listing=(await rpc(active,'/runtimes/discover/'+discoveryId)).discovery;if(listing.state==='COMPLETED')break;assert.equal(listing.state,'RUNNING');await sleep(500);}assert.equal(listing.state,'COMPLETED');
  const runtime=listing.runtimes.find(r=>r.majorVersion===21&&r.bits===64);assert.ok(runtime,'随包Java21必须被实际检测到');
  await active.page.getByRole('combobox',{name:/^游戏实例/}).selectOption(instance.id);await active.page.locator('[data-java-selection]').selectOption(runtime.id);await active.page.locator('[data-maximum-memory]').fill(mode==='loader'||mode==='mods'||['QUILT','FORGE','NEOFORGE'].includes(instance.loader)?'2048':'768');await active.page.locator('[data-save-launch-settings]:not([disabled])').click();await active.page.getByText('本实例启动设置已保存；下次启动请重新检查',{exact:true}).waitFor();
  await active.page.locator('[data-launcher-page="instances"]').click();await active.page.locator(`[data-instance-id="${instance.id}"] [data-open-launch]`).click();await active.page.locator('[data-check-launch]').click();
  let checkId;for(let i=0;i<100;i++){checkId=active.calls.find(c=>c.method==='POST'&&c.path==='/launch-checks')?.id;if(checkId)break;await sleep(100);}assert.ok(checkId);
  let check;for(let i=0;i<360;i++){check=(await rpc(active,'/launch-checks/'+checkId)).check;write('latest-check.json',check);if(check.state==='READY')break;assert.equal(check.state,'CHECKING',JSON.stringify(check));await sleep(500);}assert.equal(check.state,'READY');
  await active.page.locator('[data-start-launch]:not([disabled])').waitFor();await active.page.locator('[data-start-launch]').click();
  for(let i=0;i<100;i++){ownedLaunch=active.calls.find(c=>c.method==='POST'&&c.path==='/launches')?.id;if(ownedLaunch)break;await sleep(100);}assert.ok(ownedLaunch);
  let launch;for(let i=0;i<960;i++){launch=(await rpc(active,'/launches?'+new URLSearchParams({instanceId:instance.id}))).launches.find(l=>l.id===ownedLaunch);write('latest-launch.json',launch);if(launch?.state==='RUNNING')break;assert.ok(!['FAILED','CANCELLED','EXITED'].includes(launch?.state),JSON.stringify(launch));await sleep(500);}assert.equal(launch.state,'RUNNING');
  if(optionsEvidence){const backup=path.join(appRoot,'launcher-data/instances',instance.id,'options-backups',launch.id+'-options.txt');assert.equal(hash(fs.readFileSync(backup)),optionsEvidence.beforeSha256);const text=fs.readFileSync(optionsEvidence.targetFile,'utf8');for(const line of ['fov:0.5','mouseSensitivity:0.5','maxFps:120','renderDistance:8','mainHand:"left"'])assert.ok(text.split(/\r?\n/).includes(line),line);assert.equal(hash(fs.readFileSync(optionsEvidence.otherFile)),optionsEvidence.otherSha256);assert.equal(hash(fs.readFileSync(optionsEvidence.saveFile)),optionsEvidence.saveSha256);write('options-applied.json',{backup,backupMatchesOriginal:true,otherUnchanged:true,saveUnchanged:true});checks.push('real-game-start-sync-backup-and-isolation');}
  for(const p of ['workbench','simple','cards','immersive'])await preset(active,p);
  await active.page.evaluate(()=>window.__TAURI_INTERNALS__.invoke('window_close'));await active.page.locator('.launcher-operation-notice').filter({hasText:'游戏仍在运行'}).waitFor({timeout:15000});assert.equal(active.child.exitCode,null);checks.push('running-game-blocks-close');
  await directQuitRejected(active);checks.push('direct-quit-cannot-bypass-game');
  gameRecord=JSON.parse(fs.readFileSync(path.join(appRoot,'launcher-data/instances',instance.id,'launches',launch.id,'launch.json'),'utf8'));
  const gameExecutable=helper(['--pid',String(gameRecord.pid),'--action','inspect']);assert.equal(path.resolve(gameExecutable.executable).toLowerCase(),path.join(appRoot,'runtime/bin/java.exe').toLowerCase());write('game-executable.json',gameExecutable);
  const capture=helper(['--pid',String(gameRecord.pid),'--started-at',gameRecord.processStartedAt,'--action','capture','--output',path.join(out,'game-window.png')]);write('pending-game.json',{launchId:launch.id,record:gameRecord,capture});
  console.log(JSON.stringify({status:'WAITING_MENU_CONFIRMATION',launchId:launch.id,pid:gameRecord.pid,screenshot:path.join(out,'game-window.png')}));
  const confirmation=path.join(out,'menu-confirmation.json');const deadline=Date.now()+15*60000;while(!fs.existsSync(confirmation)&&Date.now()<deadline)await sleep(500);
  const confirmed=JSON.parse(fs.readFileSync(confirmation,'utf8'));assert.equal(confirmed.launchId,launch.id);assert.equal(confirmed.menuConfirmed,true);assert.equal(hash(fs.readFileSync(confirmed.screenshot)),confirmed.sha256);
  // 目视确认期间玩家可以自行正常退出；仍以同一launch的后端退出码判定，非零不能放行。
  const beforeClose=(await rpc(active,'/launches?'+new URLSearchParams({instanceId:instance.id}))).launches.find(l=>l.id===ownedLaunch);
  const closed=beforeClose?.state==='EXITED'?{alreadyExited:true,exitCode:beforeClose.exitCode}:helper(['--pid',String(gameRecord.pid),'--started-at',gameRecord.processStartedAt,'--action','close']);assert.equal(closed.exitCode,0);
  for(let i=0;i<120;i++){launch=(await rpc(active,'/launches?'+new URLSearchParams({instanceId:instance.id}))).launches.find(l=>l.id===ownedLaunch);if(launch.state==='EXITED')break;await sleep(500);}assert.equal(launch.state,'EXITED');assert.equal(launch.exitCode,0);ownedLaunch=null;checks.push('bundled-java-real-menu-exit-zero');
  if(optionsEvidence){const text=fs.readFileSync(optionsEvidence.targetFile,'utf8');for(const line of ['fov:0.5','mouseSensitivity:0.5','maxFps:120','renderDistance:8','mainHand:"left"'])assert.ok(text.split(/\r?\n/).includes(line),line);assert.equal(hash(fs.readFileSync(optionsEvidence.otherFile)),optionsEvidence.otherSha256);assert.equal(hash(fs.readFileSync(optionsEvidence.saveFile)),optionsEvidence.saveSha256);write('options-after.json',{gameRetainedSelectedValues:true,otherUnchanged:true,saveUnchanged:true,optionsSha256:hash(fs.readFileSync(optionsEvidence.targetFile))});checks.push('minecraft-retains-settings-after-normal-exit');}
  if(modEvidence)await confirmModGame(modEvidence,{app:active,rpc,write,checks,screenshot,ownedTasks});
  if(fabricEvidence)confirmFabricGame(fabricEvidence,{write,checks});
  if(loaderEvidence)confirmLoaderGame(loaderEvidence,{write,checks});
  await screenshot(active,'after-game');const calls=active.calls;assert.equal(calls.filter(c=>c.method==='POST'&&c.path==='/launches').length,1);write('game-calls.json',calls);write('game-result.json',{runtime,gameRecord,closed,confirmed});await close(active);active=null;
 }
 assert.deepEqual(errors,[]);assert.deepEqual(consoleErrors,[]);write('summary.json',{mode,buildId:build.buildId,checks,errors,consoleErrors});console.log(JSON.stringify({status:'ACCEPTED',mode,checks}));
}catch(error){
 if(active?.page)await active.page.screenshot({path:path.join(out,'failure.png'),fullPage:true}).catch(()=>{});
 if(active?.page)write('failure-ui.json',await active.page.locator('body').innerText().catch(()=>''));
 if(active)write('failure-calls.json',active.calls);
 write('failure.json',{message:error.message,stack:error.stack,pid:active?.child.pid,ownedLaunch,gameRecord,checks,errors,consoleErrors});throw error;
}finally{
 if(active){for(const taskId of ownedTasks)await rpc(active,'/tasks/'+taskId+'/cancel','POST',{}).catch(()=>{});if(ownedLaunch)await rpc(active,'/launches/'+ownedLaunch+'/stop','POST',{confirmed:true}).catch(()=>{});try{await close(active);}catch{}await active.browser.close().catch(()=>{});active.child.unref();}
}
