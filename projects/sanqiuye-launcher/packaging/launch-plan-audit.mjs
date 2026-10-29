import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {spawn,spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

export function validateAudit(report,officialIds){
 assert.ok(Array.isArray(report.results),'审计缺少逐版结果');
 assert.equal(new Set(report.results.map(row=>row.versionId)).size,report.results.length,'审计出现重复版本');
 assert.deepEqual(report.results.map(row=>row.versionId).sort(),[...officialIds].sort(),'审计必须覆盖当前官方快照的每个ID');
 const allowed=new Set(['FORMAT_CHECKED','JAVA_MISSING','UNSUPPORTED_FORMAT','EXTERNAL_FAILURE','LOCAL_DEFECT']);
 for(const row of report.results){assert.ok(allowed.has(row.status),'未知审计状态');assert.notEqual(row.status,'LOCAL_DEFECT','启动器本地缺陷必须失败');}
 return report;
}
async function main(){
 const args=process.argv.slice(2);assert.equal(args.length%2,0);const options=Object.fromEntries(Array.from({length:args.length/2},(_,i)=>[args[2*i],args[2*i+1]]));
 const jar=options['--jar'],root=options['--data-root'],output=options['--output-dir'];assert.ok([jar,root,output].every(value=>value&&path.isAbsolute(value)),'需要绝对 jar/data-root/output-dir');
 const marker=path.join(root,'.sqy-m2d-smoke.json');if(fs.existsSync(root))assert.equal(JSON.parse(fs.readFileSync(marker,'utf8')).plan,'launcher-m2d');else{fs.mkdirSync(root,{recursive:true});fs.writeFileSync(marker,JSON.stringify({plan:'launcher-m2d'}));}
 fs.mkdirSync(output,{recursive:true});const classes=path.join(output,'audit-classes');fs.mkdirSync(classes,{recursive:true});
 const javaHome=process.env.JAVA_HOME;assert.ok(javaHome,'需要 JAVA_HOME 指向已验证 JDK');const suffix=process.platform==='win32'?'.exe':'';
 const helper=path.join(import.meta.dirname,'tests','LaunchPlanAudit.java');const compile=spawnSync(path.join(javaHome,'bin','javac'+suffix),['-encoding','UTF-8','-cp',jar,'-d',classes,helper],{windowsHide:true,encoding:'utf8'});assert.equal(compile.status,0,compile.stderr);
 const reportPath=path.join(output,'launch-format-audit.json');
 const child=spawn(path.join(javaHome,'bin','java'+suffix),['-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-cp',jar+path.delimiter+classes,'LaunchPlanAudit','--data-root',root,'--report',reportPath],{windowsHide:true,stdio:['ignore','inherit','inherit']});
 const code=await new Promise((resolve,reject)=>{child.once('error',reject);child.once('exit',resolve);});assert.equal(code,0,'审计辅助程序失败，原报告仍保留');
 const report=JSON.parse(fs.readFileSync(reportPath,'utf8'));const snapshot=JSON.parse(fs.readFileSync(path.join(root,'catalog','version-manifest.json'),'utf8'));
 validateAudit(report,snapshot.manifest.versions.map(version=>version.id));console.log(JSON.stringify({catalogTotal:report.catalogTotal,counts:report.counts,coverage:report.coverage,gameExecutions:0}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(error=>{console.error(error);process.exitCode=1;});
