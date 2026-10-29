import {afterEach,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';import InstanceModDialog from '../InstanceModDialog.vue';import type {LocalLauncherClient} from '../local-api';
let app:ReturnType<typeof createApp>,root:HTMLDivElement;afterEach(()=>{app?.unmount();root?.remove();});async function settle(){for(let i=0;i<15;i++){await Promise.resolve();await nextTick();}}
const instance={id:'instance',name:'秋日世界',gameVersion:'1.20.6',loader:'FABRIC',installation:{},createdAt:'',state:'PREPARED'};
const plan={planId:'fixed',instanceId:'instance',gameVersion:'1.20.6',loader:'fabric',removal:false,files:[{projectId:'api',fileName:'api.jar',size:10,digest:'sha',source:'MODRINTH',versionId:'v1'}],totalBytes:10,warnings:[],expiresAt:new Date(Date.now()+600000).toISOString()};
it('预览后才可确认，确认只发送一次计划ID',async()=>{
 const preview=vi.fn(async()=>plan),apply=vi.fn((_id:string,_signal?:AbortSignal)=>new Promise(()=>{}));const client={planInstanceMods:preview,applyInstanceMods:apply} as unknown as LocalLauncherClient;
 root=document.createElement('div');document.body.append(root);app=createApp(InstanceModDialog,{client,instance:instance as never,operation:'FABRIC_API'});app.mount(root);await settle();expect(preview).not.toHaveBeenCalled();expect(root.querySelector('[data-confirm-instance-mods]')).toBeNull();
 root.querySelector<HTMLButtonElement>('[data-preview-instance-mods]')!.click();await settle();expect(root.textContent).toContain('api.jar');expect(apply).not.toHaveBeenCalled();root.querySelector<HTMLButtonElement>('[data-confirm-instance-mods]')!.click();root.querySelector<HTMLButtonElement>('[data-confirm-instance-mods]')!.click();await settle();expect(apply).toHaveBeenCalledTimes(1);expect(apply.mock.calls[0]![0]).toBe('fixed');
});
it('切换目标立即废弃旧计划，延迟结果不能覆盖新目标',async()=>{
 let resolve!:(value:unknown)=>void;const preview=vi.fn(()=>new Promise(r=>resolve=r));const client={instances:vi.fn(async()=>({instances:[instance,{...instance,id:'other',name:'另一世界'}],problems:[]})),planInstanceMods:preview} as unknown as LocalLauncherClient;
 root=document.createElement('div');document.body.append(root);app=createApp(InstanceModDialog,{client,refValue:{source:'MODRINTH',projectId:'p',versionId:'v',fileKey:'a'.repeat(64)}});app.mount(root);await settle();root.querySelector<HTMLButtonElement>('[data-preview-instance-mods]')!.click();await settle();
 const select=root.querySelector<HTMLSelectElement>('[data-mod-target]')!;select.value='other';select.dispatchEvent(new Event('change'));await settle();resolve(plan);await settle();expect(root.querySelector('[data-confirm-instance-mods]')).toBeNull();expect(root.textContent).not.toContain('api.jar');
});
