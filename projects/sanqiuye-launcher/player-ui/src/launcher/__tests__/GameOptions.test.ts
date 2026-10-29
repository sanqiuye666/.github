import {afterEach,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import GameOptions from '../GameOptions.vue';
import {LocalLauncherClient} from '../local-api';
const fields=[{key:'fov',label:'视场角',category:'画面',type:'integer',min:70,max:110,defaultValue:70},{key:'autoJump',label:'自动跳跃',category:'控制',type:'boolean',min:0,max:0,defaultValue:false}];
const initial={revision:'r1',enabled:false,values:{fov:90},fields,supportedVersions:['1.20.6','1.21.1']};
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
const reply=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
async function settle(){for(let i=0;i<12;i++){await Promise.resolve();await nextTick();}}
afterEach(()=>{app?.unmount();root?.remove();vi.unstubAllGlobals();});
async function mount(readFails=false,conflict=false){
 const fetcher=vi.fn(async(_url:string,init?:RequestInit)=>init?.method==='POST'?conflict?reply({error:{code:'OPTIONS_CHANGED',message:'设置已变化，请重新读取'}},409):reply({...initial,...JSON.parse(init?.body as string),revision:'r2'}):readFails?reply({error:{code:'OPTIONS_INVALID',message:'记录损坏，已保留'}},409):reply(initial));vi.stubGlobal('fetch',fetcher);
 root=document.createElement('div');document.body.append(root);app=createApp(GameOptions,{client:new LocalLauncherClient('a'.repeat(64))});app.mount(root);await settle();return fetcher;
}
it('切换兼容预览后明确展示实际支持结论而非装饰下拉',async()=>{await mount();const preview=root.querySelector<HTMLSelectElement>('[data-options-preview]')!;for(const version of ['1.20.6','1.21.1']){preview.value=version;preview.dispatchEvent(new Event('change'));await settle();expect(root.querySelector('[data-options-preview-result]')?.textContent).toContain('Minecraft '+version);expect(root.querySelector('[data-options-preview-result]')?.textContent).toContain('2 项均支持');}});
it('只保存明确选中字段，展示实际保存与兼容范围',async()=>{
 const fetcher=await mount();expect(root.textContent).toContain('1.20.6');expect(root.textContent).toContain('按键');
 const enabled=root.querySelector<HTMLInputElement>('[data-options-enabled]')!;enabled.checked=true;enabled.dispatchEvent(new Event('change'));
 const override=root.querySelector<HTMLInputElement>('[data-option-select="autoJump"]')!;override.checked=true;override.dispatchEvent(new Event('change'));await settle();
 const value=root.querySelector<HTMLInputElement>('[data-option-value="autoJump"]')!;value.checked=true;value.dispatchEvent(new Event('change'));await settle();
 root.querySelector<HTMLButtonElement>('[data-save-game-options]')!.click();await settle();
 expect(JSON.parse(fetcher.mock.calls.find(([,init])=>init?.method==='POST')![1]!.body as string)).toEqual({revision:'r1',enabled:true,values:{fov:90,autoJump:true}});
 expect(root.textContent).toContain('已保存');expect(root.textContent).toContain('下次启动');
});
it('读失败或修订冲突时禁止覆盖，重新读取后才可保存',async()=>{
 await mount(true);expect(root.textContent).toContain('记录损坏');expect(root.querySelector<HTMLButtonElement>('[data-save-game-options]')!.disabled).toBe(true);
 app?.unmount();root.remove();await mount(false,true);root.querySelector<HTMLButtonElement>('[data-save-game-options]')!.click();await settle();
 expect(root.textContent).toContain('设置已变化');expect(root.querySelector<HTMLButtonElement>('[data-save-game-options]')!.disabled).toBe(true);
 root.querySelector<HTMLButtonElement>('[data-read-game-options]')!.click();await settle();expect(root.querySelector<HTMLButtonElement>('[data-save-game-options]')!.disabled).toBe(false);
});
