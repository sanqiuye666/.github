<script setup lang="ts">
import {computed,onMounted,onBeforeUnmount,ref} from 'vue';
import type {LocalLauncherClient} from '../local-api';
import {PendingServerWriteJournal,ServerCallError,unwrapServerResult,reportStatusLabel,type ServerStatus,type PlayerServerView,type ManagedPack} from '../../../../dispatch-core/src/main/typescript/server-contract';
const props=defineProps<{client:LocalLauncherClient}>();
const emit=defineEmits<{own:[]}>();
const status=ref<ServerStatus|null>(null),view=ref<PlayerServerView|null>(null),source=ref<'managed'|'own'>('managed');
const username=ref(''),displayName=ref(''),busy=ref(false),error=ref(''),notice=ref('');
const lifetime=new AbortController();
const journal=new PendingServerWriteJournal('player',window.sessionStorage),pending=ref(journal.pending);
const blocked=computed(()=>busy.value||!!pending.value||!!journal.problem);
async function api<T>(path:string,body?:Record<string,unknown>){return unwrapServerResult<T>(await props.client.serverCall(path,body,lifetime.signal));}
async function refresh(){view.value=null;try{view.value=await api<PlayerServerView>('/view');}catch(e){if(!(e instanceof ServerCallError&&e.code==='AUTH_FAILED'))throw e;}}
async function perform(path?:string,body?:Record<string,unknown>,retry=false){if(busy.value)return;busy.value=true;error.value='';notice.value='';try{
 const operation=path?journal.begin(path,body??{}):retry?journal.pending:null;pending.value=journal.pending;
 if(operation){await api(operation.path,operation.body);journal.completed();pending.value=null;notice.value='演练操作完成，未执行真实上传、下载或游戏启动。';}
 status.value=await api<ServerStatus>('/status');if(status.value.enabled)await refresh();
 }catch(e){if(path||retry)journal.failed(e);if(!lifetime.signal.aborted)error.value=e instanceof Error?e.message:'连接不上，请重试';}finally{pending.value=journal.pending;busy.value=false;}}
async function report(pack:ManagedPack,state:string){await perform('/report',{packId:pack.id,status:state});}
onMounted(()=>perform());onBeforeUnmount(()=>lifetime.abort());
</script>
<template>
 <section v-if="status?.enabled||error" class="server-workspace launcher-glass" aria-label="整合包来源与面板账号">
  <header><div><span class="server-mock-badge">MOCK · 离线演练</span><h2>选择这次冒险的来源</h2><p>不验证真实密码、不上传文件、不启动 Minecraft。</p></div><button class="launcher-secondary" data-server-refresh :disabled="busy" @click="perform()">{{busy?'正在处理…':'刷新 / 复查'}}</button></header>
  <p v-if="error" class="launcher-inline-notice" role="alert">{{error}}</p><p v-if="notice" role="status">{{notice}}</p>
  <p v-if="pending||journal.problem" class="launcher-inline-notice" role="status">{{journal.problem||'有未决操作。刷新仅读取状态，不会再次提交；请使用原编号复查。'}}<button v-if="pending" :disabled="busy" data-server-retry @click="perform(undefined,undefined,true)">使用原编号重试</button></p>
  <nav class="server-source-tabs" aria-label="整合包来源"><button data-pack-source="managed" :aria-pressed="source==='managed'" @click="source='managed'">管理员整合包</button><button data-pack-source="own" :aria-pressed="source==='own'" @click="source='own'">自己的整合包</button></nav>
  <div v-if="source==='own'" class="server-own"><h3>继续使用本地实例</h3><p>自己的整合包独立保存，不向管理员上报，也不会被下发包接管。</p><button class="launcher-primary" @click="emit('own')">打开自己的游戏实例</button></div>
  <div v-else class="server-player-grid"><section><template v-if="!view"><h3>演练面板账号</h3><p>这是本地模拟账号选择，与微软游戏身份分开。</p><label>英文用户名<input v-model="username" data-server-username maxlength="32" placeholder="3–32位小写字母、数字或下划线" :disabled="busy"/></label><label>显示名称<input v-model="displayName" data-server-display-name maxlength="64" :disabled="busy"/></label><div class="server-actions"><button class="launcher-primary" data-server-register :disabled="blocked||!username||!displayName" @click="perform('/register',{username,displayName})">注册演练账号</button><button class="launcher-secondary" data-server-login :disabled="blocked||!username" @click="perform('/login',{username})">演练登录</button></div></template>
   <template v-else><h3>下发给你的整合包</h3><p v-if="!view.packs.length">还没有可见的整合包。请在管理端发布并分配后刷新。</p><article v-for="pack in view.packs" :key="pack.id" class="server-pack" :data-managed-pack="pack.id"><h3>{{pack.name}}</h3><p>{{pack.gameVersion}} · {{pack.loader}} · 管理员来源</p><small>模拟版本 {{pack.releaseId?.slice(0,8)}} · 无真实文件</small><div class="server-actions"><button class="launcher-primary" data-mock-update :disabled="blocked" @click="report(pack,'UPDATED')">演练更新</button><button class="launcher-secondary" data-mock-launch :disabled="blocked||view.profile.report?.packId!==pack.id||view.profile.report?.status!=='UPDATED'" @click="report(pack,'SIMULATED_LAUNCH')">演练启动流程</button></div></article></template>
  </section><aside v-if="view" class="server-account-summary"><span class="launcher-overline">面板账号 · 模拟</span><h3>{{view.profile.displayName}}</h3><p>@{{view.profile.username}} · {{view.profile.groupId}}</p><p>{{reportStatusLabel(view.profile.report?.status??'')}}</p><small>真实游戏身份请在“账号”中选择。本次报告仅对应管理员模拟包。</small><button class="launcher-secondary" data-server-logout :disabled="blocked" @click="perform('/logout',{})">退出演练账号</button></aside></div>
 </section>
</template>
<style scoped>
.server-workspace{padding:22px;margin-bottom:24px;border:1px solid #5e9b8f44;border-radius:18px}.server-workspace header{display:flex;justify-content:space-between;gap:15px;align-items:center}.server-workspace h2{margin:12px 0;font-size:22px}.server-workspace p{color:#aabbb8;line-height:1.7}.server-mock-badge{background:#e6b36b20;color:#efc184;padding:6px 10px;border-radius:8px;font-size:12px}.server-source-tabs,.server-actions{display:flex;gap:12px;flex-wrap:wrap;margin:16px 0}.server-source-tabs button{padding:10px 18px;border:1px solid #69928655;border-radius:10px}.server-source-tabs button[aria-pressed=true]{background:#2e6a5a70;color:#e9fff6}.server-player-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(210px,280px);gap:20px}.server-player-grid label{display:grid;gap:7px;margin:12px 0}.server-player-grid input{background:#11221e;color:inherit;border:1px solid #82998b55;border-radius:8px;padding:11px;max-width:100%}.server-account-summary,.server-pack{background:#18392c26;border:1px solid #82998b33;border-radius:12px;padding:16px}.server-account-summary{align-self:start;display:grid;gap:10px}.server-account-summary small{line-height:1.7}.server-own{padding:12px 0}@media(max-width:1050px){.server-player-grid{grid-template-columns:1fr}.server-account-summary{grid-row:1}.server-workspace header{align-items:start;flex-wrap:wrap}}
</style>
