import {afterEach,describe,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import DownloadTasks from '../DownloadTasks.vue';
import {LocalLauncherClient} from '../local-api';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
const reply=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
async function settle(){for(let i=0;i<10;i++){await Promise.resolve();await nextTick();}}
function mount(){root=document.createElement('div');document.body.append(root);app=createApp(DownloadTasks,{client:new LocalLauncherClient('a'.repeat(64))});app.mount(root);}
afterEach(()=>{app?.unmount();root?.remove();vi.useRealTimers();vi.unstubAllGlobals();});
describe('download and verification tasks',()=>{
 it('cancellation stays cancelled and retries use the task ID',async()=>{
  let state='DOWNLOADING';const task=()=>({id:'fixture',kind:'VANILLA_INSTALL',state,inputs:{instanceId:'instance',versionId:'1.0'},createdAt:'',updatedAt:'',downloadedBytes:10,totalBytes:100,completedFiles:0,totalFiles:1,message:null,errorCode:null,retryOf:null});
  const calls=vi.fn(async(url:string)=>{if(url.endsWith('/cancel')){state='CANCELLED';return reply({task:task()});}if(url.endsWith('/retry'))return reply({task:{...task(),id:'new-task',state:'QUEUED'}});return reply({tasks:[task()],problems:[],total:1});});vi.stubGlobal('fetch',calls);
  mount();await settle();root.querySelector<HTMLButtonElement>('[data-cancel-task]')!.click();await settle();expect(root.textContent).toContain('已取消');expect(root.textContent).not.toContain('任务已完成');root.querySelector<HTMLButtonElement>('[data-retry-task]')!.click();await settle();expect(calls.mock.calls.some(([url])=>url.endsWith('/tasks/fixture/retry'))).toBe(true);
 });
 it('stops polling after a 403 session failure',async()=>{
  vi.useFakeTimers();const calls=vi.fn(async()=>reply({error:{code:'SESSION_REQUIRED',message:'expired'}},403));vi.stubGlobal('fetch',calls);mount();await settle();await vi.advanceTimersByTimeAsync(10000);await settle();expect(calls).toHaveBeenCalledTimes(1);expect(root.textContent).toContain('expired');
 });
});
