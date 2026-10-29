import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createHash,randomUUID} from 'node:crypto';

const hash=file=>createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
// 只在主脚本已验证归属的全新夹具副本内工作；真实IPC与官方网络，不替换响应。
export async function checkLoaderInstallation({app,instances,rpc,screenshot,write,checks,ownedTasks,helper,kind,version,label}){
 const instance=instances.find(i=>i.gameVersion==='1.20.6'&&i.installation);assert.ok(instance);
 const root=path.join(app.root,'launcher-data/instances',instance.id),pointer=path.join(root,'active-installation.json');
 const old=JSON.parse(fs.readFileSync(pointer,'utf8')),oldReceipt=path.join(root,'installations',old.installationId,'installation.json');
 assert.equal(JSON.parse(fs.readFileSync(oldReceipt,'utf8')).schemaVersion,1);
 const other=path.join(app.root,'launcher-data/instances',randomUUID());fs.mkdirSync(path.join(other,'.minecraft/saves/keep'),{recursive:true});
 fs.writeFileSync(path.join(other,'instance.json'),JSON.stringify({schemaVersion:1,id:path.basename(other),loader:'VANILLA',createdAt:new Date().toISOString(),gameVersion:'1.21.1',name:'隔离保留实例',state:'PREPARED'}));
 const save=path.join(root,'.minecraft/saves/loader-check/level.dat');fs.mkdirSync(path.dirname(save),{recursive:true});fs.writeFileSync(save,'loader install isolation sentinel');
 const otherFile=path.join(other,'.minecraft/options.txt');fs.writeFileSync(otherFile,'version:3955\nfov:0.0\n');
 const protectedFiles=[path.join(root,'.minecraft/options.txt'),save,oldReceipt,otherFile].map(file=>({file,sha256:hash(file)}));
 await app.page.locator('[data-launcher-page="instances"]').click();await app.page.locator(`[data-instance-id="${instance.id}"] [data-plan-install]`).click();
 await app.page.locator('[data-installation-loader]').selectOption(kind);await app.page.locator(`[data-loader-version] option[value="${version}"]`).waitFor({state:'attached',timeout:38000});
 await app.page.locator('[data-loader-version]').selectOption(version);await screenshot(app,'loader-versions');
 const started=Date.now();await app.page.locator('[data-loader-plan]:not([disabled])').click();await app.page.locator('[data-confirm-install]').waitFor({timeout:39000});
 assert.ok((await app.page.locator('.launcher-plan-details').innerText()).includes(`${label} ${version}`));await screenshot(app,'loader-plan');
 write('loader-plan-time.json',{elapsedMs:Date.now()-started});checks.push('native-official-loader-compatible-versions-and-fixed-plan');
 write('loader-narrow-size.json',helper(['--pid',String(app.child.pid),'--started-at',app.ownership.processStartedAt,'--title','三秋叶启动器','--action','resize','--width','1000','--height','700']));await sleep(200);
 await app.page.locator('[data-confirm-install]').scrollIntoViewIfNeeded();assert.equal(await app.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await screenshot(app,'loader-plan-narrow');checks.push('native-loader-plan-narrow-scroll-and-confirm-visible');
 await app.page.locator('[data-confirm-install]').click();
 let taskId;for(let i=0;i<100;i++){taskId=app.calls.find(c=>c.method==='POST'&&c.path==='/installations')?.id;if(taskId)break;await sleep(100);}assert.ok(taskId);ownedTasks.add(taskId);
 let task;for(let i=0;i<1200;i++){task=(await rpc(app,'/tasks/'+taskId)).task;write('loader-task.json',task);if(task.state==='COMPLETED')break;assert.ok(!['FAILED','CANCELLED','INTERRUPTED'].includes(task.state),JSON.stringify(task));await sleep(500);}assert.equal(task.state,'COMPLETED');assert.equal(task.kind,'LOADER_INSTALL');ownedTasks.delete(taskId);
 const installed=(await rpc(app,'/instances')).instances.find(i=>i.id===instance.id);assert.equal(installed.loader,kind);assert.equal(installed.installation.loaderVersion,version);
 const active=JSON.parse(fs.readFileSync(pointer,'utf8'));assert.notEqual(active.installationId,old.installationId);const receiptFile=path.join(root,'installations',active.installationId,'installation.json');assert.equal(hash(receiptFile),active.receiptSha256);
 const receipt=JSON.parse(fs.readFileSync(receiptFile,'utf8'));assert.equal(receipt.schemaVersion,3);assert.equal(receipt.loader.kind,kind);assert.equal(receipt.loader.version,version);
 for(const entry of receipt.files){const file=path.join(root,'installations',active.installationId,entry.path);assert.equal(fs.statSync(file).size,entry.size);assert.equal(hash(file),entry.sha256,entry.path);}
 for(const entry of protectedFiles)assert.equal(hash(entry.file),entry.sha256,entry.file);
 write('loader-installation.json',{instanceId:instance.id,active,old,receiptFile,fileCount:receipt.files.length,protectedFiles,allReceiptHashesMatched:true});checks.push('native-loader-verified-install-retains-vanilla-settings-save-and-other-instance');
 await app.page.locator('[data-refresh-instances]').click();await app.page.locator(`[data-instance-id="${instance.id}"]`).getByText(`${label} ${version}`,{exact:true}).waitFor();await screenshot(app,'loader-installed');
 return {kind,version,root,protectedFiles:protectedFiles.filter(e=>!e.file.endsWith('options.txt')||e.file.startsWith(other)),instanceId:instance.id};
}

export function confirmLoaderGame(evidence,{write,checks}){
 const log=path.join(evidence.root,'.minecraft/logs/latest.log');const text=fs.readFileSync(log,'utf8');
 const expected=evidence.kind==='QUILT'?`Quilt Loader ${evidence.version}`:evidence.kind==='FORGE'?`version ${evidence.version}`:`${evidence.version}`;
 assert.ok(text.includes(expected),`实际游戏日志缺少核心版本：${expected}`);assert.ok(text.includes('Created:')&&text.includes('gui.png'),'实际渲染日志未完成');
 for(const entry of evidence.protectedFiles)assert.equal(hash(entry.file),entry.sha256,entry.file);
 write('loader-game-log.json',{log,sha256:hash(log),loaderConfirmed:true,protectedFilesUnchanged:true,matchingLines:text.split(/\r?\n/).filter(line=>/Quilt Loader|Forge mod loading|NeoForge mod loading/.test(line)).slice(0,20)});
 checks.push('real-loader-loader-log-and-isolation-after-game-exit');
}
