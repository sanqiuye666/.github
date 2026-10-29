<script setup lang="ts">
import {onBeforeUnmount,onMounted,ref} from 'vue';import {request} from '../store';import {unwrapServerResult,type CloudAccountDirectory} from '../../../../dispatch-core/src/main/typescript/server-contract';
const directory=ref<CloudAccountDirectory|null>(null),busy=ref(false),error=ref('');let alive=true,timer:ReturnType<typeof setTimeout>|undefined;
async function refresh(){if(busy.value||document.hidden||!alive)return;clearTimeout(timer);busy.value=true;error.value='';try{const value=unwrapServerResult<CloudAccountDirectory>(await request('/api/server/accounts'));if(alive)directory.value=value;}catch(e){if(alive)error.value=(e as Error).message;}finally{if(alive){busy.value=false;timer=setTimeout(refresh,10000);}}}
function visibility(){if(document.hidden)clearTimeout(timer);else void refresh();}
onMounted(()=>{document.addEventListener('visibilitychange',visibility);void refresh();});onBeforeUnmount(()=>{alive=false;clearTimeout(timer);document.removeEventListener('visibilitychange',visibility);});
</script>
<template><section class="stage cloud-accounts" aria-label="云端玩家账号"><header><div><span>真实账号服务</span><h1>玩家账号</h1><p>与玩家端共用账号服务。页面可见时每10秒更新，最近连接时间来自服务收到的账号请求。</p></div><button :disabled="busy" data-cloud-admin-refresh @click="refresh">{{busy?'正在同步…':'立即同步'}}</button></header><p v-if="error" role="alert">{{error}}。保留的列表可能已过期。</p><template v-if="directory"><p role="status">共{{directory.total}}个账号 · 更新于{{new Date(directory.checkedAt).toLocaleTimeString()}}</p><p v-if="directory.total>directory.limit">当前显示最近注册的{{directory.limit}}位玩家。</p><div class="cloud-table"><table><thead><tr><th>玩家</th><th>所属组</th><th>注册时间</th><th>最近连接</th></tr></thead><tbody><tr v-for="a in directory.accounts" :key="a.id" :data-cloud-player="a.username"><td><strong>{{a.displayName}}</strong><small>@{{a.username}}</small></td><td>{{a.groupId}}</td><td>{{new Date(a.createdAt).toLocaleString()}}</td><td>{{new Date(a.lastSeenAt).toLocaleString()}}</td></tr></tbody></table><p v-if="!directory.accounts.length">尚无账号，玩家可从启动器首页注册。</p></div></template><p v-else-if="!error">正在获取真实账号…</p><footer>此处不显示密码、会话、微软凭据或玩家自有文件；游戏运行状态和整合包下发仍按各能力接入情况展示。</footer></section></template>
<style scoped>
.cloud-accounts{padding:32px;gap:20px;overflow:auto;color:var(--text,#162026)}
.cloud-accounts header{display:flex;justify-content:space-between;align-items:center;gap:24px}
.cloud-accounts header span{font-size:12px;color:var(--accent,#12786a)}
.cloud-accounts h1{font-size:24px;font-weight:600;line-height:1.2;margin:8px 0 12px}
.cloud-accounts p,.cloud-accounts footer{line-height:1.7;margin:0;color:var(--muted,#5b6971)}
.cloud-accounts header p{max-width:64ch}
.cloud-accounts header button{flex:none}
.cloud-accounts [role=alert]{padding:12px 16px;border-radius:12px;background:var(--danger-soft,#c4423b12);color:var(--danger,#c4423b)}
.cloud-table{overflow:auto;border:1px solid var(--line,#e2e6e9);border-radius:16px;background:var(--surface-2,#f7f9fa)}
.cloud-table table{width:100%;min-width:600px;border-collapse:collapse}
.cloud-table th,.cloud-table td{text-align:left;padding:18px 20px;border-bottom:1px solid var(--line,#e2e6e9)}
.cloud-table th{font-size:12px;font-weight:600;color:var(--muted,#5b6971)}
.cloud-table td{background:var(--surface,#fff);font-variant-numeric:tabular-nums}
.cloud-table tr:last-child td{border-bottom:0}
.cloud-table strong{font-weight:600}
.cloud-table small{display:block;margin-top:5px;color:var(--muted,#5b6971)}
.cloud-accounts footer{font-size:12px;max-width:80ch}
@media(max-width:1000px){.cloud-accounts{padding:20px;gap:16px}.cloud-accounts header{align-items:start;flex-wrap:wrap}.cloud-accounts h1{font-size:22px}}
</style>
