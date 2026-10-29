import test from 'node:test';
import assert from 'node:assert/strict';
import {setTimeout as delay} from 'node:timers/promises';
import {waitForNativeExit} from '../native-exit.mjs';

test('退出IPC和进程结束必须共用从发送请求开始的期限',async()=>{
 await assert.rejects(waitForNativeExit(()=>delay(100),delay(115,0),30),/管理端未正常退出/);
});
test('IPC一直不返回时也会进入超时诊断',async()=>{
 let calls=0;
 await assert.rejects(waitForNativeExit(()=>new Promise(()=>{}),Promise.resolve(0),20,()=>calls++),/管理端未正常退出/);
 assert.equal(calls,1);
});
test('正常结束清除计时器，IPC失败也保持真实错误',async()=>{
 let calls=0;
 assert.equal(await waitForNativeExit(async()=>{},Promise.resolve(0),20,()=>calls++),0);
 await delay(30);assert.equal(calls,0);
 await assert.rejects(waitForNativeExit(async()=>{throw Error('来源拒绝');},Promise.resolve(0),20),/来源拒绝/);
});
