import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createHash,randomUUID} from 'node:crypto';

const hash=file=>createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
// 只在主脚本已验证归属的全新夹具副本内工作；真实IPC与官方网络，不替换响应。
export async function checkFabricInstallation({app,instances,rpc,screenshot,write,checks,ownedTasks,helper}){
 const instance=instances.find(i=>i.gameVersion==='1.20.6'&&i.installation);assert.ok(instance);
 const root=path.join(app.root,'launcher-data/instances',instance.id),pointer=path.join(root,'active-installation.json');
 const old=JSON.parse(fs.readFileSync(pointer,'utf8')),oldReceipt=path.join(root,'installations',old.installationId,'installation.json');
 assert.equal(JSON.parse(fs.readFileSync(oldReceipt,'utf8')).schemaVersion,1);
 const other=path.join(app.root,'launcher-data/instances',randomUUID());fs.mkdirSync(path.join(other,'.minecraft/saves/keep'),{recursive:true});
 fs.writeFileSync(path.join(other,'instance.json'),JSON.stringify({schemaVersion:1,id:path.basename(other),loader:'VANILLA',createdAt:new Date().toISOString(),gameVersion:'1.21.1',name:'隔离保留实例',state:'PREPARED'}));
 const save=path.join(root,'.minecraft/saves/fabric-check/level.dat');fs.mkdirSync(path.dirname(save),{recursive:true});fs.writeFileSync(save,'fabric install isolation sentinel');
 const otherFile=path.join(other,'.minecraft/options.txt');fs.writeFileSync(otherFile,'version:3955\nfov:0.0\n');
 const protectedFiles=[path.join(root,'.minecraft/options.txt'),save,oldReceipt,otherFile].map(file=>({file,sha256:hash(file)}));
 await app.page.locator('[data-launcher-page="instances"]').click();await app.page.locator(`[data-instance-id="${instance.id}"] [data-plan-install]`).click();
 await app.page.locator('[data-installation-loader]').selectOption('FABRIC');await app.page.locator('[data-fabric-version] option[value="0.16.14"]').waitFor({state:'attached',timeout:38000});
 await app.page.locator('[data-fabric-version]').selectOption('0.16.14');await screenshot(app,'fabric-versions');
 const started=Date.now();await app.page.locator('[data-fabric-plan]:not([disabled])').click();await app.page.locator('[data-confirm-install]').waitFor({timeout:39000});
 assert.ok((await app.page.locator('.launcher-plan-details').innerText()).includes('Fabric 0.16.14'));await screenshot(app,'fabric-plan');
 write('fabric-plan-time.json',{elapsedMs:Date.now()-started});checks.push('native-official-fabric-compatible-versions-and-fixed-plan');
 write('fabric-narrow-size.json',helper(['--pid',String(app.child.pid),'--started-at',app.ownership.processStartedAt,'--title','三秋叶启动器','--action','resize','--width','1000','--height','700']));await sleep(200);
 await app.page.locator('[data-confirm-install]').scrollIntoViewIfNeeded();assert.equal(await app.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await screenshot(app,'fabric-plan-narrow');checks.push('native-fabric-plan-narrow-scroll-and-confirm-visible');
 await app.page.locator('[data-confirm-install]').click();
 let taskId;for(let i=0;i<100;i++){taskId=app.calls.find(c=>c.method==='POST'&&c.path==='/installations')?.id;if(taskId)break;await sleep(100);}assert.ok(taskId);ownedTasks.add(taskId);
 let task;for(let i=0;i<1200;i++){task=(await rpc(app,'/tasks/'+taskId)).task;write('fabric-task.json',task);if(task.state==='COMPLETED')break;assert.ok(!['FAILED','CANCELLED','INTERRUPTED'].includes(task.state),JSON.stringify(task));await sleep(500);}assert.equal(task.state,'COMPLETED');assert.equal(task.kind,'FABRIC_INSTALL');ownedTasks.delete(taskId);
 const installed=(await rpc(app,'/instances')).instances.find(i=>i.id===instance.id);assert.equal(installed.loader,'FABRIC');assert.equal(installed.installation.fabricVersion,'0.16.14');
 const active=JSON.parse(fs.readFileSync(pointer,'utf8'));assert.notEqual(active.installationId,old.installationId);const receiptFile=path.join(root,'installations',active.installationId,'installation.json');assert.equal(hash(receiptFile),active.receiptSha256);
 const receipt=JSON.parse(fs.readFileSync(receiptFile,'utf8'));assert.equal(receipt.schemaVersion,2);assert.equal(receipt.fabricVersion,'0.16.14');
 for(const entry of receipt.files){const file=path.join(root,'installations',active.installationId,entry.path);assert.equal(fs.statSync(file).size,entry.size);assert.equal(hash(file),entry.sha256,entry.path);}
 for(const entry of protectedFiles)assert.equal(hash(entry.file),entry.sha256,entry.file);
 write('fabric-installation.json',{instanceId:instance.id,active,old,receiptFile,fileCount:receipt.files.length,protectedFiles,allReceiptHashesMatched:true});checks.push('native-fabric-verified-install-retains-vanilla-settings-save-and-other-instance');
 await app.page.locator('[data-refresh-instances]').click();await app.page.locator(`[data-instance-id="${instance.id}"]`).getByText('Fabric 0.16.14',{exact:true}).waitFor();await screenshot(app,'fabric-installed');
 return {root,protectedFiles:protectedFiles.filter(e=>!e.file.endsWith('options.txt')||e.file.startsWith(other)),instanceId:instance.id};
}

export function confirmFabricGame(evidence,{write,checks}){
 const log=path.join(evidence.root,'.minecraft/logs/latest.log');const text=fs.readFileSync(log,'utf8');
 assert.match(text,/Loading Minecraft 1\.20\.6 with Fabric Loader 0\.16\.14/);
 for(const entry of evidence.protectedFiles)assert.equal(hash(entry.file),entry.sha256,entry.file);
 write('fabric-game-log.json',{log,sha256:hash(log),loaderConfirmed:true,protectedFilesUnchanged:true,matchingLines:text.split(/\r?\n/).filter(line=>/Fabric Loader|Loading Minecraft|fabricloader|intermediary/.test(line)).slice(0,20)});
 checks.push('real-fabric-loader-log-and-isolation-after-game-exit');
}
