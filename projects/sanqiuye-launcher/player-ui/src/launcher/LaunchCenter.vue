<script setup lang="ts">
import MaidGuide from './workspace/MaidGuide.vue';
import LaunchDiagnosis from './diagnostics/LaunchDiagnosis.vue';
import {computed,onBeforeUnmount,onMounted,ref,watch} from 'vue';
import {LocalApiError,operationMessage,type LocalLauncherClient} from './local-api';
import type {GameInstance} from './local-types';
import type {IdentityListing,LaunchCheck,LaunchLogPage,LaunchSnapshot,LaunchState} from './launch-types';
const props=defineProps<{client:LocalLauncherClient;instance:GameInstance;serverTarget?:import('./resource-types').PlayerServerTarget}>();const emit=defineEmits<{'open-accounts':[];'open-settings':[]}>();
const identities=ref<IdentityListing>({identities:[],selectedId:null,temporary:true}),identityId=ref(''),check=ref<LaunchCheck|null>(null),expired=ref(false),runs=ref<LaunchSnapshot[]>([]),busy=ref(''),error=ref(''),confirmStop=ref(''),logId=ref(''),logs=ref<LaunchLogPage|null>(null),logBusy=ref(false);
let checkSequence=0;
const lifetime=new AbortController();let runTimer:ReturnType<typeof setTimeout>|undefined,checkTimer:ReturnType<typeof setTimeout>|undefined,expiryTimer:ReturnType<typeof setTimeout>|undefined,sequence=0;const stopped=ref(false);
const labels:Record<LaunchState,string>={PREPARING:'准备文件',STARTING:'创建进程',RUNNING:'进程运行中',STOPPING:'正在结束',EXITED:'进程已退出',FAILED:'启动失败',CANCELLED:'已取消',UNCERTAIN:'状态待确认'};
const active=(state:LaunchState)=>['PREPARING','STARTING','RUNNING','STOPPING'].includes(state);
const maidCollapsed=ref(false);
const diagnosisId=ref('');
const diagnosisRun=computed(()=>runs.value.find(run=>run.id===diagnosisId.value));
const preparing=computed(()=>check.value?.state==='CHECKING'||runs.value.some(r=>['PREPARING','STARTING'].includes(r.state)));
const canStart=computed(()=>!!props.instance.installation&&check.value?.state==='READY'&&!expired.value&&!busy.value&&!stopped.value);
function fail(e:unknown){if(lifetime.signal.aborted)return;error.value=operationMessage(e);if(e instanceof LocalApiError&&(e.status===401||e.status===403)){stopped.value=true;clearTimeout(runTimer);clearTimeout(checkTimer);clearTimeout(expiryTimer);}}
function acceptCheck(value:LaunchCheck){if(lifetime.signal.aborted)return;clearTimeout(checkTimer);clearTimeout(expiryTimer);check.value=value;expired.value=Date.parse(value.expiresAt)<=Date.now();
 if(value.state==='CHECKING'&&!stopped.value)checkTimer=setTimeout(pollCheck,750);
 if(value.state==='READY'&&!expired.value)expiryTimer=setTimeout(()=>{expired.value=true;},Math.max(0,Date.parse(value.expiresAt)-Date.now()));
}
async function pollCheck(){if(!check.value||stopped.value||lifetime.signal.aborted)return;const request=checkSequence;try{const value=await props.client.launchCheck(check.value.id,lifetime.signal);if(request===checkSequence)acceptCheck(value.check);}catch(e){fail(e);}}
async function loadRuns(){if(stopped.value||lifetime.signal.aborted)return;clearTimeout(runTimer);const current=++sequence;
 try{const value=await props.client.launches(props.instance.id,lifetime.signal);if(current!==sequence||lifetime.signal.aborted)return;runs.value=value.launches;if(value.launches.some(run=>active(run.state)))runTimer=setTimeout(loadRuns,1000);}catch(e){fail(e);}}
