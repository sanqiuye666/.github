import {afterEach,expect,it,vi} from 'vitest';import {createApp,h,nextTick,ref} from 'vue';
import InstanceMods from '../InstanceMods.vue';import type {LocalLauncherClient} from '../local-api';
let app:ReturnType<typeof createApp>,root:HTMLDivElement;afterEach(()=>{app?.unmount();root?.remove();});async function settle(){for(let i=0;i<10;i++){await Promise.resolve();await nextTick();}}
it('任务列表触发连续刷新时串行合并读取，避免取消中的读取与新读取争同一实例锁',async()=>{
 let finish!:(value:unknown)=>void;const listing={instanceId:'one',gameVersion:'1.20.6',loader:'fabric',managedLoader:'fabric',entries:[],manualFiles:0,directory:'test',fingerprint:'a',compatible:true};
 const read=vi.fn((_id:string,_signal?:AbortSignal)=>new Promise(r=>finish=r));const client={instanceMods:read} as unknown as LocalLauncherClient;const revision=ref(0);const instance={id:'one',name:'世界',gameVersion:'1.20.6',loader:'FABRIC'};
 root=document.createElement('div');document.body.append(root);app=createApp({render:()=>h(InstanceMods,{client,instance:instance as never,revision:revision.value})});app.mount(root);await settle();expect(read).toHaveBeenCalledTimes(1);
 revision.value++;await settle();revision.value++;await settle();expect(read).toHaveBeenCalledTimes(1);expect(read.mock.calls[0]![1]?.aborted).toBe(false);
 const first=finish;first(listing);await settle();expect(read).toHaveBeenCalledTimes(2);finish(listing);await settle();expect(root.querySelector('[data-install-fabric-api]')).not.toBeNull();expect(read).toHaveBeenCalledTimes(2);
});
