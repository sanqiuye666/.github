import {afterEach,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import VersionCenter from '../VersionCenter.vue';
import {LocalLauncherClient} from '../local-api';
import type {GameInstance} from '../local-types';
let app:ReturnType<typeof createApp>,root:HTMLDivElement;
const page={snapshotId:'a'.repeat(64),total:100,filteredTotal:1,offset:0,limit:50,typeCounts:{release:10,snapshot:20,old_alpha:30,old_beta:40},items:[{id:'official',type:'release',releaseTime:'2024-01-01T00:00:00Z'}],source:'NETWORK',fetchedAt:'2026-10-27T00:00:00Z',warning:null};
async function settle(){for(let i=0;i<14;i++){await Promise.resolve();await nextTick();}}
function mount(instances:GameInstance[]=[],manage=vi.fn(),state:Record<string,unknown>={}){
 root=document.createElement('div');document.body.append(root);app=createApp(VersionCenter,{client:new LocalLauncherClient('a'.repeat(64)),instances,onManage:manage,...state});app.mount(root);
}
afterEach(()=>{app?.unmount();root?.remove();vi.unstubAllGlobals();});
it('旧版明确分为Beta和Alpha，分类向官方完整目录查询',async()=>{
 const request=vi.fn(async(_url:string)=>new Response(JSON.stringify(page),{headers:{'Content-Type':'application/json'}}));vi.stubGlobal('fetch',request);mount();await settle();
 root.querySelector<HTMLButtonElement>('[data-version-category="legacy"]')!.click();await settle();
 expect(new URL(request.mock.calls.at(-1)![0],location.origin).searchParams.get('type')).toBe('old_beta');
 expect(root.querySelector('[data-version-legacy="old_beta"]')?.getAttribute('aria-pressed')).toBe('true');
 root.querySelector<HTMLButtonElement>('[data-version-legacy="old_alpha"]')!.click();await settle();
 expect(new URL(request.mock.calls.at(-1)![0],location.origin).searchParams.get('type')).toBe('old_alpha');
 root.querySelector<HTMLButtonElement>('[data-version-category="release"]')!.click();await settle();
 expect(new URL(request.mock.calls.at(-1)![0],location.origin).searchParams.get('type')).toBe('release');
 expect(root.querySelector('[data-version-category="release"]')?.textContent).toContain('10');
});
it('刷新后保持已提交分类与搜索，不提交输入草稿',async()=>{
 const request=vi.fn(async(_url:string)=>new Response(JSON.stringify(page),{headers:{'Content-Type':'application/json'}}));vi.stubGlobal('fetch',request);mount();await settle();
 const input=root.querySelector<HTMLInputElement>('[data-version-query]')!;input.value='1.20';input.dispatchEvent(new Event('input'));root.querySelector('form[data-version-search]')!.dispatchEvent(new Event('submit',{cancelable:true}));await settle();
 root.querySelector<HTMLButtonElement>('[data-version-category="snapshot"]')!.click();await settle();input.value='draft';input.dispatchEvent(new Event('input'));await nextTick();
 root.querySelector<HTMLButtonElement>('[data-refresh-catalog]')!.click();await settle();const url=new URL(request.mock.calls.at(-1)![0],location.origin);
 expect(url.searchParams.get('q')).toBe('1.20');expect(url.searchParams.get('type')).toBe('snapshot');expect(input.value).toBe('draft');expect(request.mock.calls.some(([url])=>url.endsWith('/catalog/refresh'))).toBe(true);
});
it.each(['STALE_CACHE','NETWORK'])('刷新%s元数据保留到同一快照的筛选与分页',async(source)=>{
 const warning=source==='STALE_CACHE'?'刷新未完成，无法连接官方来源，已使用缓存':null;
 const request=vi.fn(async(url:string)=>new Response(JSON.stringify({...page,source:url.endsWith('/catalog/refresh')?source:'CACHE',warning:url.endsWith('/catalog/refresh')?warning:null,filteredTotal:2}),{headers:{'Content-Type':'application/json'}}));
 vi.stubGlobal('fetch',request);mount();await settle();root.querySelector<HTMLButtonElement>('[data-refresh-catalog]')!.click();await settle();
 expect(root.textContent).toContain(source==='STALE_CACHE'?'缓存回退':'官方实时数据');if(warning)expect(root.textContent).toContain(warning);
 root.querySelector<HTMLButtonElement>('[data-load-more]')!.click();await settle();expect(root.textContent).toContain(source==='STALE_CACHE'?'缓存回退':'官方实时数据');if(warning)expect(root.textContent).toContain(warning);
});
it('筛选遇到另一快照时不沿用上次刷新状态',async()=>{
 let changed=false;vi.stubGlobal('fetch',vi.fn(async(url:string)=>new Response(JSON.stringify({...page,snapshotId:changed?'b'.repeat(64):page.snapshotId,source:url.endsWith('/catalog/refresh')?'STALE_CACHE':'CACHE',warning:url.endsWith('/catalog/refresh')?'旧快照回退提示':null}),{headers:{'Content-Type':'application/json'}})));
 mount();await settle();root.querySelector<HTMLButtonElement>('[data-refresh-catalog]')!.click();await settle();changed=true;root.querySelector<HTMLButtonElement>('[data-version-category="snapshot"]')!.click();await settle();expect(root.textContent).toContain('本机有效缓存');expect(root.textContent).not.toContain('旧快照回退提示');
});
it('已安装只展示安装记录，进入既有管理且不写文件',async()=>{
 const request=vi.fn(async(_url:string)=>new Response(JSON.stringify(page),{headers:{'Content-Type':'application/json'}}));vi.stubGlobal('fetch',request);
 const common={name:'世界',gameVersion:'1.20.6',loader:'VANILLA',createdAt:'',state:'PREPARED' as const},manage=vi.fn();
 mount([{...common,id:'prepared',installation:null},{...common,id:'installed',name:'已装世界',installation:{installationId:'proof',versionId:'1.20.6',installedAt:'',fileCount:10,requiredJavaMajor:21,platform:{osName:'windows',arch:'amd64',bits:64}}}],manage);await settle();const count=request.mock.calls.length;
 root.querySelector<HTMLButtonElement>('[data-version-tab="installed"]')!.click();await settle();expect(root.querySelectorAll('[data-installed-version]')).toHaveLength(1);expect(root.textContent).toContain('1 个实例尚未安装');
 root.querySelector<HTMLButtonElement>('[data-manage-installed="installed"]')!.click();await nextTick();expect(manage).toHaveBeenCalledWith('installed');expect(request.mock.calls.length).toBe(count);
 const q=root.querySelector<HTMLInputElement>('[data-installed-query]')!;q.value='不匹配';q.dispatchEvent(new Event('input'));await nextTick();expect(root.querySelectorAll('[data-installed-version]')).toHaveLength(0);
});

it.each(['loading','error'])('读取安装记录%s时不显示空安装成功状态',async(kind)=>{
 vi.stubGlobal('fetch',vi.fn(async(_url:string)=>new Response(JSON.stringify(page),{headers:{'Content-Type':'application/json'}})));
 mount([],vi.fn(),kind==='loading'?{instancesLoading:true}:{instancesError:'安装记录无法读取'});await settle();root.querySelector<HTMLButtonElement>('[data-version-tab="installed"]')!.click();await nextTick();
 expect(root.textContent).not.toContain('还没有已安装的版本');expect(root.textContent).toContain(kind==='loading'?'正在读取本机安装记录':'安装记录无法读取');
});
