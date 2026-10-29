import {afterEach,describe,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import ModCenter from '../ModCenter.vue';
import PackCenter from '../PackCenter.vue';
import {LocalLauncherClient} from '../local-api';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
const reply=(value:unknown,status=200)=>new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json'}});
const sources={sources:[{source:'MODRINTH',name:'Modrinth',state:'AVAILABLE',loaders:['fabric']},{source:'CURSEFORGE',name:'CurseForge',state:'UNCONFIGURED',reason:'需要密钥'},{source:'FTB',name:'FTB',state:'AVAILABLE'}]};
const catalog={snapshotId:'a'.repeat(64),total:0,filteredTotal:0,offset:0,limit:100,items:[],typeCounts:{release:0,snapshot:0,old_alpha:0,old_beta:0},source:'CACHE',fetchedAt:'',warning:null};
const result=(kind:string,source='MODRINTH')=>({items:[kind==='mods'?{source,projectId:'one',title:'自动获取的目录',summary:'简介'}:{source,id:'one',name:'自动获取的目录',summary:'简介',gameVersions:[],loaders:[]}],total:1,accessibleTotal:1,offset:0,limit:20,platformLimited:false,provenance:'NETWORK',fetchedAt:'',warning:null});
async function settle(){for(let i=0;i<35;i++){await Promise.resolve();await nextTick();}}
function mount(kind:string){root=document.createElement('div');document.body.append(root);app=createApp(kind==='mods'?ModCenter:PackCenter,{client:new LocalLauncherClient('a'.repeat(64))});app.mount(root);}
afterEach(()=>{app?.unmount();root?.remove();vi.useRealTimers();vi.unstubAllGlobals();});
for(const kind of ['mods','packs'])describe(kind+'自动进入目录',()=>{
 function other(url:string){return url.includes('/sources')?sources:url.includes('/catalog')?catalog:url.includes('/categories')?{categories:[]}:{tasks:[],problems:[],total:0};}
 it('进入后只自动查询一次，直接显示响应，不用点击搜索',async()=>{
  const request=vi.fn(async(url:string)=>reply(url.includes('/search')?result(kind):other(url)));vi.stubGlobal('fetch',request);mount(kind);await settle();
  expect(request.mock.calls.filter(([url])=>url.includes('/search'))).toHaveLength(1);expect(root.textContent).toContain('自动获取的目录');
 });
 it('未配置来源不查询且清除旧目录',async()=>{
  const request=vi.fn(async(url:string)=>reply(url.includes('/search')?result(kind):other(url)));vi.stubGlobal('fetch',request);mount(kind);await settle();
  if(kind==='mods')root.querySelector<HTMLButtonElement>('[data-mod-source="CURSEFORGE"]')!.click();else{const s=root.querySelector<HTMLSelectElement>('[data-pack-source]')!;s.value='CURSEFORGE';s.dispatchEvent(new Event('change'));}await settle();
  expect(request.mock.calls.filter(([url])=>url.includes('/search'))).toHaveLength(1);expect(root.textContent).not.toContain('自动获取的目录');expect(root.textContent).toContain('需要密钥');
 });
 it('来源晚到时不自动提交玩家尚未提交的关键词草稿',async()=>{
  let resolve!:(value:Response)=>void;const request=vi.fn((url:string)=>url.includes('/sources')?new Promise<Response>(r=>resolve=r):Promise.resolve(reply(url.includes('/search')?result(kind):other(url))));vi.stubGlobal('fetch',request);mount(kind);await settle();
  const input=root.querySelector<HTMLInputElement>(kind==='mods'?'[data-mod-query]':'[data-pack-query]')!;input.value='尚未提交';input.dispatchEvent(new Event('input'));resolve(reply(sources));await settle();
  const calls=request.mock.calls.filter(([url])=>url.includes('/search'));expect(calls).toHaveLength(1);expect(new URL(calls[0]![0],location.origin).searchParams.get('q')??'').toBe('');expect(input.value).toBe('尚未提交');
 });
 it('自动查询被限流后显示等待，不轮询或自动重试',async()=>{
  vi.useFakeTimers();const request=vi.fn(async(url:string)=>url.includes('/search')?reply({error:{code:'RATE_LIMITED',message:'平台限流',retryAfterSeconds:2}},429):reply(other(url)));vi.stubGlobal('fetch',request);mount(kind);await settle();
  expect(root.textContent).toContain('平台限流');await vi.advanceTimersByTimeAsync(6000);await settle();expect(request.mock.calls.filter(([url])=>url.includes('/search'))).toHaveLength(1);
 });
 it('离开后晚到来源不会再触发自动查询',async()=>{
  let resolve!:(value:Response)=>void;const request=vi.fn((url:string)=>url.includes('/sources')?new Promise<Response>(r=>resolve=r):Promise.resolve(reply(other(url))));vi.stubGlobal('fetch',request);mount(kind);await settle();app!.unmount();app=undefined;resolve(reply(sources));await settle();expect(request.mock.calls.some(([url])=>url.includes('/search'))).toBe(false);
 });
});
