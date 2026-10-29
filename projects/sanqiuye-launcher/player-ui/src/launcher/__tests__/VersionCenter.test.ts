import {afterEach,describe,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import VersionCenter from '../VersionCenter.vue';
import {LocalLauncherClient} from '../local-api';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
const reply=(value:unknown,status=200)=>new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json'}});
const page=(ids:string[],offset=0,total=ids.length)=>({snapshotId:'a'.repeat(64),total,filteredTotal:total,offset,limit:50,typeCounts:{release:total,snapshot:0,old_alpha:0,old_beta:0},items:ids.map(id=>({id,type:'release',releaseTime:'2024-01-01T00:00:00Z'})),source:'NETWORK',fetchedAt:'2026-10-21T00:00:00Z',warning:null});
async function settle(){for(let i=0;i<8;i++){await Promise.resolve();await nextTick();}}
function mount(created=vi.fn()) {root=document.createElement('div');document.body.append(root);app=createApp(VersionCenter,{client:new LocalLauncherClient('a'.repeat(64)),onCreated:created});app.mount(root);}
afterEach(()=>{app?.unmount();root?.remove();vi.unstubAllGlobals();});
describe('real version panel',()=>{
  it('continues the submitted filter when the search input has an unsubmitted edit',async()=>{
    const request=vi.fn(async(url:string)=>reply(url.includes('offset=50')?page(['v50'],50,51):page(Array.from({length:50},(_,i)=>`v${i}`),0,51)));vi.stubGlobal('fetch',request);
    mount();await settle();const input=root.querySelector<HTMLInputElement>('[data-version-query]')!;
    input.value='unsubmitted';input.dispatchEvent(new Event('input'));await nextTick();
    root.querySelector<HTMLButtonElement>('[data-load-more]')!.click();await settle();
    const url=new URL(request.mock.calls.at(-1)![0],location.origin);
    expect(url.searchParams.get('offset')).toBe('50');expect(url.searchParams.get('q')||'').toBe('');
    expect(root.querySelectorAll('[data-version-id]')).toHaveLength(51);
  });
  it('shows all types by default and appends pages without losing a version',async()=>{
    const request=vi.fn(async(url:string)=>reply(url.includes('offset=50')?page(['v50'],50,51):page(Array.from({length:50},(_,i)=>`v${i}`),0,51)));vi.stubGlobal('fetch',request);
    mount();await settle();expect(root.querySelector('[data-version-category="all"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(root.querySelectorAll('[data-version-id]')).toHaveLength(50);
    (root.querySelector('[data-load-more]') as HTMLButtonElement).click();await settle();
    expect(root.querySelectorAll('[data-version-id]')).toHaveLength(51);expect(root.textContent).toContain('v50');
  });
  it('lateSearchCannotReplaceCurrentPage',async()=>{
    let release!:(response:Response)=>void;
    vi.stubGlobal('fetch',vi.fn((url:string)=>url.includes('q=old')?new Promise<Response>(resolve=>release=resolve):Promise.resolve(reply(page([url.includes('q=new')?'新筛选结果':'初始结果'])))));
    mount();await settle();const input=root.querySelector<HTMLInputElement>('[data-version-query]')!;
    input.value='old';input.dispatchEvent(new Event('input'));root.querySelector('form[data-version-search]')!.dispatchEvent(new Event('submit',{cancelable:true}));await settle();
    input.value='new';input.dispatchEvent(new Event('input'));root.querySelector('form[data-version-search]')!.dispatchEvent(new Event('submit',{cancelable:true}));await settle();
    release(reply(page(['旧筛选结果'])));await settle();expect(root.textContent).toContain('新筛选结果');expect(root.textContent).not.toContain('旧筛选结果');
  });
  it('resets pagination when filtering and reloads a changed snapshot',async()=>{
    let changed=false;
    const request=vi.fn(async(url:string)=>{
      if(url.includes('offset=50')){changed=true;return reply({error:{code:'CATALOG_CHANGED',message:'changed'}},409);}
      return reply(page(changed?['fresh']:Array.from({length:50},(_,i)=>`v${i}`),0,51));
    });vi.stubGlobal('fetch',request);
    mount();await settle();(root.querySelector('[data-load-more]') as HTMLButtonElement).click();await settle();
    expect(root.textContent).toContain('fresh');expect(root.querySelectorAll('[data-version-id]')).toHaveLength(1);
    expect(root.textContent).toContain('官方目录已更新');
    root.querySelector<HTMLButtonElement>('[data-version-category="snapshot"]')!.click();await settle();
    expect(request.mock.calls.at(-1)![0]).toContain('offset=0');expect(request.mock.calls.at(-1)![0]).toContain('type=snapshot');
  });
  it('prepares a Chinese instance only after explicit selection and submission',async()=>{
    const created=vi.fn();let posted:Record<string,string>|undefined;
    vi.stubGlobal('fetch',vi.fn(async(url:string,init:RequestInit)=>{
      if(url.endsWith('/instances')){posted=JSON.parse(init.body as string);return reply({instance:{id:'fixture',name:'中文实例',gameVersion:'1.0',loader:'VANILLA',state:'PREPARED',installation:null,createdAt:'2026-10-21T00:00:00Z'}},201);}
      return reply(page(['1.0']));
    }));
    mount(created);await settle();(root.querySelector('[data-version-id]') as HTMLButtonElement).click();await nextTick();
    const name=root.querySelector<HTMLInputElement>('[data-instance-name]')!;name.value='中文实例';name.dispatchEvent(new Event('input'));await nextTick();
    root.querySelector('[data-create-instance]')!.dispatchEvent(new Event('submit',{cancelable:true}));await settle();
    expect(posted).toMatchObject({name:'中文实例',versionId:'1.0',snapshotId:'a'.repeat(64)});expect(created).toHaveBeenCalledTimes(1);
    expect(root.textContent).not.toContain('游戏已启动');
  });
});
