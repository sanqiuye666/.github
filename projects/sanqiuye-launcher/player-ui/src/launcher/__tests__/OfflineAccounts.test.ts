import {afterEach,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import OfflineAccounts from '../OfflineAccounts.vue';
import {LocalLauncherClient} from '../local-api';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
const reply=(body:unknown)=>new Response(JSON.stringify(body),{headers:{'Content-Type':'application/json'}});
async function settle(){for(let i=0;i<10;i++){await Promise.resolve();await nextTick();}}
afterEach(()=>{app?.unmount();root?.remove();vi.restoreAllMocks();vi.unstubAllGlobals();});
it('switches temporary identities without writing browser storage or pretending to authenticate',async()=>{
 const storage=vi.spyOn(Storage.prototype,'setItem');let selected='alice';
 const fetcher=vi.fn(async(url:string,init?:RequestInit)=>{if(url.endsWith('/bob/select'))selected='bob';return reply({identities:[{id:'alice',name:'Alice',kind:'OFFLINE'},{id:'bob',name:'Bob',kind:'OFFLINE'}],selectedId:selected,temporary:true,message:'临时离线身份，本次会话有效'});});vi.stubGlobal('fetch',fetcher);
 root=document.createElement('div');document.body.append(root);app=createApp(OfflineAccounts,{client:new LocalLauncherClient('a'.repeat(64))});app.mount(root);await settle();
 expect(root.textContent).toContain('临时离线身份，本次会话有效');expect(root.textContent).toContain('仅影响下次启动');
 root.querySelector<HTMLButtonElement>('[data-select-identity="bob"]')!.click();await settle();expect(root.querySelector('[data-selected-identity]')?.textContent).toContain('Bob');
 const name=root.querySelector<HTMLInputElement>('[data-offline-name]')!;name.value='中文';name.dispatchEvent(new Event('input'));root.querySelector('form')!.dispatchEvent(new Event('submit',{cancelable:true}));await settle();
 expect(fetcher.mock.calls.filter(([,init])=>init?.method==='POST')).toHaveLength(1);expect(storage).not.toHaveBeenCalled();expect(root.textContent).not.toContain('微软已认证');
});
