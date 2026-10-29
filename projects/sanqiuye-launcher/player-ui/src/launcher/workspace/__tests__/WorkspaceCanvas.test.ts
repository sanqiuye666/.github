import {createApp} from 'vue';
import {it,expect} from 'vitest';
import WorkspaceCanvas from '../WorkspaceCanvas.vue';
import {PRESETS,createPreset,REQUIRED_WIDGETS} from '../layout';
it('四种预设共用必需入口且不创建业务客户端',()=>{for(const p of PRESETS){const root=document.createElement('div');let page='';const app=createApp(WorkspaceCanvas,{layout:createPreset(p.id),local:true,instanceCount:2,selectedIdentityName:'Alice',onNavigate:(value:string)=>page=value});app.mount(root);for(const id of REQUIRED_WIDGETS)expect(root.querySelector('[data-widget="'+id+'"]')).not.toBeNull();expect(root.textContent).toContain('Alice');expect(root.textContent).toContain('2 个实例');root.querySelector<HTMLButtonElement>('[data-widget="launch"] button')!.click();expect(page).toBe('instances');app.unmount();}});
