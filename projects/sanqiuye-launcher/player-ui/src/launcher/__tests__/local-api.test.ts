import {afterEach,describe,expect,it,vi} from 'vitest';
import {discoverLocalSession,LocalLauncherClient} from '../local-api';
const context={native:false,embedded:false,pathname:'/',search:''};
const token='a'.repeat(64);
afterEach(()=>vi.unstubAllGlobals());
const response=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
describe('local launcher boundary',()=>{
  it('取消安装预览把查询标识传递给后台',async()=>{
    const request=vi.fn((url:string,init?:RequestInit)=>url.endsWith('/installations/cancel-query')?Promise.resolve(response({cancelRequested:true})):new Promise<Response>((_resolve,reject)=>init?.signal?.addEventListener('abort',()=>reject(new DOMException('操作已取消','AbortError')),{once:true})));vi.stubGlobal('fetch',request);
    const client=new LocalLauncherClient(token),controller=new AbortController();const pending=client.planFabricInstallation('instance','0.16.14',controller.signal);const rejected=expect(pending).rejects.toThrow();await Promise.resolve();controller.abort();await rejected;await Promise.resolve();
    const started=request.mock.calls.find(([url])=>url.includes('/installations/plan'))!,cancel=request.mock.calls.find(([url])=>url.endsWith('/installations/cancel-query'));expect(cancel).toBeDefined();expect(JSON.parse(cancel![1]!.body as string).requestId).toBe(new URL(started[0],location.origin).searchParams.get('requestId'));
  });
  it('取消整合包查询会向本机核心发送相同请求标识，关闭会话仍使用原会话头',async()=>{
    const request=vi.fn((url:string,init?:RequestInit)=>url.endsWith('/packs/cancel')?Promise.resolve(response({cancelled:true})):new Promise<Response>((_resolve,reject)=>init?.signal?.addEventListener('abort',()=>reject(new DOMException('操作已取消','AbortError')),{once:true})));vi.stubGlobal('fetch',request);
    const client=new LocalLauncherClient(token);const pending=client.searchPacks({source:'FTB',q:'test'});const rejected=expect(pending).rejects.toThrow();
    await Promise.resolve();client.close();await rejected;await Promise.resolve();
    const get=request.mock.calls.find(([url])=>url.includes('/packs/search'))!;const cancel=request.mock.calls.find(([url])=>url.endsWith('/packs/cancel'));expect(cancel).toBeDefined();
    const id=new URL(get[0],location.origin).searchParams.get('requestId');expect(id).toMatch(/^[a-f0-9-]{36}$/);expect(JSON.parse(cancel![1]!.body as string).requestId).toBe(id);expect((cancel![1]!.headers as Record<string,string>)['X-SQY-Session']).toBe(token);
  });
  it('previewNeverDiscoversLocalBackend',async()=>{
    const request=vi.fn();vi.stubGlobal('fetch',request);
    for(const input of [{...context,native:true,embedded:true},{...context,embedded:true},{...context,pathname:'/player-preview/'},{...context,search:'?adminPreview=1'}])
      expect(await discoverLocalSession(input)).toBeNull();
    expect(request).not.toHaveBeenCalled();
  });
  it('uses same-origin session headers and never persists the token',async()=>{
    const storage=vi.spyOn(Storage.prototype,'setItem');
    const request=vi.fn().mockResolvedValue(response({schemaVersion:1,mode:'LOCAL',sessionToken:token,capabilities:{catalog:'AVAILABLE'}}));vi.stubGlobal('fetch',request);
    expect((await discoverLocalSession(context))?.sessionToken).toBe(token);
    request.mockResolvedValue(response({instances:[],problems:[]}));
    const client=new LocalLauncherClient(token);await client.instances();client.close();
    expect(request.mock.calls[1][0]).toBe('/api/launcher/v1/instances');
    expect(request.mock.calls[1][1].headers['X-SQY-Session']).toBe(token);
    expect(request.mock.calls[1][0]).not.toContain(token);expect(storage).not.toHaveBeenCalled();storage.mockRestore();
  });
  it('invalidates a stale session and never repeats a write',async()=>{
    const request=vi.fn().mockResolvedValue(response({error:{code:'SESSION_REQUIRED',message:'expired'}},403));vi.stubGlobal('fetch',request);
    const invalid=vi.fn(),client=new LocalLauncherClient(token,invalid);
    await expect(client.createInstance({name:'三秋叶',versionId:'1.0',snapshotId:token})).rejects.toThrow();
    await expect(client.instances()).rejects.toThrow();expect(invalid).toHaveBeenCalledTimes(1);expect(request).toHaveBeenCalledTimes(1);
  });
});
