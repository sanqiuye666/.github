<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue';
import type {LocalLauncherClient} from '../local-api';
import type {LaunchSnapshot} from '../launch-types';
import {analyzeLaunch,diagnosisText,type LaunchDiagnosisReport} from './analyze';
import {sampleLaunchLogs} from './sample';
const props=defineProps<{client:Pick<LocalLauncherClient,'launchLogs'>;run:LaunchSnapshot;gameVersion:string}>();
const emit=defineEmits<{close:[]}>();
const report=ref<LaunchDiagnosisReport|null>(null),busy=ref(false),copyNotice=ref(''),manualCopy=ref(false);
const text=computed(()=>report.value?diagnosisText(report.value,props.gameVersion):'');
let controller:AbortController|undefined,sequence=0,alive=true;
async function analyse(){
 controller?.abort();controller=new AbortController();const current=++sequence,signal=controller.signal,run=props.run;
 report.value=null;copyNotice.value='';manualCopy.value=false;busy.value=true;
 try{const sample=['EXITED','FAILED'].includes(run.state)&&run.exitCode!==0?await sampleLaunchLogs(props.client,run.id,signal):{lines:[],partial:false,readError:false};if(alive&&current===sequence)report.value=analyzeLaunch(run,sample);}
 catch{if(alive&&current===sequence&&!signal.aborted)report.value=analyzeLaunch(run,{lines:[],partial:true,readError:true});}
 finally{if(alive&&current===sequence)busy.value=false;}
}
async function copy(){const current=sequence;try{await navigator.clipboard.writeText(text.value);if(alive&&current===sequence)copyNotice.value='诊断摘要已复制，不含日志原文。';}catch{if(alive&&current===sequence){manualCopy.value=true;copyNotice.value='无法访问剪贴板，请从下方选择摘要自行复制。';}}}
watch(()=>props.run.id,analyse,{immediate:true});
onBeforeUnmount(()=>{alive=false;++sequence;controller?.abort();});
</script>
<template>
 <section class="launch-diagnosis" aria-label="本次启动诊断" data-launch-diagnosis>
  <header><div><span class="launcher-overline">运行诊断</span><h3>{{report?.title??'正在读取这次运行的日志…'}}</h3></div><button class="diagnosis-text-action" data-close-diagnosis @click="emit('close')">关闭诊断</button></header>
  <p class="diagnosis-meta">运行 {{run.id}}<span v-if="report?.exitCode!==null&&report"> · 退出码 {{report.exitCode}}</span></p>
  <p v-if="busy" role="status">只读分析中，不会改动游戏文件。</p>
  <template v-if="report">
   <p class="launcher-caption">取样范围：当前保留日志前部，最多400行/128Ki字符，不含完整崩溃报告。本次取样 {{report.sampledLines}} 行。</p>
   <p v-if="report.partial||report.readError" role="status" class="diagnosis-limited">{{report.readError?'日志读取失败；当前结论主要依据运行结果。':'片段已轮转或达到取样上限，证据不全。'}} 未发现标记也不代表其他部分没有错误。</p>
   <article v-for="finding in report.findings" :key="finding.code" class="diagnosis-finding"><div class="diagnosis-heading"><h4>{{finding.title}}</h4><span>{{finding.confidence==='recognized'?'已识别结果类型':'候选原因'}}</span></div><p>{{finding.reason}}</p><ul><li v-for="step in finding.steps" :key="step">{{step}}</li></ul></article>
   <footer><button class="launcher-secondary" data-copy-diagnosis @click="copy">复制诊断摘要</button><button v-if="report.readError" class="diagnosis-text-action" data-retry-diagnosis @click="analyse">重新读取日志</button><span class="launcher-caption">仅本机分析，不上传日志，不自动修复。</span></footer>
   <p v-if="copyNotice" role="status">{{copyNotice}}</p><textarea v-if="manualCopy" readonly :value="text" aria-label="可复制的诊断摘要" rows="10"/>
  </template>
 </section>
</template>
<style scoped>
.launch-diagnosis{margin:24px 0;padding:24px;background:var(--diagnosis-bg,#192923);border-radius:16px;border-top:2px solid var(--launcher-accent,#458a70);color:inherit}.launch-diagnosis header,.launch-diagnosis footer,.diagnosis-heading{display:flex;align-items:baseline;justify-content:space-between;gap:16px;flex-wrap:wrap}.launch-diagnosis h3{margin:8px 0;font-size:20px;line-height:1.3}.launch-diagnosis h4{font-size:15px;margin:0}.diagnosis-meta{font-size:11px;color:var(--launcher-muted);overflow-wrap:anywhere}.diagnosis-finding{margin:24px 0}.diagnosis-finding p,.diagnosis-finding li{font-size:14px;line-height:1.75}.diagnosis-finding ul{padding-left:20px}.diagnosis-heading span{font-size:11px;color:var(--launcher-muted)}.diagnosis-limited{font-size:13px;line-height:1.7;border-left:3px solid #b78232;padding:8px 14px}.diagnosis-text-action{font:inherit;color:var(--launcher-muted);padding:8px;background:none;border:0;cursor:pointer}.launch-diagnosis textarea{width:100%;box-sizing:border-box;white-space:pre-wrap}
</style>
