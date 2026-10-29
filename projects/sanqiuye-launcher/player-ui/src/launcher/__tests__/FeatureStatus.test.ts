import {afterEach,describe,expect,it} from 'vitest';
import {createApp,nextTick} from 'vue';
import {featureStatus} from '../feature-status';
import FeatureStatus from '../FeatureStatus.vue';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement|undefined;
afterEach(()=>{app?.unmount();root?.remove();});
const capabilities={catalog:'AVAILABLE',instances:'AVAILABLE',vanilla:'AVAILABLE',mods:'AVAILABLE',runtimes:'AVAILABLE',identities:'AVAILABLE',launch:'AVAILABLE'} as const;
describe('真实功能状态',()=>{
 it('预览与未检测网络不冒充游戏可运行或全部正常',()=>{
  const rows=featureStatus({local:false,capabilities:{}});
  expect(rows.find(r=>r.id==='workspace')?.state).toBe('available');
  expect(rows.find(r=>r.id==='launch')?.state).toBe('unchecked');
  expect(rows.find(r=>r.id==='microsoft')?.state).toBe('pending');
  const connected=featureStatus({local:true,capabilities});
  expect(connected.find(r=>r.id==='launch')?.state).toBe('unchecked');
  expect(connected.find(r=>r.id==='catalog')?.state).toBe('unchecked');
  expect(connected.find(r=>r.id==='curseforge')?.detail).toContain('密钥');
  expect(connected.find(r=>r.id==='loaders')?.state).toBe('pending');
 });
 it('实例读取错误仅影响该功能，不把未开发的皮肤标为异常',()=>{
  const rows=featureStatus({local:true,capabilities,instanceError:'读取失败'});
  expect(rows.find(r=>r.id==='instances')?.state).toBe('error');
  expect(rows.find(r=>r.id==='skins')?.state).toBe('partial');expect(rows.find(r=>r.id==='skins')?.detail).toContain('账号换肤');
  expect(featureStatus({local:true,capabilities:{instances:'UNAVAILABLE'}}).find(r=>r.id==='instances')?.state).toBe('error');
 });
 it('只读展开全部类别，退出关闭后恢复焦点',async()=>{
  root=document.createElement('div');document.body.append(root);app=createApp(FeatureStatus,{local:true,capabilities});app.mount(root);
  const open=root.querySelector<HTMLButtonElement>('[data-feature-status-open]')!;open.focus();open.click();await nextTick();
  expect(root.querySelector('[role="dialog"]')).not.toBeNull();
  for(const value of ['首页与工作台','版本与实例','账号与皮肤','社区与资源','联机与扩展','管理与交付'])expect(root.textContent).toContain(value);
  expect(root.textContent).toContain('未检测');expect(root.textContent).toContain('未接入');expect(root.textContent).not.toContain('全部系统运行正常');
  const dialog=root.querySelector('[role="dialog"]')!;
  dialog.dispatchEvent(new KeyboardEvent('keydown',{key:'Tab',bubbles:true}));
  dialog.dispatchEvent(new KeyboardEvent('keydown',{key:'Tab',bubbles:true}));
  expect(document.activeElement).toBe(root.querySelector('.feature-status-list'));
  dialog.dispatchEvent(new KeyboardEvent('keydown',{key:'Tab',shiftKey:true,bubbles:true}));
  expect(document.activeElement?.getAttribute('aria-label')).toBe('关闭功能运行情况');
  root.querySelector('[role="dialog"]')!.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));await nextTick();
  expect(root.querySelector('[role="dialog"]')).toBeNull();expect(document.activeElement).toBe(open);
 });
});

it('已配置真实账号不能冒充完整云端和游戏状态都已接通',()=>{const row=featureStatus({local:true,capabilities:{server:'LIVE'}}).find(r=>r.id==='cloud');expect(row?.state).toBe('partial');expect(row?.detail).toContain('账号');expect(row?.detail).toContain('待接入');});
