import {afterEach,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import RuntimeSettings from '../RuntimeSettings.vue';
import {LocalLauncherClient} from '../local-api';
const instance={id:'instance',name:'隔离实例',gameVersion:'1.21.1',loader:'VANILLA',createdAt:'',state:'PREPARED' as const,installation:{installationId:'installed',versionId:'1.21.1',installedAt:'',fileCount:3,requiredJavaMajor:21,platform:{osName:'windows',arch:'amd64',bits:64}}};
const settings={javaId:null,initialMemoryMiB:256,maximumMemoryMiB:2048,width:960,height:540,extraJvmArgs:[],acceptUnknownJava:false};
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
const reply=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
async function settle(){for(let i=0;i<10;i++){await Promise.resolve();await nextTick();}}
afterEach(()=>{app?.unmount();root?.remove();vi.unstubAllGlobals();});
async function mount(major=21,bits=32,missing=false){
 const fetcher=vi.fn(async(url:string,init?:RequestInit)=>reply(init?.method==='POST'?{error:{code:'SETTINGS_CHANGED',message:'设置已更新，请重新读取'}}:url.endsWith('/runtimes')?{runtimes:missing?[]:[{id:'java',majorVersion:major,bits,vendor:'测试 Java'}],diagnostics:[],platformBits:64}:{revision:'revision',settings:missing?{...settings,javaId:'old-java'}:settings},init?.method==='POST'?409:200));vi.stubGlobal('fetch',fetcher);
 root=document.createElement('div');document.body.append(root);app=createApp(RuntimeSettings,{client:new LocalLauncherClient('a'.repeat(64)),instances:[instance]});app.mount(root);await settle();return fetcher;
}
function input(selector:string,value:string,event='input'){const element=root.querySelector<HTMLInputElement>(selector)!;element.value=value;element.dispatchEvent(new Event(event));}
it('rejects incompatible bits and memory bounds, then preserves an expired settings revision',async()=>{
 const fetcher=await mount();expect(root.textContent).toContain('Java 21');expect(fetcher.mock.calls.every(([,init])=>init?.method==='GET')).toBe(true);
 input('[data-java-selection]','java','change');await settle();expect(root.textContent).toContain('位数');expect(root.querySelector<HTMLButtonElement>('[data-save-launch-settings]')!.disabled).toBe(true);
 input('[data-java-selection]','','change');input('[data-maximum-memory]','255');await settle();expect(root.querySelector<HTMLButtonElement>('[data-save-launch-settings]')!.disabled).toBe(true);
 input('[data-maximum-memory]','512');await settle();root.querySelector<HTMLButtonElement>('[data-save-launch-settings]')!.click();await settle();expect(root.textContent).toContain('设置已更新');expect(root.querySelector<HTMLButtonElement>('[data-save-launch-settings]')!.disabled).toBe(true);expect(fetcher.mock.calls.filter(([,init])=>init?.method==='POST')).toHaveLength(1);
});
it('requires explicit acknowledgement before using a newer Java with unknown compatibility',async()=>{
 await mount(25,64);input('[data-java-selection]','java','change');await settle();expect(root.textContent).toContain('兼容性');expect(root.querySelector<HTMLButtonElement>('[data-save-launch-settings]')!.disabled).toBe(true);
 const accept=root.querySelector<HTMLInputElement>('[data-accept-unknown-java]')!;accept.checked=true;accept.dispatchEvent(new Event('change'));await settle();expect(root.querySelector<HTMLButtonElement>('[data-save-launch-settings]')!.disabled).toBe(false);
});
it('shows a saved Java selection that has not been discovered in this backend session',async()=>{
 await mount(21,64,true);expect(root.textContent).toContain('上次选择的 Java 尚未检测');expect(root.querySelector<HTMLButtonElement>('[data-save-launch-settings]')!.disabled).toBe(true);
});