async function refresh(){try{const value=await props.client.identities(lifetime.signal);if(lifetime.signal.aborted)return;identities.value=value;identityId.value=value.selectedId??'';await loadRuns();}catch(e){fail(e);}}
async function checkLaunch(){if(busy.value||!identityId.value||!props.instance.installation||stopped.value)return;busy.value='check';error.value='';check.value=null;clearTimeout(checkTimer);clearTimeout(expiryTimer);
 const request=++checkSequence;try{const value=await props.client.checkLaunch(props.instance.id,identityId.value,lifetime.signal,props.serverTarget);if(request===checkSequence)acceptCheck(value.check);}catch(e){fail(e);}finally{busy.value='';}}
async function cancelCheck(){if(busy.value||!check.value||stopped.value)return;busy.value='cancel';clearTimeout(checkTimer);try{acceptCheck((await props.client.cancelLaunchCheck(check.value.id,lifetime.signal)).check);}catch(e){fail(e);}finally{busy.value='';}}
async function start(){if(!canStart.value||!check.value)return;if(Date.parse(check.value.expiresAt)<=Date.now()){expired.value=true;return;}busy.value='start';error.value='';
 try{const result=await props.client.startLaunch(check.value.id,lifetime.signal);check.value={...check.value,state:'CONSUMED'};clearTimeout(expiryTimer);runs.value=[...runs.value.filter(run=>run.id!==result.launch.id),result.launch];await loadRuns();}
 catch(e){check.value=null;clearTimeout(expiryTimer);fail(e);}finally{busy.value='';}}
