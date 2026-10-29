import {afterEach,describe,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import PackCenter from '../PackCenter.vue';
import {LocalLauncherClient} from '../local-api';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
const reply=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
const sources={sources:[{source:'MODRINTH',name:'Modrinth',state:'AVAILABLE',reason:'官方目录'},{source:'FTB',name:'FTB',state:'AVAILABLE',reason:'官方目录'},{source:'ATLAUNCHER',name:'ATLauncher',state:'AVAILABLE',reason:'官方目录'},{source:'CURSEFORGE',name:'CurseForge',state:'PENDING',reason:'整合包适配尚未接入'}]};
const project={source:'MODRINTH',id:'TestPack',name:'真实目录包',summary:'<img src=x onerror=alert(1)>',gameVersions:['1.21.1'],loaders:['fabric']};
const page={items:[project],offset:0,limit:20,total:21,provenance:'NETWORK',fetchedAt:'2026-10-23T00:00:00Z',warning:null};
async function settle(){for(let i=0;i<18;i++){await Promise.resolve();await nextTick();}}
function mount(){root=document.createElement('div');document.body.append(root);app=createApp(PackCenter,{client:new LocalLauncherClient('a'.repeat(64))});app.mount(root);}
function submit(){root.querySelector('form')!.dispatchEvent(new Event('submit',{cancelable:true}));}
afterEach(()=>{app?.unmount();root?.remove();vi.useRealTimers();vi.unstubAllGlobals();});
describe('整合包目录',()=>{
 it('从第二页回第一页仍保留已提交关键词和分类，不采用草稿',async()=>{
  const request=vi.fn(async(url:string)=>responseFor(url));
  function responseFor(url:string){if(url.includes('/sources'))return reply(sources);if(url.includes('/categories'))return reply({categories:[{id:'technology',name:'technology',group:'categories'},{id:'magic',name:'magic',group:'categories'}]});const params=new URL(url,location.origin).searchParams;return reply({...page,offset:Number(params.get('offset')??0)});}
  vi.stubGlobal('fetch',request);mount();await settle();const input=root.querySelector<HTMLInputElement>('[data-pack-query]')!;
  input.value='original';input.dispatchEvent(new Event('input'));root.querySelector<HTMLButtonElement>('[data-content-category="technology"]')!.click();await settle();submit();await settle();root.querySelector<HTMLButtonElement>('[data-pack-next]')!.click();await settle();
  input.value='unsubmitted draft';input.dispatchEvent(new Event('input'));root.querySelector<HTMLButtonElement>('[data-content-category="magic"]')!.click();await settle();Array.from(root.querySelectorAll('button')).find(b=>b.textContent==='上一页')!.click();await settle();
  const url=new URL(request.mock.calls.filter(([url])=>url.includes('/search')).at(-1)![0],location.origin);expect(url.searchParams.get('offset')).toBe('0');expect(url.searchParams.get('q')).toBe('original');expect(url.searchParams.get('category')).toBe('technology');
 });
 it('查询与版本展示真实来源且不执行HTML或安装',async()=>{
  const fetcher=vi.fn(async(url:string,_init?:RequestInit)=>reply(url.includes('/sources')?sources:url.includes('/search')?page:{project,versions:[{id:'v1',name:'兼容版本1',gameVersions:['1.21.1'],loaders:['fabric'],publishedAt:'2026-01-01T00:00:00Z'}],provenance:'CACHE',fetchedAt:'2026-10-23T00:00:00Z',warning:null}));vi.stubGlobal('fetch',fetcher);mount();await settle();submit();await settle();
  expect(root.textContent).toContain('真实目录包');expect(root.querySelector('img')).toBeNull();root.querySelector<HTMLButtonElement>('[data-pack-project="TestPack"]')!.click();await settle();expect(root.textContent).toContain('兼容版本1');expect(root.textContent).toContain('缓存');expect(root.textContent).toContain('安装待接入');expect(fetcher.mock.calls.every(([,init])=>!(init as RequestInit|undefined)?.method||(init as RequestInit).method==='GET')).toBe(true);
 });
 it('切换来源清空旧结果并拒绝晚到数据和未接入源请求',async()=>{
  const pending=new Map<string,(response:Response)=>void>();
  const fetcher=vi.fn((url:string)=>url.includes('/search')?new Promise<Response>(r=>pending.set(new URL(url,location.origin).searchParams.get('source')!,r)):Promise.resolve(reply(sources)));vi.stubGlobal('fetch',fetcher);mount();await settle();root.querySelector<HTMLSelectElement>('[data-pack-source]')!.value='FTB';root.querySelector('[data-pack-source]')!.dispatchEvent(new Event('change'));await settle();pending.get('MODRINTH')!(reply(page));await settle();expect(root.querySelector('[data-pack-project]')).toBeNull();
  pending.get('FTB')!(reply({...page,items:[{...project,source:'FTB',name:'新来源自动目录'}]}));await settle();expect(root.textContent).toContain('新来源自动目录');
  const select=root.querySelector<HTMLSelectElement>('[data-pack-source]')!;select.value='CURSEFORGE';select.dispatchEvent(new Event('change'));await settle();submit();await settle();expect(fetcher.mock.calls.filter(([url])=>url.includes('/search'))).toHaveLength(2);expect(root.querySelector('[data-pack-project]')).toBeNull();expect(root.textContent).toContain('尚未接入');
 });
 it('翻页保留已提交词，并在429后显示等待',async()=>{
  const fetcher=vi.fn(async(url:string)=>reply(url.includes('/sources')?sources:url.includes('offset=20')?{error:{code:'RATE_LIMITED',message:'平台限流',retryAfterSeconds:10}}:page,url.includes('offset=20')?429:200));vi.stubGlobal('fetch',fetcher);mount();await settle();const input=root.querySelector<HTMLInputElement>('[data-pack-query]')!;input.value='原关键词';input.dispatchEvent(new Event('input'));submit();await settle();input.value='草稿';input.dispatchEvent(new Event('input'));await settle();root.querySelector<HTMLButtonElement>('[data-pack-next]')!.click();await settle();
  const url=new URL(fetcher.mock.calls.at(-1)![0],location.origin);expect(url.searchParams.get('q')).toBe('原关键词');expect(root.textContent).toContain('平台限流');expect(root.querySelector<HTMLButtonElement>('[data-pack-search]')!.disabled).toBe(true);
 });
});
