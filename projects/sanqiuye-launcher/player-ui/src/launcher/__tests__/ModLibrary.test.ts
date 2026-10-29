import {afterEach,describe,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import ModLibrary from '../ModLibrary.vue';
import {LocalLauncherClient} from '../local-api';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
const reply=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
async function settle(){for(let i=0;i<12;i++){await Promise.resolve();await nextTick();}}
afterEach(()=>{app?.unmount();root?.remove();vi.unstubAllGlobals();});
describe('real mod library records',()=>{
 it('escapes titles and never calls a failed content verification successful',async()=>{
  vi.stubGlobal('fetch',vi.fn(async(url:string)=>url.endsWith('/verify')?reply({error:{code:'IO_ERROR',message:'模组内容摘要已变化'}},409):reply({entries:[{id:'entry',taskId:'task',source:'MODRINTH',projectId:'project',versionId:'version',fileKey:'a'.repeat(64),title:'<img src=x onerror=alert(1)>',fileName:'mod.jar',gameVersions:['1.21.1'],loaders:['fabric'],sourceDigest:{algorithm:'SHA512',hex:'a'.repeat(128)},size:3,sha256:'a'.repeat(64),createdAt:'2026-01-01',dependencies:[],filePath:'G:/safe/content.jar'}],problems:[]})));
  root=document.createElement('div');document.body.append(root);app=createApp(ModLibrary,{client:new LocalLauncherClient('a'.repeat(64))});app.mount(root);await settle();expect(root.querySelector('img')).toBeNull();expect(root.textContent).toContain('<img src=x');root.querySelector<HTMLButtonElement>('[data-verify-mod]')!.click();await settle();expect(root.textContent).toContain('模组内容摘要已变化');expect(root.textContent).not.toContain('重新校验成功');
 });
});
