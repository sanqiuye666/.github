import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {spawn,spawnSync} from 'node:child_process';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';

export function validateLaunchEvidence(report){
 assert.equal(report.games?.length,2,'需要两个真实游戏结果');
 for(const key of ['versionId','instanceId','nativesDirectory'])assert.equal(new Set(report.games.map(game=>game[key])).size,2,`${key}必须不同`);
 for(const game of report.games){for(const key of ['windowDetected','menuConfirmed','normalExitConfirmed','sentinelsStable','otherInstanceOptionsStable','sourceStable','cacheStable'])assert.equal(game[key],true,`缺少${key}证据`);assert.equal(game.exitCode,0,'需要真实正常退出码0');}
 return report;
}
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function digest(file){const hash=createHash('sha256');for await(const bytes of fs.createReadStream(file))hash.update(bytes);return hash.digest('hex');}
async function hashes(directory){
 const result={};if(!fs.existsSync(directory))return result;
 async function visit(current){for(const entry of fs.readdirSync(current,{withFileTypes:true})){const file=path.join(current,entry.name);assert.ok(!fs.lstatSync(file).isSymbolicLink(),'测试目录不能含链接');if(entry.isDirectory())await visit(file);else if(entry.isFile())result[path.relative(directory,file)]=await digest(file);}}
 await visit(directory);return result;
}
async function main(){
 const args=process.argv.slice(2);assert.equal(args.length%2,0);const options=Object.fromEntries(Array.from({length:args.length/2},(_,i)=>[args[i*2],args[i*2+1]]));
 const jar=options['--jar'],root=options['--data-root'],output=options['--output-dir'],mode=options['--mode']||'prepare';assert.ok(['prepare','games'].includes(mode));assert.ok([jar,root,output].every(value=>value&&path.isAbsolute(value)),'需要绝对测试路径');
 const marker=path.join(root,'.sqy-m2d-smoke.json');if(fs.existsSync(root))assert.equal(JSON.parse(fs.readFileSync(marker,'utf8')).plan,'launcher-m2d');else{fs.mkdirSync(root,{recursive:true});fs.writeFileSync(marker,JSON.stringify({plan:'launcher-m2d'}));}
 fs.mkdirSync(output,{recursive:true});const write=(name,value)=>fs.writeFileSync(path.join(output,name),JSON.stringify(value,null,2)+'\n');
 const require=createRequire(import.meta.url);const {chromium}=require(options['--playwright']||'playwright');assert.ok(process.env.JAVA_HOME,'需要 JAVA_HOME');const java=path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java');
 let backend,browser,page,url,ownedLaunch;
 const errors=[],consoleErrors=[],badResponses=[],writes=[],failures=[];
 async function boot(){
  backend=spawn(java,['-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-cp',jar,'cn.dreamingfish.updater.player.launcher.LauncherWebMain','--data-root',root],{windowsHide:true,stdio:['ignore','pipe','pipe']});
  url=await new Promise((resolve,reject)=>{let text='',error='';const timer=setTimeout(()=>reject(new Error('本机后端启动超时 '+error)),30000);backend.stderr.on('data',bytes=>{error=(error+bytes).slice(-4000);});backend.stdout.on('data',bytes=>{text+=bytes;if(text.includes('\n')){clearTimeout(timer);try{resolve(JSON.parse(text.split('\n')[0]).url);}catch(failure){reject(failure);}}});backend.once('error',reject);backend.once('exit',code=>{clearTimeout(timer);reject(new Error('本机后端退出 '+code+' '+error));});});
 }
 async function shutdown(){if(backend){const child=backend;backend=null;await new Promise(resolve=>{child.once('exit',resolve);child.kill();});}}
 async function api(route,method='GET',body){return page.evaluate(async({route,method,body})=>{const base='/api/launcher/v1',common={'X-SQY-Client':'launcher'};const session=await(await fetch(base+'/session',{headers:common})).json();const response=await fetch(base+route,{method,headers:{...common,'X-SQY-Session':session.sessionToken,...(method==='POST'?{'Content-Type':'application/json'}:{})},body:method==='POST'?JSON.stringify(body??{}):undefined});const value=await response.json();if(!response.ok)throw new Error(JSON.stringify(value));return value;},{route,method,body});}
 async function navigate(id){await page.locator(`[data-launcher-page="${id}"]`).click();}
 async function enter(){await page.goto(url);await page.locator('[data-enter-local]:not([disabled])').click();}
 async function images(){const broken=await page.locator('img').evaluateAll(async items=>{await Promise.all(items.map(item=>item.decode().catch(()=>{})));return items.filter(item=>!item.naturalWidth).map(item=>item.src);});assert.deepEqual(broken,[]);}
 async function screens(name,selector){for(const [width,height]of [[1600,950],[1280,800],[960,640]]){await page.setViewportSize({width,height});if(selector)await page.locator(selector).scrollIntoViewIfNeeded();await images();await page.screenshot({path:path.join(output,`${name}-${width}.png`),fullPage:true});}await page.setViewportSize({width:1280,height:800});}
 async function waitTask(id){const deadline=Date.now()+35*60000;let tick=0;while(Date.now()<deadline){const {task}=await api('/tasks/'+id);if(task.state==='COMPLETED')return task;if(['FAILED','CANCELLED','INTERRUPTED'].includes(task.state))throw Object.assign(new Error('文件任务未完成'),{task});if(Date.now()>tick){console.log(JSON.stringify({taskId:id,state:task.state,bytes:task.downloadedBytes,files:task.completedFiles}));tick=Date.now()+15000;}await pause(750);}throw new Error('安装超过35分钟上限');}
 async function install(id){
  await navigate('instances');await page.locator('[data-refresh-instances]:not([disabled])').click();const row=page.locator(`[data-instance-id="${id}"]`);await row.locator('[data-plan-install]').click();await page.locator('[data-confirm-install]').waitFor({timeout:180000});
  const [response]=await Promise.all([page.waitForResponse(response=>response.url().endsWith('/installations')&&response.request().method()==='POST'),page.locator('[data-confirm-install]').click()]);assert.equal(response.status(),202);let task=(await response.json()).task;
  for(let attempt=0;attempt<3;attempt++){try{return await waitTask(task.id);}catch(error){if(!error.task||error.task.state!=='FAILED'||error.task.errorCode!=='IO_ERROR'||attempt===2)throw error;failures.push(error.task);write('download-failed-attempts.json',failures);await page.locator('[data-refresh-tasks]').click();const taskRow=page.locator(`[data-task-id="${task.id}"][data-task-state="FAILED"]`);const [retry]=await Promise.all([page.waitForResponse(response=>response.url().endsWith('/tasks/'+task.id+'/retry')),taskRow.locator('[data-retry-task]').click()]);assert.equal(retry.status(),200);task=(await retry.json()).task;}}
 }
 async function selectInstance(id){await navigate('instances');await page.locator('[data-refresh-instances]:not([disabled])').click();await page.locator(`[data-instance-id="${id}"] [data-open-launch]`).click();await page.locator('[data-check-launch]').waitFor();}
 async function identity(name){await navigate('accounts');await page.locator('[data-offline-name]').fill(name);const [response]=await Promise.all([page.waitForResponse(response=>response.url().endsWith('/identities')&&response.request().method()==='POST'),page.locator('.launcher-account-form button').click()]);assert.equal(response.status(),201);const list=await response.json();const item=list.identities.find(value=>value.name===name);assert.ok(item);if(list.selectedId!==item.id)await page.locator(`[data-select-identity="${item.id}"]`).click();return item;}
 async function detect(){await navigate('settings');const [response]=await Promise.all([page.waitForResponse(response=>response.url().endsWith('/runtimes/discover')),page.locator('[data-discover-java]').click()]);assert.equal(response.status(),202);let value=(await response.json()).discovery;while(value.state==='RUNNING'){await pause(500);value=(await api('/runtimes/discover/'+value.id)).discovery;}assert.equal(value.state,'COMPLETED',value.message);const java=value.runtimes.find(value=>value.majorVersion===21&&value.bits===64);assert.ok(java,'本机没有Java21/64位');return java;}
 async function configure(id,java){await navigate('settings');await page.getByLabel('游戏实例').selectOption(id);await page.locator('[data-java-selection] option[value="'+java.id+'"]').waitFor({state:'attached'});await page.locator('[data-java-selection]').selectOption(java.id);await page.locator('[data-maximum-memory]').fill('2048');await page.locator('[data-save-launch-settings]:not([disabled])').waitFor();const [response]=await Promise.all([page.waitForResponse(response=>response.url().endsWith('/instances/'+id+'/launch-settings')&&response.request().method()==='POST'),page.locator('[data-save-launch-settings]').click()]);assert.equal(response.status(),200);}
 async function waitLaunch(instanceId,state){const deadline=Date.now()+8*60000;while(Date.now()<deadline){const rows=(await api('/launches?'+new URLSearchParams({instanceId}))).launches;const run=rows.at(-1);if(run?.state===state)return run;if(run&&['FAILED','CANCELLED'].includes(run.state))throw new Error(JSON.stringify(run));await pause(500);}throw new Error('运行状态超时 '+state);}
 async function nativeWindow(record,action,image){const result=spawnSync(options['--python']||'python',['-X','utf8',path.join(import.meta.dirname,'tests/windows-game-window.py'),'--pid',String(record.pid),'--started-at',record.processStartedAt,'--action',action,...(image?['--output',image]:[])],{windowsHide:true,encoding:'utf8',timeout:210000,maxBuffer:1024*1024});assert.equal(result.status,0,result.stderr||result.stdout);return JSON.parse(result.stdout);}
 try{
  await boot();browser=await chromium.launch({headless:true,executablePath:options['--browser']||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});page=await browser.newPage({viewport:{width:1280,height:800}});
  page.on('pageerror',error=>errors.push(error.message));page.on('console',message=>{if(message.type()==='error')consoleErrors.push(message.text());});page.on('response',response=>{if(response.status()>=400)badResponses.push({url:response.url(),status:response.status()});});page.on('request',request=>{if(request.method()==='POST')writes.push({url:request.url(),body:request.postDataJSON()});});
  await enter();const actor=await identity('SQY_TestAlice');const java=await detect();const instances=[];
  for(const versionId of ['1.20.6','1.21.1']){
   let existing=(await api('/instances')).instances.find(value=>value.name==='M2D 验收 '+versionId);
   if(!existing){await navigate('versions');await page.locator('[data-version-query]').fill(versionId);await page.locator('[data-version-search]').evaluate(form=>form.requestSubmit());await page.locator(`[data-version-id="${versionId}"]`).click();await page.locator('[data-instance-name]').fill('M2D 验收 '+versionId);const [response]=await Promise.all([page.waitForResponse(response=>response.url().endsWith('/instances')&&response.request().method()==='POST'),page.locator('[data-create-instance] button').click()]);assert.equal(response.status(),201);existing=(await response.json()).instance;}
   if(!existing.installation){const task=await install(existing.id);write('installation-'+versionId+'.json',{versionId,instanceId:existing.id,task});existing=(await api('/instances')).instances.find(value=>value.id===existing.id);}
   assert.equal(existing.installation.requiredJavaMajor,21);instances.push(existing);await configure(existing.id,java);
  }
  if(mode==='prepare'){
   await navigate('home');await screens('home');await navigate('accounts');await screens('accounts');await navigate('settings');await page.locator('[data-save-launch-settings]:not([disabled])').waitFor();await screens('settings','[data-save-launch-settings]');await selectInstance(instances[0].id);await screens('launch-controls','.launcher-launch-center');
   const before=writes.length;await page.reload();await page.locator('[data-enter-local]:not([disabled])').click();await navigate('accounts');await page.getByText('SQY_TestAlice',{exact:true}).first().waitFor();assert.equal(writes.length,before,'浏览器重开不应自动发写请求');
   assert.deepEqual(errors,[]);assert.deepEqual(consoleErrors,[]);assert.deepEqual(badResponses,[]);write('prepare-summary.json',{mode,java,instances,actor,browserReopened:true,errors,consoleErrors,badResponses,writes,realGameStarted:false});return;
  }
  const immutableBefore=await hashes(path.join(root,'downloads','objects'));const sourceBefore=await Promise.all(instances.map(value=>hashes(path.join(root,'instances',value.id,'installations'))));
  const sentinelFiles=[];for(const item of instances){const game=path.join(root,'instances',item.id,'.minecraft');for(const relative of ['config/.sqy-isolation-sentinel','saves/SQY-TestWorld/marker.txt']){const file=path.join(game,relative);fs.mkdirSync(path.dirname(file),{recursive:true});if(!fs.existsSync(file))fs.writeFileSync(file,'M2D 隔离哨兵 '+item.id);sentinelFiles.push(file);}const optionsFile=path.join(game,'options.txt');if(!fs.existsSync(optionsFile))fs.writeFileSync(optionsFile,'fullscreen:false\nguiScale:2\nlang:en_us\n');}
  const sentinelsBefore=await Promise.all(sentinelFiles.map(digest));const games=[];
  for(const item of instances){
   const other=instances.find(value=>value.id!==item.id);const otherOptions=path.join(root,'instances',other.id,'.minecraft/options.txt'),otherHash=await digest(otherOptions);
   await selectInstance(item.id);const [checked]=await Promise.all([page.waitForResponse(response=>response.url().endsWith('/launch-checks')&&response.request().method()==='POST'),page.locator('[data-check-launch]').click()]);assert.equal(checked.status(),202);await page.locator('[data-start-launch]:not([disabled])').waitFor({timeout:180000});
   const [started]=await Promise.all([page.waitForResponse(response=>response.url().endsWith('/launches')&&response.request().method()==='POST'),page.locator('[data-start-launch]').click()]);assert.equal(started.status(),202);ownedLaunch=(await started.json()).launch.id;await waitLaunch(item.id,'RUNNING');
   const launchDirectory=path.join(root,'instances',item.id,'launches',ownedLaunch);const record=JSON.parse(fs.readFileSync(path.join(launchDirectory,'launch.json'),'utf8'));assert.ok(record.pid&&record.processStartedAt);
   const window=await nativeWindow(record,'capture',path.join(output,'game-'+item.gameVersion+'-initial.png'));
   write('pending-game.json',{versionId:item.gameVersion,launchId:ownedLaunch,record,window,confirmationFile:path.join(output,'menu-'+ownedLaunch+'.json')});console.log(JSON.stringify({status:'WAITING_MENU_VERIFICATION',versionId:item.gameVersion,launchId:ownedLaunch,screenshot:window.screenshot}));
   const confirmationFile=path.join(output,'menu-'+ownedLaunch+'.json'),deadline=Date.now()+15*60000;while(!fs.existsSync(confirmationFile)&&Date.now()<deadline)await pause(1000);assert.ok(fs.existsSync(confirmationFile),'没有实际菜单复核，不计游戏通过');
   const confirmed=JSON.parse(fs.readFileSync(confirmationFile,'utf8'));assert.equal(confirmed.launchId,ownedLaunch);assert.equal(confirmed.menuConfirmed,true);assert.ok(path.resolve(confirmed.screenshot).startsWith(path.resolve(output)+path.sep));assert.equal(await digest(confirmed.screenshot),confirmed.sha256);
   if(games.length===0)await identity('SQY_TestBob');
   const closed=await nativeWindow(record,'close');const done=await waitLaunch(item.id,'EXITED');assert.equal(done.exitCode,0);ownedLaunch=null;
   const sentinelAfter=await Promise.all(sentinelFiles.map(digest));assert.deepEqual(sentinelAfter,sentinelsBefore);assert.equal(await digest(otherOptions),otherHash);assert.deepEqual(await hashes(path.join(root,'downloads','objects')),immutableBefore);
   for(let i=0;i<instances.length;i++)assert.deepEqual(await hashes(path.join(root,'instances',instances[i].id,'installations')),sourceBefore[i]);
   await selectInstance(item.id);await page.locator('[data-view-launch-logs]').last().click();await page.locator('.launcher-game-logs pre').waitFor();await screens('logs-'+item.gameVersion,'.launcher-game-logs');
   games.push({versionId:item.gameVersion,instanceId:item.id,nativesDirectory:path.join(launchDirectory,'natives'),launchId:done.id,java,windowDetected:window.windowDetected,menuConfirmed:confirmed.menuConfirmed,screenshot:confirmed.screenshot,normalExitConfirmed:closed.normalExitConfirmed,exitCode:done.exitCode,sentinelsStable:true,otherInstanceOptionsStable:true,sourceStable:true,cacheStable:true});write('games-intermediate.json',{games});
  }
  const otherBefore=await hashes(path.join(root,'instances',instances[1].id,'.minecraft'));const reinstall=await install(instances[0].id);assert.deepEqual(await hashes(path.join(root,'instances',instances[1].id,'.minecraft')),otherBefore);assert.deepEqual(await Promise.all(sentinelFiles.map(digest)),sentinelsBefore);assert.deepEqual(await hashes(path.join(root,'downloads','objects')),immutableBefore);
  for(let i=0;i<instances.length;i++){const now=await hashes(path.join(root,'instances',instances[i].id,'installations'));for(const [name,hash]of Object.entries(sourceBefore[i]))assert.equal(now[name],hash,'旧固定安装被改动');}
  await shutdown();await boot();await enter();assert.deepEqual((await api('/identities')).identities,[]);for(const item of instances)assert.ok((await api('/launches?'+new URLSearchParams({instanceId:item.id}))).launches.some(value=>value.state==='EXITED'&&value.exitCode===0));
  const report={mode,games,reinstallTask:reinstall,backendReopened:true,temporaryIdentitiesNotPersisted:true,errors,consoleErrors,badResponses};validateLaunchEvidence(report);assert.deepEqual(errors,[]);assert.deepEqual(consoleErrors,[]);assert.deepEqual(badResponses,[]);write('games-summary.json',report);console.log(JSON.stringify({status:'ACCEPTED',games:games.map(value=>({versionId:value.versionId,exitCode:value.exitCode}))}));
 }catch(error){write('failure.json',{message:error.message,task:error.task,errors,consoleErrors,badResponses,ownedLaunch});throw error;}
 finally{if(ownedLaunch&&page)try{await api('/launches/'+ownedLaunch+'/stop','POST',{confirmed:true});}catch{}await browser?.close();await shutdown();}
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(error=>{console.error(error);process.exitCode=1;});
