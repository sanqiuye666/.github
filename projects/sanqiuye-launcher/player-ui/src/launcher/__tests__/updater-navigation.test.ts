import {afterEach,describe,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
const mocks=vi.hoisted(()=>({native:false,mounted:vi.fn()}));
vi.mock('../entry',async importOriginal=>({...await importOriginal<object>(),currentEntryContext:()=>({native:mocks.native,embedded:false,pathname:'/',search:''}),readPlayerEntryMode:async()=> 'launcher'}));
vi.mock('../window',()=>({showPlayerWindow:vi.fn(),minimizePlayerWindow:vi.fn(),togglePlayerMaximize:vi.fn(),closePlayerWindow:vi.fn()}));
vi.mock('../../App.vue',async()=>{const {h,onMounted}=await import('vue');return {default:{setup(){onMounted(mocks.mounted);return()=>h('div',{'data-existing-updater':''},'原更新器与本地管理');}}};});
import PlayerEntry from '../../PlayerEntry.vue';
let app:ReturnType<typeof createApp>|null=null;let root:HTMLDivElement;
async function settle(){await vi.dynamicImportSettled();await new Promise(resolve=>setTimeout(resolve,0));await nextTick();}
afterEach(()=>{app?.unmount();root?.remove();mocks.native=false;vi.clearAllMocks();});
async function open(){root=document.createElement('div');document.body.append(root);app=createApp(PlayerEntry);app.mount(root);await settle();(root.querySelector('[data-enter-preview]') as HTMLButtonElement).click();await nextTick();(root.querySelector('[data-launcher-page="updates"]') as HTMLButtonElement).click();await nextTick();}
describe('legacy updater integration',()=>{
  it('keeps one actual updater component instance across browser preview navigation',async()=>{
    await open();
    for(let i=0;i<2;i++){(root.querySelector('[data-open-updater]') as HTMLButtonElement).click();await settle();expect(root.querySelector('[data-existing-updater]')).not.toBeNull();expect(root.textContent).toContain('更新器预览');(root.querySelector('[data-return-launcher]') as HTMLButtonElement).click();await nextTick();}
    expect(mocks.mounted).toHaveBeenCalledTimes(1);
  });
  it('does not start a preview sidecar from a native launcher without an instance',async()=>{
    mocks.native=true;await open();expect(root.textContent).toContain('尚未选择受管实例');expect(root.querySelector('[data-open-updater]')).toBeNull();expect(mocks.mounted).not.toHaveBeenCalled();
  });
});
