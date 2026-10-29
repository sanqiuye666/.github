<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,onMounted,reactive,ref,watch} from 'vue';
import AppIcon from '../components/AppIcon.vue';
import {currentEntryContext} from './entry';
import {createLauncherSession,LAUNCHER_PAGES,type LauncherPage} from './model';
import LauncherTitleBar from './LauncherTitleBar.vue';
import ResourceNavigation from './ResourceNavigation.vue';
import ResourceManager from './ResourceManager.vue';
import PlayerServers from './PlayerServers.vue';
import type {PlayerServerTarget} from './resource-types';
import LoginGate from './LoginGate.vue';
import LauncherHome from './LauncherHome.vue';
import FeaturePanel from './FeaturePanel.vue';
import FeatureStatus from './FeatureStatus.vue';
import VersionCenter from './VersionCenter.vue';
import InstanceCenter from './InstanceCenter.vue';
import InstallationDialog from './InstallationDialog.vue';
import ModCenter from './ModCenter.vue';
import PackCenter from './PackCenter.vue';
import OfflineAccounts from './OfflineAccounts.vue';
import RuntimeSettings from './RuntimeSettings.vue';
import GameOptions from './GameOptions.vue';
import CreativeTools from './tools/CreativeTools.vue';
import ServerWorkspace from './server/ServerWorkspace.vue';
import CloudAccount from './server/CloudAccount.vue';
import type {IdentityListing} from './launch-types';
import {operationMessage} from './local-api';
import {useLocalLauncher} from './useLocalLauncher';
import './launcher.css';
import './workspace/workspace.css';
import './launcher-theme.css';
import './launcher-glass.css';
import StartupIntro from './workspace/StartupIntro.vue';
import FirstRunGuide from './workspace/FirstRunGuide.vue';
import MaidGuide from './workspace/MaidGuide.vue';
import type {GuideStep} from './workspace/guide';
import WorkspaceEditor from './workspace/WorkspaceEditor.vue';
import type {WorkspaceLayout} from './workspace/layout';
import WorkspaceSettings from './workspace/WorkspaceSettings.vue';
import WorkspaceAppearance from './workspace/WorkspaceAppearance.vue';
import {PRESETS,activeLayout,defaultPreferences,type WorkspacePreferences} from './workspace/layout';
import {loadPreferences,savePreferences} from './workspace/preferences';
let loaded;try{loaded=loadPreferences(window.localStorage);}catch{loaded={value:defaultPreferences(),warning:'本机外观存储不可用。'};}
const directTarget=ref<PlayerServerTarget|undefined>(),directInstanceId=ref('');
const resourceKinds={resourcepacks:'RESOURCEPACKS',shaders:'SHADERS',recordings:'RECORDINGS',schematics:'SCHEMATICS'} as const;
const preferences=ref(loaded.value),workspaceWarning=ref(loaded.warning);
const editingLayout=ref<WorkspaceLayout|null>(null);
const appearanceOpen=ref(false),appearanceTrigger=ref<HTMLButtonElement>();
function closeAppearance(){appearanceOpen.value=false;void nextTick(()=>appearanceTrigger.value?.focus());}
const workspace=computed(()=>activeLayout(preferences.value));
function applyWorkspace(value:WorkspacePreferences){try{savePreferences(window.localStorage,value);preferences.value=value;workspaceWarning.value=null;editingLayout.value=null;}catch(e){workspaceWarning.value=e instanceof Error?e.message:'工作台未保存。';}}
const guideOpen=ref(false),guideStep=ref<GuideStep>('identity'),maidCollapsed=ref(false),guideCelebrated=ref(false);
function showHelp(){guideStep.value=selectedIdentity.value?'java':'identity';guideOpen.value=true;}
function finishGuide(complete=false){guideOpen.value=false;guideCelebrated.value=complete;applyWorkspace({...preferences.value,guideCompleted:true});}
const emit=defineEmits<{'open-updater-preview':[]}>();
const session=reactive(createLauncherSession()),page=ref<LauncherPage>('home'),notice=ref('');
const definition=computed(()=>LAUNCHER_PAGES.find(item=>item.id===page.value)!);
const installationId=ref(''),taskRevision=ref(0);
const launchInstanceId=ref(''),identityListing=ref<IdentityListing>({identities:[],selectedId:null,temporary:true});
const selectedIdentity=computed(()=>identityListing.value.identities.find(value=>value.id===identityListing.value.selectedId));
const canUseIdentities=computed(()=>local.session.value?.capabilities.identities==='AVAILABLE');
const canUseRuntime=computed(()=>local.session.value?.capabilities.runtimes==='AVAILABLE'&&local.session.value?.capabilities.launch==='AVAILABLE');
const canLaunch=computed(()=>canUseIdentities.value&&canUseRuntime.value);
const allowUpdaterPreview=!currentEntryContext().native;
const local=useLocalLauncher(()=>{session.mode='login';page.value='home';notice.value='本机会话已失效，请重新连接。';});
function enterLocal(){if(!local.client.value)return;session.mode='local';page.value='home';void local.refreshInstances();if(canUseIdentities.value)void local.client.value.identities().then(value=>{if(session.mode==='local')identityListing.value=value;}).catch(e=>{notice.value=operationMessage(e);});}
function leavePreview(){session.mode='login';page.value='home';notice.value='';identityListing.value={identities:[],selectedId:null,temporary:true};launchInstanceId.value='';local.reconnect();}
watch(()=>local.available.value,available=>{if(available&&currentEntryContext().native&&session.mode==='login')enterLocal();});
let nativeNoticeOff:(()=>void)|undefined,noticeAlive=true;
onMounted(()=>{if(currentEntryContext().native)void import('@tauri-apps/api/event').then(({listen})=>listen<string>('launcher-close-blocked',event=>{notice.value=event.payload;})).then(off=>{if(noticeAlive)nativeNoticeOff=off;else off();}).catch(()=>{});});
onBeforeUnmount(()=>{noticeAlive=false;nativeNoticeOff?.();});
async function created(){await local.refreshInstances();page.value='instances';}
watch(()=>session.mode,mode=>{if(mode==='login')guideOpen.value=false;else if(!preferences.value.guideCompleted)showHelp();});
const installation=computed(()=>local.listing.value.instances.find(i=>i.id===installationId.value));
function submitted(){installationId.value='';++taskRevision.value;void local.refreshInstances();}
async function verify(id:string){try{await local.client.value?.verifyInstallation(id);++taskRevision.value;}catch(e){notice.value=operationMessage(e);}}
</script>
<template>
  <div class="launcher-shell" :class="['theme-'+preferences.theme,'workspace-'+workspace.preset,'workspace-accent-'+workspace.appearance.accent,{'workspace-no-background':!workspace.appearance.showBackground}]" :style="{'--workspace-opacity':workspace.appearance.opacity+'%'}"><StartupIntro :enabled="preferences.introEnabled&&!notice&&!local.error.value&&!workspaceWarning"/><LauncherTitleBar :preview="session.mode==='preview'" @error="notice=$event"/>
    <LoginGate v-if="session.mode==='login'" :local-available="local.available.value" :local-busy="local.connecting.value" :local-error="local.error.value" @retry="local.reconnect" @local="enterLocal" @preview="session.mode='preview'"/>
    <div v-else class="launcher-layout"><aside class="launcher-sidebar"><div class="launcher-sidebar-label">你的冒险空间</div><ResourceNavigation :capabilities="session.mode==='local'?local.session.value?.capabilities:undefined" :page="page" @navigate="page=$event"/><div class="launcher-sidebar-bottom"><button class="launcher-account-chip" @click="page='accounts'"><span class="launcher-avatar">{{session.mode==='local'?selectedIdentity?.name[0]??'?':'?'}}</span><span><strong>{{session.mode==='local'?selectedIdentity?.name??'未选择游戏身份':'尚未登录'}}</strong><small>{{session.mode==='local'&&selectedIdentity?'临时离线 · 本次会话':'管理账号与游戏身份'}}</small></span><AppIcon name="chevron"/></button><div class="launcher-connection"><span><i></i>调度中心</span><small>尚未连接</small></div><button class="launcher-exit-preview" @click="leavePreview">{{session.mode==='local'?'退出本机模式':'退出预览'}} <span>↗</span></button></div><FeatureStatus :local="session.mode==='local'" :capabilities="local.session.value?.capabilities??{}" :instance-error="local.error.value" :instances-loading="local.loading.value" :instance-problems="local.listing.value.problems.length" :workspace-warning="workspaceWarning"/></aside><main class="launcher-content" tabindex="0" aria-label="启动器内容"><p v-if="local.session.value?.capabilities.server==='MOCK'" class="launcher-inline-notice" role="status">MOCK · 离线演练模式：目录为本地示例，真实服务器、文件上传下载及管理员包启动未接入。</p>
      <div class="workspace-toolbar" aria-label="工作台固定入口"><label>外观 <select data-quick-preset :value="preferences.activeId" @change="applyWorkspace({...preferences,activeId:($event.target as HTMLSelectElement).value})"><option v-for="p in PRESETS" :key="p.id" :value="'preset:'+p.id">{{p.name}}</option><option v-for="p in preferences.custom" :key="p.id" :value="p.id">{{p.name}}</option></select></label><button data-workspace-help @click="showHelp">使用指引</button><button ref="appearanceTrigger" data-workspace-settings @click="appearanceOpen=true">工作台外观</button><button data-workspace-recover @click="applyWorkspace({...preferences,activeId:'preset:workbench'})">恢复默认</button></div>
      <p v-if="workspaceWarning" class="workspace-warning" role="alert">{{workspaceWarning}}</p>
      <MaidGuide v-if="(page!=='instances'||!launchInstanceId)&&(page!=='versions'||notice||workspaceWarning||guideOpen||local.connecting.value)" :state="notice||workspaceWarning?'attention':guideOpen?'guide':local.connecting.value?'waiting':guideCelebrated?'success':'welcome'" :message="notice||workspaceWarning||(guideOpen?'我会介绍入口，具体操作由你确认。':guideCelebrated?'随时可以从使用指引重新查看步骤。':'欢迎来到三秋叶。选一个属于你的世界吧。')" :collapsed="maidCollapsed" @toggle="maidCollapsed=!maidCollapsed"/>
      <FirstRunGuide :open="guideOpen" :local="session.mode==='local'" :step="guideStep" @navigate="page=$event" @close="finishGuide()" @complete="finishGuide(true)"/>
      <CloudAccount v-if="page==='home'&&session.mode==='local'&&local.client.value" :client="local.client.value"/>
      <ServerWorkspace v-if="page==='home'&&session.mode==='local'&&local.client.value&&local.session.value?.capabilities.server==='MOCK'" :client="local.client.value" @own="page='instances'"/>
      <LauncherHome v-if="page==='home'" :layout="workspace" :selected-identity-name="selectedIdentity?.name??null" @help="showHelp" :local="session.mode==='local'" :instance-count="local.listing.value.instances.length" @navigate="page=$event"/>
      <VersionCenter v-else-if="session.mode==='local'&&page==='versions'&&local.client.value" :client="local.client.value" :instances="local.listing.value.instances" :instances-loading="local.loading.value" :instances-error="local.error.value" @created="created" @manage="launchInstanceId=$event;page='instances'" @instances="page='instances'"/>
      <ModCenter :install-available="local.session.value?.capabilities.instanceMods==='AVAILABLE'" v-else-if="session.mode==='local'&&page==='mods'&&local.client.value" :client="local.client.value"/>
      <PackCenter v-else-if="session.mode==='local'&&page==='packs'&&local.client.value&&local.session.value?.capabilities.packs==='AVAILABLE'" :client="local.client.value"/>
      <ResourceManager v-else-if="session.mode==='local'&&page in resourceKinds&&local.client.value&&local.session.value?.capabilities.resources==='AVAILABLE'" :key="page" :kind="resourceKinds[page as keyof typeof resourceKinds]" :client="local.client.value" :instances="local.listing.value.instances" :instances-loading="local.loading.value" :instances-error="local.error.value"/>
      <PlayerServers v-else-if="session.mode==='local'&&page==='servers'&&local.client.value&&local.session.value?.capabilities.playerServers==='AVAILABLE'" :client="local.client.value" :instances="local.listing.value.instances" @connect="(id,server)=>{directTarget=server;directInstanceId=id;launchInstanceId=id;page='instances'}"/>
      <OfflineAccounts v-else-if="session.mode==='local'&&page==='accounts'&&local.client.value&&canUseIdentities" :client="local.client.value" @changed="identityListing=$event"/>
      <CreativeTools v-else-if="page==='tools'"/>
      <GameOptions v-else-if="page==='defaults'&&session.mode==='local'&&local.client.value&&local.session.value?.capabilities.gameOptions==='AVAILABLE'" :client="local.client.value"/>
      <section v-else-if="page==='defaults'" class="launcher-local-page" data-defaults-unavailable><h1>默认配置</h1><p>默认配置需要可用的本机核心。当前未连接，或核心没有提供游戏选项能力。</p><p>没有读取或改写游戏设置。请在完整玩家EXE中重新连接；旧版本核心请整体更新程序目录。</p><button class="launcher-secondary" :disabled="local.connecting.value" @click="local.reconnect">重新连接本机核心</button></section>
      <section v-else-if="page==='settings'"><button class="launcher-secondary" @click="page='defaults'">默认游戏配置</button><WorkspaceSettings :preferences="preferences" @apply="applyWorkspace" @edit="editingLayout=$event"/><RuntimeSettings v-if="session.mode==='local'&&local.client.value&&canUseRuntime" :client="local.client.value" :instances="local.listing.value.instances" :initial-instance-id="launchInstanceId"/></section>
      <InstanceCenter :mods-available="local.session.value?.capabilities.instanceMods==='AVAILABLE'" v-else-if="session.mode==='local'&&page==='instances'" :listing="local.listing.value" :loading="local.loading.value" :error="local.error.value" :client="local.client.value??undefined" :revision="taskRevision" :launch-available="canLaunch" :selected-id="launchInstanceId" :server-target="launchInstanceId===directInstanceId?directTarget:undefined" @launch="directTarget=undefined;launchInstanceId=$event" @open-accounts="page='accounts'" @open-settings="page='settings'" @refresh="local.refreshInstances" @open-versions="page='versions'" @install="installationId=$event" @verify="verify"/>
      <FeaturePanel v-else :page="definition" :allow-updater-preview="allowUpdaterPreview" @open-updater-preview="emit('open-updater-preview')"/>
      <footer class="launcher-content-footer"><span>三秋叶启动器</span><span>{{session.mode==='local'?(local.session.value?.capabilities.server==='LIVE'?'本机模式 · 真实账号服务已配置，其余能力见功能状态':'本机模式 · 云端账号服务未配置'):'框架预览 · 账号、下载与同步待接入'}}</span></footer></main></div>
    <div v-if="notice" class="launcher-operation-notice" role="alert">{{notice}}<button aria-label="关闭提示" @click="notice=''">×</button></div>
    <WorkspaceAppearance v-if="appearanceOpen&&!editingLayout" :preferences="preferences" :warning="workspaceWarning" @apply="applyWorkspace" @edit="editingLayout=$event" @close="closeAppearance"/>
    <WorkspaceEditor v-if="editingLayout" :layout="editingLayout" :save-error="workspaceWarning" @cancel="editingLayout=null" @apply="applyWorkspace({...preferences,custom:preferences.custom.map(l=>l.id===$event.id?$event:l)})"/>
    <InstallationDialog v-if="session.mode==='local'&&installation&&local.client.value" :instance="installation" :client="local.client.value" :fabric-available="local.session.value?.capabilities.fabric==='AVAILABLE'" :loaders-available="local.session.value?.capabilities.loaders==='AVAILABLE'" @close="installationId=''" @submitted="submitted"/>
  </div>
</template>
