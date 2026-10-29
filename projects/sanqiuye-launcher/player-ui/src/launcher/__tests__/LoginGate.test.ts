import {afterEach,describe,expect,it,vi} from 'vitest';
import {createApp,nextTick} from 'vue';
import LoginGate from '../LoginGate.vue';
let app:ReturnType<typeof createApp>|null=null;let root:HTMLDivElement;
afterEach(()=>{app?.unmount();root?.remove();vi.restoreAllMocks();});
describe('launcher login boundary',()=>{
  it('shows a local core startup error and offers a retry without submitting credentials',async()=>{
    const retry=vi.fn();root=document.createElement('div');document.body.append(root);app=createApp(LoginGate,{localError:'随包Java缺失',onRetry:retry});app.mount(root);
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('随包Java缺失');
    root.querySelector<HTMLButtonElement>('[data-retry-local]')!.click();await nextTick();expect(retry).toHaveBeenCalledTimes(1);
  });
  it('does not pretend that a panel login is available or store a typed password',async()=>{
    const request=vi.spyOn(globalThis,'fetch');const storage=vi.spyOn(Storage.prototype,'setItem');
    root=document.createElement('div');document.body.append(root);app=createApp(LoginGate);app.mount(root);
    const username=root.querySelector<HTMLInputElement>('[name="username"]')!,password=root.querySelector<HTMLInputElement>('[name="password"]')!;
    username.value='player';username.dispatchEvent(new Event('input'));password.value='do-not-store';password.dispatchEvent(new Event('input'));
    root.querySelector('form')!.dispatchEvent(new Event('submit',{cancelable:true}));await nextTick();
    expect(root.textContent).toContain('尚未连接调度中心');expect(password.value).toBe('');expect(request).not.toHaveBeenCalled();expect(storage).not.toHaveBeenCalled();
  });
  it('labels Microsoft authorization separately and only emits a preview event',async()=>{
    const preview=vi.fn();root=document.createElement('div');document.body.append(root);app=createApp(LoginGate,{onPreview:preview});app.mount(root);
    expect(root.textContent).toContain('微软游戏账号');(root.querySelector('[data-enter-preview]') as HTMLButtonElement).click();await nextTick();expect(preview).toHaveBeenCalledTimes(1);
  });
});
