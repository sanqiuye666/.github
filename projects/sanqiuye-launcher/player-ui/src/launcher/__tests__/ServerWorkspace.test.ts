import {afterEach,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import ServerWorkspace from '../server/ServerWorkspace.vue';
import {LocalLauncherClient} from '../local-api';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
async function settle(){for(let i=0;i<16;i++){await Promise.resolve();await nextTick();}}
afterEach(()=>{app?.unmount();root?.remove();vi.unstubAllGlobals();});
it('演练标签、自己的包和管理员包可切换，未登录不会显示真实成功',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(url:string)=>new Response(JSON.stringify({schemaVersion:1,source:'MOCK',requestId:'req-test',attempts:1,durationMs:1,data:url.endsWith('/status')?{enabled:true,source:'MOCK',role:'player',scenario:'success',message:'离线演练'}:null,error:url.endsWith('/status')?null:{code:'AUTH_FAILED',message:'请先演练登录'}}),{headers:{'Content-Type':'application/json'}})));
 root=document.createElement('div');document.body.append(root);app=createApp(ServerWorkspace,{client:new LocalLauncherClient('a'.repeat(64))});app.mount(root);await settle();
 expect(root.textContent).toContain('离线演练');expect(root.textContent).toContain('自己的整合包');expect(root.textContent).toContain('不验证真实密码');
 root.querySelector<HTMLButtonElement>('[data-pack-source="own"]')!.click();await settle();expect(root.textContent).toContain('本地实例');
 expect(root.textContent).not.toContain('游戏正在运行');
});
