import test from 'node:test';
import assert from 'node:assert/strict';
import {validateAudit} from '../launch-plan-audit.mjs';
import {validateLaunchEvidence} from '../launcher-launch-smoke.mjs';

test('目录审计不得漏项、重复或隐藏本地缺陷',()=>{
 const report={results:[{versionId:'one',status:'FORMAT_CHECKED'},{versionId:'two',status:'JAVA_MISSING'}]};
 assert.doesNotThrow(()=>validateAudit(report,['one','two']));
 assert.throws(()=>validateAudit(report,['one','two','three']));
 assert.throws(()=>validateAudit({results:[...report.results,report.results[0]]},['one','two']));
 assert.throws(()=>validateAudit({results:[{versionId:'one',status:'LOCAL_DEFECT'}]},['one']));
});
test('真实窗口验收需要两版本菜单、正常退出及所有隔离证据',()=>{
 const sample=(versionId,instanceId)=>({versionId,instanceId,nativesDirectory:instanceId+'/natives',windowDetected:true,menuConfirmed:true,normalExitConfirmed:true,exitCode:0,sentinelsStable:true,otherInstanceOptionsStable:true,sourceStable:true,cacheStable:true});
 const report={games:[sample('1.20.6','one'),sample('1.21.1','two')]};
 assert.doesNotThrow(()=>validateLaunchEvidence(report));
 for(const key of ['windowDetected','menuConfirmed','normalExitConfirmed','sentinelsStable','otherInstanceOptionsStable','sourceStable','cacheStable']){
  const invalid=structuredClone(report);invalid.games[0][key]=false;assert.throws(()=>validateLaunchEvidence(invalid),key);
 }
 const badExit=structuredClone(report);badExit.games[1].exitCode=7;assert.throws(()=>validateLaunchEvidence(badExit));
 assert.throws(()=>validateLaunchEvidence({games:[report.games[0],report.games[0]]}));
 assert.throws(()=>validateLaunchEvidence({games:[report.games[0]]}));
});