async function stop(){if(busy.value||!confirmStop.value||stopped.value)return;busy.value='stop';error.value='';try{const result=await props.client.stopLaunch(confirmStop.value,true,lifetime.signal);runs.value=runs.value.map(run=>run.id===result.launch.id?result.launch:run);confirmStop.value='';clearTimeout(runTimer);runTimer=setTimeout(loadRuns,1000);}catch(e){fail(e);}finally{busy.value='';}}
async function readLogs(id:string,cursor?:string){if(logBusy.value||stopped.value)return;logBusy.value=true;error.value='';try{const page=await props.client.launchLogs(id,cursor,500,lifetime.signal);if(lifetime.signal.aborted)return;logId.value=id;logs.value=page;}catch(e){fail(e);}finally{logBusy.value=false;}}
watch([identityId,()=>props.instance.id,()=>props.instance.installation?.installationId,()=>props.instance.gameVersion,()=>props.serverTarget?.id,()=>props.serverTarget?.revision,()=>props.serverTarget?.address.host,()=>props.serverTarget?.address.port],()=>{++checkSequence;check.value=null;clearTimeout(checkTimer);clearTimeout(expiryTimer);});
onMounted(refresh);onBeforeUnmount(()=>{stopped.value=true;++sequence;lifetime.abort();clearTimeout(runTimer);clearTimeout(checkTimer);clearTimeout(expiryTimer);});
</script>
<template>
 <section class="launcher-glass launcher-launch-center"><div class="launcher-modal-title"><div><span class="launcher-overline">ISOLATED LAUNCH</span><h2>{{instance.name}} · 启动与日志</h2></div><button class="launcher-secondary" :disabled="!!busy||stopped" @click="refresh">刷新状态</button></div>
  <p v-if="serverTarget" class="launcher-inline-notice" data-direct-target>本次直连：{{serverTarget.name}} · {{serverTarget.address.host}}:{{serverTarget.address.port}}；仍须检查和确认启动。</p>
  <p class="launcher-caption">{{instance.gameVersion}} · 配置和存档保存在此实例。游戏进程运行中不代表已进入菜单。</p><p v-if="error" class="launcher-inline-notice" role="alert">{{error}}</p>
  <MaidGuide :state="error?'attention':preparing?'waiting':canStart?'success':'guide'" :message="error||(preparing?'正在检查或准备实际文件，请查看下方状态。':canStart?'启动检查通过。是否启动，仍由你确认。':'准备好身份与 Java 后，先做启动检查。')" :collapsed="maidCollapsed" @toggle="maidCollapsed=!maidCollapsed"/>
  <div class="launcher-launch-controls"><label>本次启动的临时离线身份<select v-model="identityId" :disabled="!!busy||check?.state==='CHECKING'||stopped"><option value="">请选择身份</option><option v-for="identity in identities.identities" :key="identity.id" :value="identity.id">{{identity.name}}</option></select></label><button class="launcher-secondary" @click="emit('open-accounts')">管理临时身份</button><button class="launcher-secondary" @click="emit('open-settings')">Java 与启动设置</button></div>
  <p v-if="!identities.identities.length" class="launcher-inline-notice">请先添加临时离线身份，再检查启动环境。</p>
  <div class="launcher-inline-actions"><button class="launcher-secondary" data-check-launch :disabled="!!busy||!identityId||!instance.installation||stopped||check?.state==='CHECKING'" @click="checkLaunch">启动检查</button><button v-if="check?.state==='CHECKING'||check?.state==='READY'" class="launcher-secondary" data-cancel-launch-check :disabled="!!busy||stopped" @click="cancelCheck">取消检查</button><button class="launcher-primary" data-start-launch :disabled="!canStart" @click="start">启动游戏</button></div>
  <p v-if="check" class="launcher-caption" role="status">{{check.message}}<span v-if="check.javaMajor"> · Java {{check.javaMajor}}</span><span v-if="check.errorCode"> · {{check.errorCode}}</span><span v-if="expired"> · 检查已过期，请重新检查</span></p>
  <p v-if="!runs.length" class="launcher-caption">此实例尚无启动记录。检查完成后，明确点击“启动游戏”。</p>
  <div v-for="run in runs" :key="run.id" class="launcher-task-row" :data-launch-id="run.id" :data-launch-state="run.state"><div><strong>{{labels[run.state]}}</strong><small>{{run.message}}</small><small v-if="run.exitCode!==null">真实退出码 · {{run.exitCode}}</small><small v-if="run.errorCode">{{run.errorCode}}</small></div><div class="launcher-inline-actions"><button v-if="run.state==='FAILED'||run.state==='EXITED'&&run.exitCode!==0" class="launcher-secondary" :data-diagnose-launch="run.id" :disabled="stopped" @click="diagnosisId=run.id">分析启动问题</button><button class="launcher-secondary" data-view-launch-logs :disabled="logBusy||stopped" @click="readLogs(run.id)">查看日志</button><button v-if="active(run.state)&&run.state!=='STOPPING'" class="launcher-secondary" data-stop-launch :disabled="!!busy||stopped" @click="confirmStop=run.id">结束游戏进程</button></div></div>
  <LaunchDiagnosis v-if="diagnosisRun" :client="client" :run="diagnosisRun" :game-version="instance.gameVersion" @close="diagnosisId=''"/>
  <div v-if="confirmStop" class="launcher-inline-notice" role="alert"><p>结束进程可能丢失未保存进度。建议先从游戏窗口正常退出。确定结束此进程？</p><div class="launcher-inline-actions"><button class="launcher-secondary" :disabled="!!busy" @click="confirmStop=''">继续游戏</button><button class="launcher-primary" data-confirm-stop-launch :disabled="!!busy" @click="stop">确认结束进程</button></div></div>
  <section v-if="logs" class="launcher-game-logs"><div class="launcher-modal-title"><h3>采集日志</h3><span class="launcher-caption">每页最多 500 行</span></div><p v-if="logs.truncated" class="launcher-caption">较早日志已轮转，从当前仍保留的内容继续。</p><pre>{{logs.lines.join('\n')||'当前没有更多日志。'}}</pre><div class="launcher-inline-actions"><button class="launcher-secondary" :disabled="logBusy||stopped" @click="readLogs(logId)">从头读取</button><button class="launcher-secondary" data-next-launch-logs :disabled="logBusy||stopped" @click="readLogs(logId,logs.nextCursor)">下一页 / 读取新内容</button></div></section>
 </section>
</template>
