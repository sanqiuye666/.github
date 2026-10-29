import {afterEach,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import CottagePreview from '../cottage/CottagePreview.vue';
const mock=vi.hoisted(()=>({disposed:vi.fn(),loaded:vi.fn()}));
vi.mock('skinview3d',()=>({SkinViewer:class{controls={enableRotate:true,enableZoom:true,enablePan:true};playerObject={rotation:{y:0}};animation:any;renderPaused=true;loadSkin=mock.loaded;dispose=mock.disposed;render(){}},WalkingAnimation:class{paused=true;update(){}}}));
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
async function settle(){for(let i=0;i<20;i++){await Promise.resolve();await nextTick();}}
function mount(){root=document.createElement('div');root.className='launcher-shell';document.body.append(root);app=createApp(CottagePreview,{onClose:()=>{app?.unmount();app=undefined;}});app.mount(root);}
afterEach(()=>{app?.unmount();root?.remove();vi.restoreAllMocks();vi.unstubAllGlobals();mock.disposed.mockClear();mock.loaded.mockClear();});
it('只是主动演示，明确示例皮肤/模拟完成；关闭清理画布和帧回调',async()=>{
 const cancel=vi.fn();vi.stubGlobal('requestAnimationFrame',vi.fn(()=>19));vi.stubGlobal('cancelAnimationFrame',cancel);vi.stubGlobal('matchMedia',vi.fn(()=>({matches:false,addEventListener(){},removeEventListener(){}})));
 vi.spyOn(HTMLCanvasElement.prototype,'getContext').mockReturnValue({fillRect(){},fillStyle:''} as any);mount();await settle();
 expect(root.textContent).toContain('示例皮肤');expect(root.textContent).toContain('模拟加载完成');expect(root.querySelector('[data-cottage-stage]')?.getAttribute('data-phase')).toBe('loading');
 root.querySelector<HTMLButtonElement>('[data-cottage-ready]')!.click();await settle();expect(root.textContent).toContain('已停止发叶');
 root.querySelector<HTMLButtonElement>('[data-cottage-close]')!.click();await settle();expect(mock.disposed).toHaveBeenCalledOnce();expect(cancel).toHaveBeenCalledWith(19);
});
it('回家结束后停止排帧，重新演示才恢复',async()=>{
 let pending:FrameRequestCallback|undefined;const schedule=vi.fn((cb:FrameRequestCallback)=>{pending=cb;return 19;});vi.stubGlobal('requestAnimationFrame',schedule);vi.stubGlobal('cancelAnimationFrame',vi.fn());vi.stubGlobal('matchMedia',vi.fn(()=>({matches:false,addEventListener(){},removeEventListener(){}})));
 vi.spyOn(HTMLCanvasElement.prototype,'getContext').mockReturnValue({fillRect(){},fillStyle:''} as any);mount();await settle();root.querySelector<HTMLButtonElement>('[data-cottage-ready]')!.click();await settle();
 for(let i=0;i<120&&pending;i++){const cb=pending;pending=undefined;cb(i*80);}await settle();expect(root.querySelector('[data-cottage-stage]')?.getAttribute('data-phase')).toBe('entered');expect(pending).toBeUndefined();
 root.querySelector<HTMLButtonElement>('[data-cottage-replay]')!.click();await settle();expect(root.querySelector('[data-cottage-stage]')?.getAttribute('data-phase')).toBe('loading');expect(pending).toBeTypeOf('function');
});
it('完成按钮禁用后焦点回到页面，Esc仍只关闭演示并移除键盘监听',async()=>{
 vi.stubGlobal('requestAnimationFrame',vi.fn(()=>19));vi.stubGlobal('cancelAnimationFrame',vi.fn());vi.stubGlobal('matchMedia',vi.fn(()=>({matches:false,addEventListener(){},removeEventListener(){}})));
 vi.spyOn(HTMLCanvasElement.prototype,'getContext').mockReturnValue({fillRect(){},fillStyle:''} as any);mount();await settle();
 const ready=root.querySelector<HTMLButtonElement>('[data-cottage-ready]')!;ready.focus();ready.click();await settle();expect(ready.disabled).toBe(true);
 // 原生WebView禁用已聚焦按钮后，键盘事件的目标变为body。
 const escape=new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true});document.body.dispatchEvent(escape);await settle();
 expect(root.querySelector('.cottage-preview')).toBeNull();expect(escape.defaultPrevented).toBe(true);
 const afterClose=new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true});document.body.dispatchEvent(afterClose);expect(afterClose.defaultPrevented).toBe(false);
});
