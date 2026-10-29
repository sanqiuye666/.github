import {afterEach,describe,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import ModFiles from '../ModFiles.vue';
import {LocalLauncherClient} from '../local-api';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
const ref={source:'MODRINTH',projectId:'project',versionId:'version',fileKey:'a'.repeat(64)};
const file={ref,displayName:'普通文件',fileName:'mod.jar',gameVersions:['1.21.1'],loaders:['fabric'],releaseChannel:'release',publishedAt:'2026-01-01',size:3,sourceDigest:{algorithm:'SHA512',hex:'a'.repeat(128)},dependencies:[{kind:'REQUIRED',projectId:'dependency',versionId:null,fileName:null}],downloadable:true,blockedReason:null};
const reply=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
async function settle(){for(let i=0;i<12;i++){await Promise.resolve();await nextTick();}}
function mount(open=vi.fn(),invalid=vi.fn()){root=document.createElement('div');document.body.append(root);app=createApp(ModFiles,{client:new LocalLauncherClient('a'.repeat(64),invalid),project:{source:'MODRINTH',projectId:'project',title:'示例项目',author:null,summary:'',downloads:null},gameVersion:'1.21.1',loader:'fabric',onOpenProject:open});app.mount(root);}
afterEach(()=>{app?.unmount();root?.remove();vi.unstubAllGlobals();});
describe('explicit mod file choice',()=>{
 it('blocks attachments and sends the exact selected reference without downloading dependencies',async()=>{
  const calls=vi.fn(async(url:string,_init?:RequestInit)=>reply(url.endsWith('/downloads')?{task:{id:'task',state:'QUEUED'}}:{items:[file,{...file,ref:{...ref,fileKey:'b'.repeat(64)},fileName:'mod-sources.jar',downloadable:false,blockedReason:'附件'}],offset:0,limit:20,total:2,accessibleTotal:2,platformLimited:false}));vi.stubGlobal('fetch',calls);const open=vi.fn();mount(open);await settle();root.querySelector<HTMLButtonElement>('[data-open-dependency]')!.click();await settle();expect(open).toHaveBeenCalledWith({source:'MODRINTH',projectId:'dependency'});expect(calls.mock.calls.some(([url])=>url.endsWith('/downloads'))).toBe(false);const buttons=root.querySelectorAll<HTMLButtonElement>('[data-download-mod]');expect(buttons[1]!.disabled).toBe(true);buttons[0]!.click();buttons[0]!.click();await settle();const posts=calls.mock.calls.filter(([url])=>url.endsWith('/downloads'));expect(posts).toHaveLength(1);expect(JSON.parse(posts[0]![1]!.body as string)).toEqual({ref,gameVersion:'1.21.1',loader:'fabric'});
 });
 it('requires reselection after a 409 and invalidates only a 403 session',async()=>{
  const invalid=vi.fn();const calls=vi.fn(async(url:string)=>url.endsWith('/downloads')?reply({error:{code:'FILE_CHANGED',message:'文件已变化，请重新选择'}},409):reply({items:[file],offset:0,limit:20,total:1,accessibleTotal:1,platformLimited:false}));vi.stubGlobal('fetch',calls);mount(vi.fn(),invalid);await settle();root.querySelector<HTMLButtonElement>('[data-download-mod]')!.click();await settle();expect(invalid).not.toHaveBeenCalled();expect(root.textContent).toContain('重新选择');expect(root.querySelector<HTMLButtonElement>('[data-download-mod]')!.disabled).toBe(true);expect(calls.mock.calls.filter(([url])=>url.endsWith('/downloads'))).toHaveLength(1);
 });
 it('stops all file actions after a 403 session failure',async()=>{
  const invalid=vi.fn();const calls=vi.fn(async(url:string)=>url.endsWith('/downloads')?reply({error:{code:'SESSION_REQUIRED',message:'会话失效'}},403):reply({items:[file],offset:0,limit:20,total:1,accessibleTotal:1,platformLimited:false}));vi.stubGlobal('fetch',calls);mount(vi.fn(),invalid);await settle();root.querySelector<HTMLButtonElement>('[data-download-mod]')!.click();await settle();expect(invalid).toHaveBeenCalledTimes(1);expect(root.querySelector<HTMLButtonElement>('[data-download-mod]')!.disabled).toBe(true);root.querySelector<HTMLButtonElement>('[data-download-mod]')!.click();await settle();expect(calls.mock.calls.filter(([url])=>url.endsWith('/downloads'))).toHaveLength(1);
 });
});
