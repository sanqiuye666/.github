<script setup lang="ts">
import {onBeforeUnmount,onMounted,ref} from 'vue';
import {operationMessage,type LocalLauncherClient} from './local-api';
import type {GameInstance,InstallationPlan,TaskSnapshot,LoaderKind} from './local-types';
const props=withDefaults(defineProps<{client:LocalLauncherClient;instance:GameInstance;fabricAvailable?:boolean;loadersAvailable?:boolean}>(),{fabricAvailable:true,loadersAvailable:false});
const emit=defineEmits<{close:[];submitted:[task:TaskSnapshot]}>();
const names:Record<LoaderKind,string>={VANILLA:'官方原版',FABRIC:'Fabric',QUILT:'Quilt',FORGE:'Forge',NEOFORGE:'NeoForge'};
const kind=ref<LoaderKind>(props.instance.loader==='FABRIC'&&props.fabricAvailable?'FABRIC':props.loadersAvailable&&['QUILT','FORGE','NEOFORGE'].includes(props.instance.loader)?props.instance.loader as LoaderKind:'VANILLA'),versions=ref<{version:string;stable:boolean}[]>([]),loaderVersion=ref('');
const plan=ref<InstallationPlan|null>(null),loading=ref(false),busy=ref(false),error=ref('');let request:AbortController|undefined,sequence=0,alive=true;
function invalidate(){request?.abort();++sequence;plan.value=null;error.value='';loading.value=false;}
async function loadPlan(){
 if(busy.value||kind.value!=='VANILLA'&&!loaderVersion.value)return;invalidate();request=new AbortController();const current=sequence;loading.value=true;
 try{const result=kind.value==='FABRIC'?await props.client.planFabricInstallation(props.instance.id,loaderVersion.value,request.signal):kind.value==='VANILLA'?await props.client.planInstallation(props.instance.id,request.signal):await props.client.planLoaderInstallation(props.instance.id,kind.value,loaderVersion.value,request.signal);if(alive&&sequence===current)plan.value=result;}
 catch(e){if(alive&&sequence===current)error.value=operationMessage(e);}finally{if(alive&&sequence===current)loading.value=false;}
}
async function selectKind(){
 invalidate();versions.value=[];loaderVersion.value='';if(kind.value==='VANILLA'){await loadPlan();return;}if(kind.value==='FABRIC'?!props.fabricAvailable:!props.loadersAvailable)return;
 request=new AbortController();const current=sequence;loading.value=true;
 try{const result=kind.value==='FABRIC'?await props.client.fabricVersions(props.instance.id,request.signal):await props.client.loaderVersions(props.instance.id,kind.value,request.signal);if(alive&&sequence===current){versions.value=result.versions;loaderVersion.value=result.versions.find(v=>v.version===(props.instance.installation?.loaderVersion??props.instance.installation?.fabricVersion))?.version??result.versions.find(v=>v.stable)?.version??result.versions[0]?.version??'';}}
 catch(e){if(alive&&sequence===current)error.value=operationMessage(e);}finally{if(alive&&sequence===current)loading.value=false;}
}
async function install(){if(!plan.value||busy.value)return;busy.value=true;error.value='';request=new AbortController();try{const result=await props.client.install(plan.value.planId,request.signal);if(alive)emit('submitted',result.task);}catch(e){if(alive){error.value=operationMessage(e);busy.value=false;}}}
onMounted(()=>kind.value!=='VANILLA'?selectKind():loadPlan());onBeforeUnmount(()=>{alive=false;invalidate();});
</script>
<template>
 <div class="launcher-modal-backdrop"><section class="launcher-modal launcher-glass" role="dialog" aria-modal="true" aria-labelledby="installation-title">
  <div class="launcher-modal-title"><span class="launcher-overline">游戏文件与加载器</span><button class="launcher-secondary" aria-label="关闭安装计划" :disabled="busy" @click="emit('close')">×</button></div>
  <h2 id="installation-title">为 {{instance.name}} 准备游戏文件</h2><p>校验完成后发布到本实例的独立安装目录，保留存档和配置。</p>
  <div class="installation-choice"><label>游戏类型<select v-model="kind" data-installation-loader :disabled="busy" @change="selectKind"><option value="VANILLA">官方原版</option><option value="FABRIC" :disabled="!fabricAvailable">Fabric</option><option v-for="core in (['QUILT','FORGE','NEOFORGE'] as const)" :key="core" :value="core" :disabled="!loadersAvailable">{{names[core]}}</option></select></label><label v-if="kind!=='VANILLA'">{{names[kind]}}版本<select v-model="loaderVersion" data-loader-version :data-fabric-version="kind==='FABRIC'?'':undefined" :disabled="busy||loading||!versions.length" @change="invalidate"><option v-for="v in versions" :key="v.version" :value="v.version">{{v.version}}{{v.stable&&kind==='FABRIC'?' · 稳定':''}}</option></select></label></div>
  <p v-if="!fabricAvailable" class="launcher-caption">当前模式尚未接入Fabric官方目录。</p><p v-if="kind!=='VANILLA'" class="launcher-caption">按当前游戏版本查询官方兼容目录。生成计划会预取核心描述和缺少长度的依赖到缓存。安装使用本实例独立目录，失败不替换旧安装；其他模组仍需另行选择。旧安装格式可能尚不支持。</p>
  <button v-if="error&&kind!=='VANILLA'" class="launcher-secondary" :disabled="busy||loading" @click="selectKind">重新查询兼容版本</button><p v-if="loading" role="status">正在读取官方元数据并校验安装计划…</p><p v-if="error" class="launcher-inline-notice" role="alert">{{error}}</p>
  <p v-if="kind!=='VANILLA'&&!loading&&!error&&!versions.length" role="status">官方目录未列出此版本的{{names[kind]}}。</p>
  <button v-if="kind!=='VANILLA'&&!plan&&versions.length" class="launcher-secondary" data-loader-plan :data-fabric-plan="kind==='FABRIC'?'':undefined" :disabled="busy||loading||!loaderVersion" @click="loadPlan">生成{{names[kind]}}安装计划</button>
  <template v-if="plan"><dl class="launcher-plan-details"><dt>游戏版本</dt><dd>{{plan.versionId}}</dd><dt>加载器</dt><dd>{{plan.loader&&plan.loader!=='VANILLA'?names[plan.loader]+' '+(plan.loaderVersion??''):plan.fabricVersion?'Fabric '+plan.fabricVersion:'官方原版'}}</dd><dt>目标系统</dt><dd>{{plan.platform.osName}} · {{plan.platform.arch}} · {{plan.platform.bits}} 位</dd><dt>文件数量</dt><dd>{{plan.fileCount}} 个目标文件</dd><dt>下载上限估算</dt><dd>{{(plan.totalBytes/1048576).toFixed(2)}} MiB（已校验缓存可复用）</dd><dt>运行要求</dt><dd>{{plan.requiredJavaMajor?`Java ${plan.requiredJavaMajor}`:'官方元数据未声明'}}</dd></dl><p class="launcher-caption">安装完成后，到“启动与日志”选择游戏身份、检查Java并启动。OptiFine、完整整合包和模组自动装入仍待后续。</p><button class="launcher-primary launcher-full" data-confirm-install :disabled="busy" @click="install">{{busy?'正在提交…':'确认下载并安装文件'}} →</button></template>
 </section></div>
</template>
<style scoped>
.installation-choice{display:flex;gap:18px;flex-wrap:wrap;margin:20px 0}.installation-choice label{min-width:160px;flex:1}.installation-choice select{color:inherit;background:var(--launcher-surface,#213b32);padding:10px 12px;border:1px solid var(--launcher-line);border-radius:8px;font:inherit}
</style>
