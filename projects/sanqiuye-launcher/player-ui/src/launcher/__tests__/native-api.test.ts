import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {discoverLocalSession,LocalLauncherClient} from '../local-api';
const native=vi.hoisted(()=>({invoke:vi.fn()}));
vi.mock('@tauri-apps/api/core',()=>({invoke:native.invoke}));
const context={native:true,embedded:false,pathname:'/',search:''};
const token='a'.repeat(64);
const session={schemaVersion:1,mode:'LOCAL',sessionToken:token,capabilities:{instances:'AVAILABLE'}};
beforeEach(()=>{native.invoke.mockReset();vi.stubGlobal('__TAURI_INTERNALS__',{});});
afterEach(()=>vi.unstubAllGlobals());
describe('原生玩家本机通道',()=>{
 it('原生整合包取消传到Java取消路由，不只抛弃IPC结果',async()=>{
  native.invoke.mockImplementation((_command:string,args:{method:string;path:string})=>args.method==='POST'?Promise.resolve({status:200,body:{cancelled:true}}):new Promise(()=>{}));
  const client=new LocalLauncherClient(token);const controller=new AbortController();const pending=client.searchPacks({source:'FTB'},controller.signal);const rejected=expect(pending).rejects.toThrow();
  await vi.waitFor(()=>expect(native.invoke).toHaveBeenCalled());controller.abort();await rejected;for(let i=0;i<10;i++)await Promise.resolve();
  const get=native.invoke.mock.calls.find(([,args])=>args.method==='GET')![1];const cancel=native.invoke.mock.calls.find(([,args])=>args.path==='/packs/cancel')?.[1];expect(cancel).toBeDefined();expect(cancel.body.requestId).toBe(new URL(get.path,location.origin).searchParams.get('requestId'));client.close();
 });
 it('在内置主页面通过IPC发现真实核心，不向Tauri资源域发HTTP',async()=>{
  const request=vi.fn();vi.stubGlobal('fetch',request);native.invoke.mockResolvedValue(session);
  expect(await discoverLocalSession(context)).toEqual(session);
  expect(native.invoke).toHaveBeenCalledWith('launcher_local_session');expect(request).not.toHaveBeenCalled();
 });
 it('预览和嵌入上下文不能启动原生核心',async()=>{
  for(const value of [{...context,embedded:true},{...context,pathname:'/player-preview/'},{...context,search:'?updaterPreview=1'},{...context,search:'?adminPreview=1'}])expect(await discoverLocalSession(value)).toBeNull();
  expect(native.invoke).not.toHaveBeenCalled();
 });
 it('后台缺失的可读原因保留给界面',async()=>{
  native.invoke.mockRejectedValue('随包Java缺失，请重新解压完整目录');
  await expect(discoverLocalSession(context)).rejects.toThrow('随包Java缺失');
 });
 it('原生请求只传业务相对路径，不让界面选择目的地或认证头',async()=>{
  const request=vi.fn();vi.stubGlobal('fetch',request);native.invoke.mockResolvedValue({status:200,body:{instances:[],problems:[]}});
  const client=new LocalLauncherClient(token);expect(await client.instances()).toEqual({instances:[],problems:[]});
  expect(native.invoke).toHaveBeenCalledWith('launcher_local_request',{method:'GET',path:'/instances',body:null});expect(request).not.toHaveBeenCalled();client.close();
 });
 it('失效写请求不自动重发，取消后不提交原生调用',async()=>{
  native.invoke.mockResolvedValue({status:403,body:{error:{code:'SESSION_REQUIRED',message:'会话失效'}}});
  const invalid=vi.fn(),client=new LocalLauncherClient(token,invalid);
  await expect(client.createInstance({name:'本机测试',versionId:'1.20.6',snapshotId:token})).rejects.toThrow('会话失效');
  await expect(client.instances()).rejects.toThrow();expect(native.invoke).toHaveBeenCalledTimes(1);expect(invalid).toHaveBeenCalledTimes(1);
  const other=new LocalLauncherClient(token),cancelled=new AbortController();cancelled.abort();
  await expect(other.instances(cancelled.signal)).rejects.toThrow();expect(native.invoke).toHaveBeenCalledTimes(1);other.close();
 });
});
