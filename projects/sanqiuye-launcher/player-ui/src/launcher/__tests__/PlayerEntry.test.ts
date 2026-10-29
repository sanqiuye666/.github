import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp, nextTick } from 'vue';
const mocks = vi.hoisted(() => ({context:{native:true,embedded:false,pathname:'/',search:''},read:vi.fn(),show:vi.fn(),close:vi.fn(),legacy:vi.fn()}));
vi.mock('../entry', async importOriginal => ({...await importOriginal<object>(),currentEntryContext:()=>mocks.context,readPlayerEntryMode:mocks.read}));
vi.mock('../window',()=>({showPlayerWindow:mocks.show,closePlayerWindow:mocks.close}));
vi.mock('../../App.vue',async()=>{const {h,onMounted}=await import('vue');return {default:{setup(){onMounted(mocks.legacy);return()=>h('div',{'data-legacy':''},'原更新器');}}};});
import PlayerEntry from '../../PlayerEntry.vue';

let app:ReturnType<typeof createApp>|null=null;
let root:HTMLDivElement;
async function settle(){await vi.dynamicImportSettled();await nextTick();await new Promise(resolve=>setTimeout(resolve,0));await nextTick();}
function mount(){root=document.createElement('div');document.body.append(root);app=createApp(PlayerEntry);app.mount(root);}
beforeEach(()=>{vi.clearAllMocks();mocks.context.native=true;mocks.context.search='';mocks.show.mockResolvedValue(undefined);mocks.close.mockResolvedValue(undefined);mocks.read.mockResolvedValue('launcher');});
afterEach(()=>{app?.unmount();app=null;root?.remove();});
describe('player entry lifecycle',()=>{
  it('shows the standalone window without mounting the updater',async()=>{mount();await settle();expect(root.querySelector('[data-launcher-entry]')).not.toBeNull();expect(mocks.show).toHaveBeenCalledTimes(1);expect(mocks.legacy).not.toHaveBeenCalled();});
  it('mounts the existing updater exactly once and lets it show its own window',async()=>{mocks.read.mockResolvedValue('updater');mount();await settle();expect(root.querySelector('[data-legacy]')).not.toBeNull();expect(mocks.legacy).toHaveBeenCalledTimes(1);expect(mocks.show).not.toHaveBeenCalled();expect(root.querySelector('[data-preview-notice]')).toBeNull();});
  it('persistently labels direct browser updater previews as simulated data',async()=>{mocks.context.native=false;mocks.context.search='?updaterPreview=1';mount();await settle();expect(root.querySelector('[data-legacy]')).not.toBeNull();expect(root.querySelector('[data-preview-notice]')).not.toBeNull();expect(root.querySelector('[data-preview-notice]')?.textContent).toContain('更新器预览 · 演示数据');expect(mocks.read).not.toHaveBeenCalled();});
  it('shows a closable error when startup context cannot be read',async()=>{mocks.read.mockRejectedValue(new Error('启动参数无效'));mount();await settle();expect(root.textContent).toContain('启动参数无效');expect(mocks.show).toHaveBeenCalledTimes(1);(root.querySelector('[data-entry-close]') as HTMLButtonElement).click();expect(mocks.close).toHaveBeenCalledTimes(1);expect(mocks.legacy).not.toHaveBeenCalled();});
  it('does not mount an updater or show a window after the entry is unmounted',async()=>{let finish!:(value:string)=>void;mocks.read.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;}));mount();app!.unmount();app=null;finish('updater');await settle();expect(mocks.legacy).not.toHaveBeenCalled();expect(mocks.show).not.toHaveBeenCalled();});
});
