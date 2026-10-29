import {afterEach,describe,expect,it,vi} from 'vitest';
import {createApp} from 'vue';
import InstanceCenter from '../InstanceCenter.vue';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
afterEach(()=>{app?.unmount();root?.remove();});
describe('instance records',()=>{
  it('shows damaged records and escapes names without claiming installation',()=>{
    root=document.createElement('div');document.body.append(root);
    app=createApp(InstanceCenter,{listing:{instances:[{id:'fixture',name:'<img src=x onerror=alert(1)>',gameVersion:'1.0',loader:'VANILLA',state:'PREPARED',createdAt:'2026-10-21T00:00:00Z',installation:null}],problems:[{directoryId:'broken',reason:'损坏记录'}]},loading:false,error:''});app.mount(root);
    expect(root.textContent).toContain('<img src=x onerror=alert(1)>');expect(root.querySelector('img')).toBeNull();expect(root.textContent).toContain('损坏记录');
    expect(root.textContent).toContain('尚未安装');expect(root.textContent).not.toContain('游戏已启动');
  });
  it('prevents repeated refresh while busy',()=>{
    root=document.createElement('div');document.body.append(root);const refresh=vi.fn();
    app=createApp(InstanceCenter,{listing:{instances:[],problems:[]},loading:true,error:'',onRefresh:refresh});app.mount(root);
    const button=root.querySelector<HTMLButtonElement>('[data-refresh-instances]')!;expect(button.disabled).toBe(true);button.click();expect(refresh).not.toHaveBeenCalled();
  });
});
