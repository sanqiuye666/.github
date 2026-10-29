import {afterEach,describe,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import ModCenter from '../ModCenter.vue';
import {LocalLauncherClient} from '../local-api';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
const reply=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
const sources={sources:[{source:'MODRINTH',state:'AVAILABLE',reason:null,loaders:['fabric'],warning:null},{source:'CURSEFORGE',state:'AVAILABLE',reason:null,loaders:['fabric'],warning:null}]};
const catalog={snapshotId:'a'.repeat(64),total:1,filteredTotal:1,offset:0,limit:100,items:[{id:'1.21.1',type:'release',releaseTime:'2024'}],typeCounts:{release:1,snapshot:0,old_alpha:0,old_beta:0},source:'CACHE',fetchedAt:'',warning:null};
const results=(source:string,title:string)=>({items:[{source,projectId:'project',title,summary:'summary',author:null,downloads:null}],offset:0,limit:20,total:21,accessibleTotal:21,platformLimited:false});
async function settle(){for(let i=0;i<14;i++){await Promise.resolve();await nextTick();}}
function mount(){root=document.createElement('div');document.body.append(root);app=createApp(ModCenter,{client:new LocalLauncherClient('a'.repeat(64))});app.mount(root);}
function submit(){root.querySelector('form[data-mod-search]')!.dispatchEvent(new Event('submit',{cancelable:true}));}
afterEach(()=>{app?.unmount();root?.remove();vi.useRealTimers();vi.unstubAllGlobals();});
describe('official mod center',()=>{
 it('next page retains the submitted keyword while a draft is edited',async()=>{
  const request=vi.fn(async(url:string)=>reply(url.includes('/sources')?sources:url.includes('/catalog')?catalog:url.includes('/mods/search')?results('MODRINTH','结果'):{tasks:[],problems:[],total:0}));vi.stubGlobal('fetch',request);mount();await settle();
  const input=root.querySelector<HTMLInputElement>('[data-mod-query]')!;input.value='sodium';input.dispatchEvent(new Event('input'));submit();await settle();
  input.value='iris';input.dispatchEvent(new Event('input'));await nextTick();root.querySelector<HTMLButtonElement>('[data-mod-next]')!.click();await settle();
  const url=new URL(request.mock.calls.filter(([url])=>url.includes('/mods/search')).at(-1)![0],location.origin);
  expect(url.searchParams.get('q')).toBe('sodium');expect(url.searchParams.get('offset')).toBe('20');
 });
 for(const action of ['card','search'] as const)it(`a delayed dependency cannot replace a newer ${action} selection`,async()=>{
  let release!:(response:Response)=>void;const projects={...results('MODRINTH','项目一'),items:[{source:'MODRINTH',projectId:'project',title:'项目一',summary:'',author:null,downloads:null},{source:'MODRINTH',projectId:'newer',title:'新项目',summary:'',author:null,downloads:null}]};
  const dependencyFile={ref:{source:'MODRINTH',projectId:'project',versionId:'v',fileKey:'a'.repeat(64)},fileName:'mod.jar',gameVersions:[],loaders:[],dependencies:[{kind:'REQUIRED',projectId:'dependency',versionId:null,fileName:null}],downloadable:false,blockedReason:'测试只读'};
  const request=vi.fn((url:string)=>url.includes('/mods/project?')?new Promise<Response>(r=>release=r):Promise.resolve(reply(url.includes('/sources')?sources:url.includes('/catalog')?catalog:url.includes('/mods/search')?projects:url.includes('/mods/files?')?{items:[dependencyFile],total:1,accessibleTotal:1,offset:0,limit:20}:{tasks:[],problems:[],total:0})));vi.stubGlobal('fetch',request);
  mount();await settle();submit();await settle();root.querySelector<HTMLButtonElement>('[data-mod-project="project"]')!.click();await settle();root.querySelector<HTMLButtonElement>('[data-open-dependency]')!.click();await settle();
  if(action==='card')root.querySelector<HTMLButtonElement>('[data-mod-project="newer"]')!.click();else submit();await settle();
  release(reply({project:{source:'MODRINTH',projectId:'dependency',title:'过期依赖详情',summary:''}}));await settle();
  expect(root.querySelector('[data-mod-files]')?.textContent||'').not.toContain('过期依赖详情');
  if(action==='card')expect(root.querySelector('[data-mod-files]')!.textContent).toContain('新项目');else expect(root.querySelector('[data-mod-files]')).toBeNull();
 });
 it('lateSourceResponseCannotReplaceCurrentResults',async()=>{
  let release!:(response:Response)=>void;
  vi.stubGlobal('fetch',vi.fn((url:string)=>url.includes('/sources')?Promise.resolve(reply(sources)):url.includes('/catalog')?Promise.resolve(reply(catalog)):url.includes('/mods/search')?(url.includes('CURSEFORGE')?Promise.resolve(reply(results('CURSEFORGE','当前来源结果'))):new Promise<Response>(r=>release=r)):Promise.resolve(reply({tasks:[],problems:[],total:0}))));
  mount();await settle();submit();await settle();root.querySelector<HTMLButtonElement>('[data-mod-source="CURSEFORGE"]')!.click();await settle();submit();await settle();release(reply(results('MODRINTH','旧来源结果')));await settle();expect(root.textContent).toContain('当前来源结果');expect(root.textContent).not.toContain('旧来源结果');
 });
 it('resets pages and makes no search request for an unconfigured source',async()=>{
  const request=vi.fn(async(url:string)=>reply(url.includes('/sources')?{sources:[sources.sources[0],{...sources.sources[1],state:'UNCONFIGURED',reason:'需要配置官方应用密钥',loaders:[]}]}:url.includes('/catalog')?catalog:url.includes('/mods/search')?results('MODRINTH','结果'):{tasks:[],problems:[],total:0}));vi.stubGlobal('fetch',request);mount();await settle();submit();await settle();root.querySelector<HTMLButtonElement>('[data-mod-next]')!.click();await settle();expect(request.mock.calls.some(([url])=>url.includes('offset=20'))).toBe(true);
  const version=root.querySelector<HTMLSelectElement>('[data-mod-game-version]')!;version.value='1.21.1';version.dispatchEvent(new Event('change'));await settle();submit();await settle();expect(request.mock.calls.filter(([url])=>url.includes('/mods/search')).at(-1)![0]).toContain('offset=0');root.querySelector<HTMLButtonElement>('[data-mod-source="CURSEFORGE"]')!.click();await settle();submit();await settle();expect(root.textContent).toContain('需要配置官方应用密钥');expect(request.mock.calls.some(([url])=>url.includes('/mods/search')&&url.includes('CURSEFORGE'))).toBe(false);
 });
 it('blocks repeated search while a source is cooling down',async()=>{
  vi.useFakeTimers();const request=vi.fn(async(url:string)=>url.includes('/sources')?reply(sources):url.includes('/catalog')?reply(catalog):url.includes('/mods/search')?reply({error:{code:'RATE_LIMITED',message:'等待重试',retryAfterSeconds:2}},429):reply({tasks:[],problems:[],total:0}));vi.stubGlobal('fetch',request);mount();await settle();submit();await settle();submit();await settle();expect(request.mock.calls.filter(([url])=>url.includes('/mods/search'))).toHaveLength(1);await vi.advanceTimersByTimeAsync(2500);submit();await settle();expect(request.mock.calls.filter(([url])=>url.includes('/mods/search'))).toHaveLength(2);
 });
});
