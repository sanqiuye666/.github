import {afterEach,it,expect,vi} from 'vitest';
import {createApp,nextTick,reactive} from 'vue';
import WorkspaceSettings from '../WorkspaceSettings.vue';
import {defaultPreferences} from '../layout';
let app:ReturnType<typeof createApp>;let root:HTMLDivElement;
vi.mock('../../cottage/CottagePreview.vue',()=>({__esModule:true,default:{template:'<section role="dialog" aria-label="枫叶小屋独立演示"><button data-cottage-close @click="$emit(\'close\')">关闭</button></section>',emits:['close']}}));
afterEach(()=>{app?.unmount();root?.remove();});
it('主动预览小屋不会启用或替换现有开屏偏好，关闭返回原入口',async()=>{
 const p=reactive(defaultPreferences());p.introEnabled=false;root=document.createElement('div');document.body.append(root);app=createApp(WorkspaceSettings,{preferences:p,onApply:(value:typeof p)=>Object.assign(p,value)});app.mount(root);
 const button=root.querySelector<HTMLButtonElement>('[data-preview-cottage]');expect(button).not.toBeNull();button!.click();for(let i=0;i<20;i++){await Promise.resolve();await nextTick();}
 expect(root.querySelector('[aria-label="枫叶小屋独立演示"]')).not.toBeNull();expect(p.introEnabled).toBe(false);root.querySelector<HTMLButtonElement>('[data-cottage-close]')!.click();await nextTick();await nextTick();expect(root.querySelector('[aria-label="枫叶小屋独立演示"]')).toBeNull();expect(p.introEnabled).toBe(false);expect(document.activeElement).toBe(button);
});
it('独立切换界面主题，不替换当前布局或开屏设置',async()=>{const p=reactive(defaultPreferences());p.introEnabled=false;root=document.createElement('div');document.body.append(root);app=createApp(WorkspaceSettings,{preferences:p,onApply:(value:typeof p)=>Object.assign(p,value)});app.mount(root);const theme=root.querySelector<HTMLSelectElement>('[data-workspace-theme]');expect(theme).not.toBeNull();theme!.value='forest';theme!.dispatchEvent(new Event('change'));await nextTick();expect(p.theme).toBe('forest');expect(p.activeId).toBe('preset:workbench');expect(p.introEnabled).toBe(false);});
it('内置不可删除；复制、改名、满额保护和删除当前布局',async()=>{
 const p=reactive(defaultPreferences());root=document.createElement('div');document.body.append(root);app=createApp(WorkspaceSettings,{preferences:p,onApply:(value:typeof p)=>Object.assign(p,value)});app.mount(root);
 expect(root.querySelectorAll('[data-workspace-preset]')).toHaveLength(4);expect(root.querySelector('[data-delete-layout]')).toBeNull();
 root.querySelector<HTMLButtonElement>('[data-workspace-preset="cards"]')!.click();await nextTick();expect(p.activeId).toBe('preset:cards');
 for(let i=0;i<4;i++){root.querySelector<HTMLButtonElement>('[data-create-layout]')!.click();await nextTick();}
 expect(p.custom).toHaveLength(4);expect(root.querySelector<HTMLButtonElement>('[data-create-layout]')!.disabled).toBe(true);
 const input=root.querySelector<HTMLInputElement>('[aria-label^="重命名"]')!;input.value='新名称';input.dispatchEvent(new Event('change'));await nextTick();expect(p.custom[0].name).toBe('新名称');
 root.querySelector<HTMLButtonElement>('[data-delete-layout="'+p.activeId+'"]')!.click();await nextTick();expect(p.custom).toHaveLength(3);expect(p.activeId).toBe('preset:workbench');
});
