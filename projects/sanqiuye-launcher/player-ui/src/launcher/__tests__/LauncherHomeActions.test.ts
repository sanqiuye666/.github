import {afterEach,it,expect,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import LauncherHome from '../LauncherHome.vue';
import {createPreset,PRESETS} from '../workspace/layout';
let app:ReturnType<typeof createApp>,root:HTMLDivElement;
afterEach(()=>{app?.unmount();root?.remove();});
it.each(PRESETS.map(p=>[p.id] as const))('预设%s只有一个启动主动作，背景存在时主动作在主卡片内',async id=>{root=document.createElement('div');document.body.append(root);const navigate=vi.fn(),layout=createPreset(id);app=createApp(LauncherHome,{layout,onNavigate:navigate});app.mount(root);await nextTick();expect(root.querySelectorAll('.launcher-primary')).toHaveLength(1);if(['workbench','cards'].includes(id))expect(root.querySelector('.launcher-hero .launcher-primary')).not.toBeNull();else expect(root.querySelector('.launcher-hero')).toBeNull();root.querySelector<HTMLButtonElement>('.launcher-primary')!.click();expect(navigate).toHaveBeenCalledWith('instances');});
it('关闭背景不丢失唯一启动入口',async()=>{root=document.createElement('div');document.body.append(root);const layout=createPreset('workbench');layout.appearance.showBackground=false;app=createApp(LauncherHome,{layout});app.mount(root);await nextTick();expect(root.querySelector('.launcher-hero')).toBeNull();expect(root.querySelectorAll('.launcher-primary')).toHaveLength(1);});
