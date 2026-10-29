import test from 'node:test';
import assert from 'node:assert/strict';
import {queryDesktopUpdate} from '../src/self-update.ts';

test('未配置更新时不发原生请求，也不将未配置当作最新版',async()=>{
 let calls=0;
 await assert.rejects(()=>queryDesktopUpdate(false,async()=>{calls++;return null;}),/自更新服务尚未配置/);
 assert.equal(calls,0);
});
test('有配置时保留真实更新结果和失败，不自行重试',async()=>{
 let calls=0;const info={version:'next',notes:'说明',installable:false};
 assert.equal(await queryDesktopUpdate(true,async()=>{calls++;return info;}),info);
 await assert.rejects(()=>queryDesktopUpdate(true,async()=>{calls++;throw new Error('真实网络失败');}),/真实网络失败/);
 assert.equal(calls,2);
});
