import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
vi.mock('../window',()=>({minimizePlayerWindow:vi.fn(),togglePlayerMaximize:vi.fn(),closePlayerWindow:vi.fn()}));
const native=vi.hoisted(()=>({session:vi.fn(),request:vi.fn()}));
vi.mock('../native-api',()=>({nativeSession:native.session,nativeRequest:native.request}));
vi.mock('@tauri-apps/api/event',()=>({listen:vi.fn().mockResolvedValue(()=>{})}));
import LauncherApp from '../LauncherApp.vue';
import {createLauncherSession} from '../model';
let app:ReturnType<typeof createApp>|null=null;let root:HTMLDivElement;
afterEach(()=>{app?.unmount();root?.remove();vi.unstubAllGlobals();});
beforeEach(()=>{localStorage.clear();vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new Error('offline fixture')));});
describe('launcher framework',()=>{
  it.each(['preview','missing-capability'])('默认配置%s时不冒充可用也不落入实例占位',async(mode)=>{if(mode==='missing-capability'){vi.stubGlobal('__TAURI_INTERNALS__',{});native.session.mockResolvedValue({schemaVersion:1,mode:'LOCAL',sessionToken:'a'.repeat(64),capabilities:{instances:'AVAILABLE'}});native.request.mockResolvedValue(new Response(JSON.stringify({instances:[],problems:[]}),{status:200,headers:{'Content-Type':'application/json'}}));}root=document.createElement('div');document.body.append(root);app=createApp(LauncherApp);app.mount(root);for(let i=0;i<20;i++){await Promise.resolve();await nextTick();}if(mode==='preview'){root.querySelector<HTMLButtonElement>('[data-enter-preview]')!.click();await nextTick();}root.querySelector<HTMLButtonElement>('[data-launcher-page="defaults"]')!.click();await nextTick();expect(root.querySelector('[data-defaults-unavailable]')?.textContent).toContain('默认配置需要可用的本机核心');expect(root.textContent).not.toContain('还没有游戏实例');});
  it('原生核心就绪后直接进入本机工作台，不把玩家停在预览门',async()=>{
    vi.stubGlobal('__TAURI_INTERNALS__',{});native.session.mockResolvedValue({schemaVersion:1,mode:'LOCAL',sessionToken:'a'.repeat(64),capabilities:{instances:'AVAILABLE'}});
    native.request.mockResolvedValue(new Response(JSON.stringify({instances:[],problems:[]}),{status:200,headers:{'Content-Type':'application/json'}}));
    root=document.createElement('div');document.body.append(root);app=createApp(LauncherApp);app.mount(root);
    for(let i=0;i<20;i++){await Promise.resolve();await nextTick();}
    expect(root.querySelector('.launcher-sidebar')).not.toBeNull();expect(root.querySelector('[name="password"]')).toBeNull();
  });
  it('原生后台启动失败原因可见并可重试，不静默退回不可用预览',async()=>{
    vi.stubGlobal('__TAURI_INTERNALS__',{});native.session.mockRejectedValue(new Error('随包Java缺失，请重新解压完整目录'));
    root=document.createElement('div');document.body.append(root);app=createApp(LauncherApp);app.mount(root);
    for(let i=0;i<16;i++){await Promise.resolve();await nextTick();}
    expect(root.textContent).toContain('随包Java缺失');expect(root.querySelector('[data-retry-local]')).not.toBeNull();
  });
  it.each(['READY','CHECKING'])('编辑外观保留非默认本次身份和%s检查，不重建启动组件或再提交',async(state)=>{
    const check={id:'check',instanceId:'instance',state,createdAt:'',expiresAt:new Date(Date.now()+300000).toISOString(),installationId:'installed',versionId:'1.21.1',javaMajor:21,errorCode:null,message:'已检查'};
    const fetcher=vi.fn(async(url:string)=>new Response(JSON.stringify(url.endsWith('/session')?{schemaVersion:1,mode:'LOCAL',sessionToken:'a'.repeat(64),capabilities:{catalog:'AVAILABLE',instances:'AVAILABLE',vanilla:'AVAILABLE',mods:'AVAILABLE',runtimes:'AVAILABLE',identities:'AVAILABLE',launch:'AVAILABLE'}}:url.endsWith('/instances')?{instances:[{id:'instance',name:'界面实例',gameVersion:'1.21.1',loader:'VANILLA',state:'PREPARED',createdAt:'',installation:{installationId:'installed',versionId:'1.21.1',requiredJavaMajor:21,fileCount:3,platform:{bits:64}}}],problems:[]}:url.endsWith('/identities')?{identities:[{id:'alice',name:'Alice',kind:'OFFLINE'},{id:'bob',name:'Bob',kind:'OFFLINE'}],selectedId:'alice',temporary:true}:url.endsWith('/runtimes')?{runtimes:[],diagnostics:[],platformBits:64}:url.endsWith('/launch-settings')?{revision:'revision',settings:{javaId:null,initialMemoryMiB:256,maximumMemoryMiB:2048,width:960,height:540,extraJvmArgs:[],acceptUnknownJava:false}}:url.includes('/launch-checks')?{check}:url.includes('/tasks')?{tasks:[],problems:[],total:0,offset:0,limit:50}:{launches:[]}),{headers:{'Content-Type':'application/json'}}));vi.stubGlobal('fetch',fetcher);
    async function settle(){for(let i=0;i<14;i++){await Promise.resolve();await nextTick();}}
    root=document.createElement('div');document.body.append(root);app=createApp(LauncherApp);app.mount(root);await settle();root.querySelector<HTMLButtonElement>('[data-enter-local]')!.click();await settle();root.querySelector<HTMLButtonElement>('[data-launcher-page="instances"]')!.click();await settle();root.querySelector<HTMLButtonElement>('[data-open-launch]')!.click();await settle();
    const identity=root.querySelector<HTMLSelectElement>('.launcher-launch-controls select')!;identity.value='bob';identity.dispatchEvent(new Event('change'));await settle();root.querySelector<HTMLButtonElement>('[data-check-launch]')!.click();await settle();const start=root.querySelector<HTMLButtonElement>('[data-start-launch]')!;expect(start.disabled).toBe(state!=='READY');
    root.querySelector<HTMLButtonElement>('[data-workspace-settings]')!.click();await settle();expect(root.querySelector('.launcher-launch-controls select')).toBe(identity);const appearance=root.querySelector<HTMLElement>('[aria-label="工作台外观"][role="dialog"]')!;expect(appearance).not.toBeNull();appearance.querySelector<HTMLButtonElement>('[data-create-layout]')!.click();await settle();Array.from(appearance.querySelectorAll('button')).find(b=>b.textContent==='编辑排列')!.click();await settle();root.querySelector<HTMLButtonElement>('[data-auto-arrange]')!.click();await settle();root.querySelector<HTMLButtonElement>('[data-apply-layout]')!.click();await settle();root.querySelector<HTMLButtonElement>('[data-close-appearance]')!.click();await settle();
    expect(root.querySelector('.launcher-launch-controls select')).toBe(identity);expect(identity.value).toBe('bob');expect(root.querySelector('[data-start-launch]')).toBe(start);expect(start.disabled).toBe(state!=='READY');expect(fetcher.mock.calls.filter(([url])=>url.endsWith('/launch-checks'))).toHaveLength(1);
  });
  it('keeps feature status reachable in preview and changes no game or network state when opened',async()=>{
    root=document.createElement('div');document.body.append(root);app=createApp(LauncherApp);app.mount(root);
    root.querySelector<HTMLButtonElement>('[data-enter-preview]')!.click();await nextTick();
    expect(root.querySelector('[data-feature-status-open]')).not.toBeNull();
    const count=vi.mocked(fetch).mock.calls.length;
    root.querySelector<HTMLButtonElement>('[data-feature-status-open]')!.click();await nextTick();
    expect(root.querySelector('[aria-label="功能运行情况"]')).not.toBeNull();
    expect(vi.mocked(fetch).mock.calls.length).toBe(count);
  });
  it('connects installed instances, temporary identities and settings only in capable local mode',async()=>{
    const identity={id:'alice',name:'Alice',kind:'OFFLINE'};
    const fetcher=vi.fn(async(url:string,_init?:RequestInit)=>new Response(JSON.stringify(url.endsWith('/session')?{schemaVersion:1,mode:'LOCAL',sessionToken:'a'.repeat(64),capabilities:{catalog:'AVAILABLE',instances:'AVAILABLE',vanilla:'AVAILABLE',mods:'AVAILABLE',runtimes:'AVAILABLE',identities:'AVAILABLE',launch:'AVAILABLE'}}:url.endsWith('/instances')?{instances:[{id:'instance',name:'界面实例',gameVersion:'1.21.1',loader:'VANILLA',state:'PREPARED',createdAt:'',installation:{installationId:'installed',versionId:'1.21.1',requiredJavaMajor:21,fileCount:3,platform:{bits:64}}}],problems:[]}:url.endsWith('/identities')?{identities:[identity],selectedId:'alice',temporary:true}:url.endsWith('/runtimes')?{runtimes:[],diagnostics:[],platformBits:64}:url.endsWith('/launch-settings')?{revision:'revision',settings:{javaId:null,initialMemoryMiB:256,maximumMemoryMiB:2048,width:960,height:540,extraJvmArgs:[],acceptUnknownJava:false}}:url.includes('/tasks')?{tasks:[],problems:[],total:0,offset:0,limit:50}:{launches:[]}),{headers:{'Content-Type':'application/json'}}));vi.stubGlobal('fetch',fetcher);
    async function settle(){for(let i=0;i<14;i++){await Promise.resolve();await nextTick();}}
    root=document.createElement('div');document.body.append(root);app=createApp(LauncherApp);app.mount(root);await settle();
    root.querySelector<HTMLButtonElement>('[data-enter-local]')!.click();await settle();
    root.querySelector<HTMLButtonElement>('[data-launcher-page="accounts"]')!.click();await settle();expect(root.textContent).toContain('临时离线身份，本次会话有效');expect(root.querySelector('.launcher-account-chip')?.textContent).toContain('Alice');
    root.querySelector<HTMLButtonElement>('[data-launcher-page="settings"]')!.click();await settle();expect(root.querySelector('[data-save-launch-settings]')).not.toBeNull();
    root.querySelector<HTMLButtonElement>('[data-launcher-page="instances"]')!.click();await settle();root.querySelector<HTMLButtonElement>('[data-open-launch]')!.click();await settle();expect(root.querySelector('[data-check-launch]')).not.toBeNull();expect(root.querySelector<HTMLButtonElement>('[data-start-launch]')!.disabled).toBe(true);
    const launch=root.querySelector('[data-check-launch]');const count=fetcher.mock.calls.length;
    for(const preset of ['simple','cards','immersive','workbench']){const select=root.querySelector<HTMLSelectElement>('[data-quick-preset]')!;select.value='preset:'+preset;select.dispatchEvent(new Event('change'));await settle();expect(root.querySelector('[data-check-launch]')).toBe(launch);expect(root.querySelector('.launcher-account-chip')?.textContent).toContain('Alice');}
    expect(fetcher.mock.calls.length).toBe(count);expect(JSON.parse(localStorage.getItem('sqy.launcher.workspace.v1')!).activeId).toBe('preset:workbench');
    const failing=vi.spyOn(window,'localStorage','get').mockReturnValue({setItem:()=>{throw new Error('quota');}} as unknown as Storage);const select=root.querySelector<HTMLSelectElement>('[data-quick-preset]')!;select.value='preset:cards';select.dispatchEvent(new Event('change'));await settle();expect(root.querySelector('.launcher-shell')?.classList.contains('workspace-workbench')).toBe(true);expect(root.textContent).toContain('未保存');failing.mockRestore();
    expect(fetcher.mock.calls.every(([,init])=>!(init as RequestInit|undefined)?.method|| (init as RequestInit).method==='GET')).toBe(true);
  });
  it('keeps preview separate from authenticated panel and game identities',()=>{
    const session=createLauncherSession();session.mode='preview';
    expect(session.panelIdentity).toBeNull();expect(session.gameIdentity).toBeNull();expect(createLauncherSession().mode).toBe('login');
  });
  it('opens real navigation without claiming downloads or login have succeeded',async()=>{
    root=document.createElement('div');document.body.append(root);app=createApp(LauncherApp);app.mount(root);
    expect(root.querySelector('[name="password"]')).not.toBeNull();(root.querySelector('[data-enter-preview]') as HTMLButtonElement).click();await nextTick();
    expect(root.textContent).toContain('框架预览');expect(root.querySelectorAll('[data-launcher-page]')).toHaveLength(16);expect(root.querySelector('[data-launcher-page="defaults"]')).not.toBeNull();expect(root.querySelector('[data-launcher-page="tools"]')).not.toBeNull();
    for(const id of ['versions','mods','packs']){(root.querySelector(`[data-launcher-page="${id}"]`) as HTMLButtonElement).click();await nextTick();expect(root.textContent).toContain('待接入');expect(root.querySelector('[data-feature-action]')?.hasAttribute('disabled')).toBe(true);}
  });
});
