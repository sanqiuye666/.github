import {afterEach,expect,it,vi} from 'vitest';
import {createApp,nextTick,reactive,shallowReactive,h} from 'vue';
import LaunchCenter from '../LaunchCenter.vue';
import {LocalLauncherClient} from '../local-api';
const instance={id:'instance',name:'隔离实例',gameVersion:'1.21.1',loader:'VANILLA',createdAt:'',state:'PREPARED' as const,installation:{installationId:'installed',versionId:'1.21.1',installedAt:'',fileCount:3,requiredJavaMajor:21,platform:{osName:'windows',arch:'amd64',bits:64}}};
const launch={id:'launch',instanceId:'instance',installationId:'installed',state:'RUNNING',createdAt:'',startedAt:'',endedAt:null,exitCode:null,errorCode:null,message:'进程运行'};
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
let replaceInstance:(value:typeof instance)=>void;
const reply=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
async function settle(){for(let i=0;i<12;i++){await Promise.resolve();await nextTick();}}
afterEach(()=>{app?.unmount();root?.remove();vi.useRealTimers();vi.unstubAllGlobals();});
async function mount(stale=false,checking=false,extra:Record<string,unknown>={}){
 let started=false;const fetcher=vi.fn(async(url:string,init?:RequestInit)=>{
  if(url.endsWith('/identities'))return reply({identities:[{id:'alice',name:'Alice',kind:'OFFLINE'}],selectedId:'alice',temporary:true});
  if(url.includes('/logs'))return reply({lines:[url.includes('cursor=next')?'第二页日志':'第一页日志'],nextCursor:'next',truncated:false});
  if(url.endsWith('/launch-checks'))return reply({check:{id:'check',instanceId:'instance',state:checking?'CHECKING':'READY',createdAt:'',expiresAt:new Date(Date.now()+300000).toISOString(),installationId:'installed',versionId:'1.21.1',javaMajor:21,errorCode:null,message:'已检查'}});
  if(url.endsWith('/cancel'))return reply({check:{id:'check',state:'CANCELLED',expiresAt:new Date(Date.now()+300000).toISOString(),message:'已取消'}});
  if(url.endsWith('/launches')&&init?.method==='POST'){if(stale)return reply({error:{code:'SETTINGS_CHANGED',message:'设置变化，请重新检查'}},409);started=true;return reply({launch});}
  if(url.endsWith('/stop'))return reply({launch:{...launch,state:'STOPPING'}});
  return reply({launches:started?[launch]:[]});
 });vi.stubGlobal('fetch',fetcher);root=document.createElement('div');document.body.append(root);const props=shallowReactive({client:new LocalLauncherClient('a'.repeat(64)),instance,...extra});replaceInstance=value=>props.instance=value;app=createApp({setup:()=>()=>h(LaunchCenter,props)});app.mount(root);await settle();return fetcher;
}
it('任务刷新返回相同安装的新实例对象时保留启动检查，实际安装改变才失效',async()=>{
 vi.useFakeTimers();await mount(false,false,{serverTarget:{id:'s',revision:'r',name:'朋友的服',address:{host:'one.example.org',port:25565}}});click('[data-check-launch]');await settle();expect(root.querySelector<HTMLButtonElement>('[data-start-launch]')!.disabled).toBe(false);replaceInstance({...instance,installation:{...instance.installation}});await settle();expect(root.querySelector<HTMLButtonElement>('[data-start-launch]')!.disabled).toBe(false);replaceInstance({...instance,installation:{...instance.installation,installationId:'changed'}});await settle();expect(root.querySelector<HTMLButtonElement>('[data-start-launch]')!.disabled).toBe(true);
});
it('直连收藏的修订变化使旧检查失效，迟到的检查响应不能重新启用启动',async()=>{
 vi.useFakeTimers();const server=reactive({id:'s',revision:'r1',name:'朋友的服',address:{host:'one.example.org',port:25565}});
 const fetcher=await mount(false,false,{serverTarget:server});click('[data-check-launch]');await settle();expect(root.querySelector<HTMLButtonElement>('[data-start-launch]')!.disabled).toBe(false);
 server.revision='r2';server.address.host='two.example.org';await settle();expect(root.querySelector<HTMLButtonElement>('[data-start-launch]')!.disabled).toBe(true);
 let resolve!:(r:Response)=>void;fetcher.mockImplementationOnce(()=>new Promise<Response>(r=>resolve=r));click('[data-check-launch]');await settle();server.revision='r3';await settle();resolve(reply({check:{id:'old',state:'READY',expiresAt:new Date(Date.now()+300000).toISOString()}}));await settle();expect(root.querySelector<HTMLButtonElement>('[data-start-launch]')!.disabled).toBe(true);
});
function click(selector:string){root.querySelector<HTMLButtonElement>(selector)!.click();}
it('starts once only after explicit checks, confirms termination and pages logs without accumulating them',async()=>{
 vi.useFakeTimers();const fetcher=await mount();expect(fetcher.mock.calls.every(([,init])=>init?.method==='GET')).toBe(true);
 click('[data-check-launch]');click('[data-check-launch]');await settle();expect(fetcher.mock.calls.filter(([url])=>url.endsWith('/launch-checks'))).toHaveLength(1);
 click('[data-start-launch]');click('[data-start-launch]');await settle();expect(fetcher.mock.calls.filter(([url,init])=>url.endsWith('/launches')&&init?.method==='POST')).toHaveLength(1);expect(root.textContent).toContain('不代表已进入菜单');
 click('[data-stop-launch]');await settle();expect(fetcher.mock.calls.some(([url])=>url.endsWith('/stop'))).toBe(false);click('[data-confirm-stop-launch]');await settle();expect(fetcher.mock.calls.filter(([url])=>url.endsWith('/stop'))).toHaveLength(1);
 click('[data-view-launch-logs]');await settle();expect(root.textContent).toContain('第一页日志');click('[data-next-launch-logs]');await settle();expect(root.textContent).toContain('第二页日志');expect(root.textContent).not.toContain('第一页日志');
 app?.unmount();app=undefined;const count=fetcher.mock.calls.length;await vi.advanceTimersByTimeAsync(5000);expect(fetcher).toHaveBeenCalledTimes(count);
});
it('never retries a stale start and makes the old check unusable',async()=>{
 vi.useFakeTimers();const fetcher=await mount(true);click('[data-check-launch]');await settle();click('[data-start-launch]');await settle();expect(root.textContent).toContain('设置变化');expect(root.querySelector<HTMLButtonElement>('[data-start-launch]')!.disabled).toBe(true);await vi.advanceTimersByTimeAsync(5000);expect(fetcher.mock.calls.filter(([url,init])=>url.endsWith('/launches')&&init?.method==='POST')).toHaveLength(1);
});
it('cancels a checking plan without starting a process',async()=>{
 vi.useFakeTimers();const fetcher=await mount(false,true);click('[data-check-launch]');await settle();expect(root.querySelector('[data-maid-state="waiting"]')).not.toBeNull();click('[data-cancel-launch-check]');await settle();expect(root.textContent).toContain('已取消');expect(root.querySelector<HTMLButtonElement>('[data-start-launch]')!.disabled).toBe(true);expect(fetcher.mock.calls.some(([url,init])=>url.endsWith('/launches')&&init?.method==='POST')).toBe(false);
});
it('直连检查轮询完成后启用启动，普通渲染不会清除检查',async()=>{
 vi.useFakeTimers();const server={id:'s',revision:'r1',name:'朋友的服',address:{host:'one.example.org',port:25565}};const fetcher=await mount(false,true,{serverTarget:server});click('[data-check-launch]');await settle();fetcher.mockImplementation(async(url:string)=>url.includes('/launch-checks/')?reply({check:{id:'check',state:'READY',expiresAt:new Date(Date.now()+300000).toISOString(),message:'已检查'}}):reply({launches:[]}));await vi.advanceTimersByTimeAsync(800);await settle();expect(root.querySelector<HTMLButtonElement>('[data-start-launch]')!.disabled).toBe(false);
});
it('disables a ready plan and stops polling when the session expires',async()=>{
 vi.useFakeTimers();const fetcher=await mount();click('[data-check-launch]');await settle();expect(root.querySelector<HTMLButtonElement>('[data-start-launch]')!.disabled).toBe(false);
 fetcher.mockImplementation(async()=>reply({error:{code:'SESSION_REQUIRED',message:'会话失效'}},403));
 Array.from(root.querySelectorAll('button')).find(button=>button.textContent?.includes('刷新状态'))!.click();await settle();expect(root.textContent).toContain('会话失效');expect(root.querySelector<HTMLButtonElement>('[data-start-launch]')!.disabled).toBe(true);
 const count=fetcher.mock.calls.length;await vi.advanceTimersByTimeAsync(5000);expect(fetcher).toHaveBeenCalledTimes(count);
});
