import {createApp,nextTick} from 'vue';
import {afterEach,expect,it,vi} from 'vitest';
const state=vi.hoisted(()=>({viewer:null as null|{animation:{paused:boolean}|null;renderPaused:boolean;dispose:()=>void},disposed:0}));
vi.mock('skinview3d',()=>({SkinViewer:class {animation=null;renderPaused=false;constructor(){state.viewer=this;}loadSkin(){}dispose(){state.disposed++;}},WalkingAnimation:class {paused=false;}}));
import SkinEditor from '../tools/SkinEditor.vue';
let app:ReturnType<typeof createApp>,root:HTMLDivElement;
afterEach(()=>{app?.unmount();root?.remove();vi.restoreAllMocks();vi.unstubAllGlobals();});
it('异步初始化遵守已取消动画，隐藏暂停并在卸载时清理',async()=>{state.disposed=0;vi.spyOn(HTMLCanvasElement.prototype,'getContext').mockReturnValue({putImageData(){}} as never);vi.stubGlobal('ImageData',class {constructor(..._a:unknown[]){}});root=document.createElement('div');document.body.append(root);app=createApp(SkinEditor);app.mount(root);const toggle=[...root.querySelectorAll<HTMLInputElement>('input[type=checkbox]')][0]!;toggle.checked=false;toggle.dispatchEvent(new Event('change'));for(let i=0;i<20;i++){await Promise.resolve();await nextTick();}expect(state.viewer?.animation?.paused).toBe(true);vi.spyOn(document,'hidden','get').mockReturnValue(true);document.dispatchEvent(new Event('visibilitychange'));expect(state.viewer?.renderPaused).toBe(true);app.unmount();expect(state.disposed).toBe(1);vi.spyOn(document,'hidden','get').mockReturnValue(false);document.dispatchEvent(new Event('visibilitychange'));expect(state.viewer?.renderPaused).toBe(true);});
