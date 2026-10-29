import {afterEach,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import ModCenter from '../ModCenter.vue';
import PackCenter from '../PackCenter.vue';
import {LocalLauncherClient} from '../local-api';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
const response=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
const presentation={iconUrl:'https://cdn.modrinth.com/data/test/icon.png',categories:[{id:'technology',name:'technology',group:'categories'}],description:'# 项目玩法\n\n有完整说明。<script>alert(1)</script>',gallery:['https://cdn.modrinth.com/data/test/screen.png'],gameVersions:['1.20.6'],loaders:['fabric'],updatedAt:'2026-10-20T00:00:00Z',links:{}};
const project={source:'MODRINTH',projectId:'test',id:'test',name:'测试整合包',title:'测试模组',summary:'项目简介',presentation,gameVersions:['1.20.6'],loaders:['fabric']};
async function settle(){for(let i=0;i<25;i++){await Promise.resolve();await nextTick();}}
function mount(kind:'mod'|'pack'){root=document.createElement('div');document.body.append(root);app=createApp(kind==='mod'?ModCenter:PackCenter,{client:new LocalLauncherClient('a'.repeat(64))});app.mount(root);}
afterEach(()=>{app?.unmount();root?.remove();vi.unstubAllGlobals();});
for(const kind of ['mod','pack'] as const)it(`${kind}: 来源分类传入查询，项目有图片简介和安全详情`,async()=>{
 const request=vi.fn(async(url:string)=>response(url.includes('/sources')?{sources:[{source:'MODRINTH',name:'Modrinth',state:'AVAILABLE',loaders:['fabric']},...(kind==='pack'?[{source:'FTB',name:'FTB',state:'AVAILABLE'}]:[])]}:url.includes('/categories')?{categories:[{id:'technology',name:'technology',group:'categories'},{id:'new-category',name:'new-category',group:'categories'}]}:url.includes('/catalog')?{items:[],total:0,snapshotId:'s'}:url.includes('/search')?{items:[project],total:21,accessibleTotal:21,offset:0,limit:20,provenance:'NETWORK'}:url.includes('/files')?{items:[],total:0}:url.includes('/project')||url.includes('/packs/MODRINTH/')?{project,versions:[],provenance:'NETWORK'}:{tasks:[],total:0,problems:[]}));
 vi.stubGlobal('fetch',request);mount(kind);await settle();
 const category=root.querySelector<HTMLButtonElement>('[data-content-category="technology"]');expect(category).not.toBeNull();category!.click();await settle();
 root.querySelector(`form${kind==='mod'?'[data-mod-search]':''}`)!.dispatchEvent(new Event('submit',{cancelable:true}));await settle();
 expect(request.mock.calls.some(([u])=>u.includes('/search')&&new URL(u,location.origin).searchParams.get('category')==='technology')).toBe(true);
 expect(root.querySelector<HTMLImageElement>('[data-project-image]')?.src).toBe(presentation.iconUrl);expect(root.textContent).toContain('项目简介');expect(root.textContent).toContain('科技');
 root.querySelector<HTMLButtonElement>(kind==='mod'?'[data-mod-project]':'[data-pack-project]')!.click();await settle();
 expect(root.querySelector('[data-project-introduction]')?.textContent).toContain('项目玩法');expect(root.querySelector('[data-project-introduction] script')).toBeNull();expect(root.querySelectorAll('[data-project-gallery] img')).toHaveLength(1);
 if(kind==='pack'){const select=root.querySelector<HTMLSelectElement>('[data-pack-source]')!;select.value='FTB';select.dispatchEvent(new Event('change'));await settle();expect(root.querySelector('[data-content-category="technology"]')).toBeNull();expect(root.textContent).toContain('不支持完整分类筛选');}
});
it('分类读取失败仍可普通搜索，晚到分类不会覆盖切换后的来源',async()=>{
 let release!:(r:Response)=>void;
 const request=vi.fn((url:string)=>url.includes('/categories')?(url.includes('MODRINTH')?new Promise<Response>(r=>release=r):Promise.resolve(response({error:{code:'SOURCE_UNAVAILABLE',message:'分类读取失败'}},502))):Promise.resolve(response(url.includes('/sources')?{sources:[{source:'MODRINTH',state:'AVAILABLE',loaders:[]},{source:'CURSEFORGE',state:'AVAILABLE',loaders:[]}]}:url.includes('/catalog')?{items:[],total:0,snapshotId:'s'}:url.includes('/search')?{items:[],total:0,accessibleTotal:0}:{tasks:[],total:0})));
 vi.stubGlobal('fetch',request);mount('mod');await settle();root.querySelector<HTMLButtonElement>('[data-mod-source="CURSEFORGE"]')!.click();await settle();release(response({categories:[{id:'utility',name:'旧分类',group:'categories'}]}));await settle();
 expect(root.textContent).not.toContain('旧分类');expect(root.textContent).toContain('分类读取失败');root.querySelector('form[data-mod-search]')!.dispatchEvent(new Event('submit',{cancelable:true}));await settle();expect(request.mock.calls.some(([u])=>u.includes('/mods/search')&&u.includes('CURSEFORGE'))).toBe(true);
});
