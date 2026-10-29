import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {createApp,h,nextTick,ref} from 'vue';
import ResourceNavigation from '../ResourceNavigation.vue';
import FeaturePanel from '../FeaturePanel.vue';
import {LAUNCHER_PAGES,type LauncherPage} from '../model';
let app:ReturnType<typeof createApp>,root:HTMLDivElement;
const key='sqy.launcher.resources.expanded.v1';
function mount(){const page=ref<LauncherPage>('home'),navigate=vi.fn();root=document.createElement('div');document.body.append(root);app=createApp({render:()=>h(ResourceNavigation,{page:page.value,onNavigate:(value:LauncherPage)=>{page.value=value;navigate(value);}})});app.mount(root);return {page,navigate};}
beforeEach(()=>localStorage.clear());afterEach(()=>{app?.unmount();root?.remove();vi.restoreAllMocks();});
it('资源总入口收起当前子页时不跳页、不执行外部操作',async()=>{
 const request=vi.spyOn(window,'fetch');const {page,navigate}=mount();root.querySelector<HTMLButtonElement>('[data-launcher-page="mods"]')!.click();await nextTick();
 const toggle=root.querySelector<HTMLButtonElement>('[data-resource-toggle]')!;expect(toggle.getAttribute('aria-expanded')).toBe('true');toggle.click();await nextTick();
 expect(toggle.getAttribute('aria-expanded')).toBe('false');expect(page.value).toBe('mods');expect(toggle.textContent).toContain('模组中心');expect(root.querySelector('[data-launcher-page="mods"]')).toBeNull();expect(navigate).toHaveBeenCalledTimes(1);expect(request).not.toHaveBeenCalled();
 expect(root.querySelector('[data-launcher-page="settings"]')).not.toBeNull();
});
it('收起偏好重挂载仍保持，错误值不隐藏资源',async()=>{
 mount();root.querySelector<HTMLButtonElement>('[data-resource-toggle]')!.click();await nextTick();expect(localStorage.getItem(key)).toBe('0');app.unmount();root.remove();mount();expect(root.querySelector('[data-resource-toggle]')?.getAttribute('aria-expanded')).toBe('false');
 app.unmount();root.remove();localStorage.setItem(key,'损坏值');mount();expect(root.querySelector('[data-resource-toggle]')?.getAttribute('aria-expanded')).toBe('true');expect(root.querySelector('[role="status"]')?.textContent).toContain('无法读取');
});
it('偏好写入失败仍可临时收起并明确提示',async()=>{
 vi.spyOn(window,'localStorage','get').mockReturnValue({getItem:()=>null,setItem:()=>{throw new Error('quota');}} as unknown as Storage);mount();root.querySelector<HTMLButtonElement>('[data-resource-toggle]')!.click();await nextTick();expect(root.querySelector('[data-resource-toggle]')?.getAttribute('aria-expanded')).toBe('false');expect(root.querySelector('[role="status"]')?.textContent).toContain('未保存');
});
it('新增资源页明确待接入，不落入无关实例占位',()=>{
 root=document.createElement('div');document.body.append(root);app=createApp(FeaturePanel,{page:LAUNCHER_PAGES.find(p=>p.id==='resourcepacks')!,allowUpdaterPreview:false});app.mount(root);
 expect(root.textContent).toContain('资源包');expect(root.textContent).toContain('此资源类型尚未接入');expect(root.textContent).not.toContain('还没有游戏实例');expect(root.querySelector<HTMLButtonElement>('[data-feature-action]')?.disabled).toBe(true);
});
