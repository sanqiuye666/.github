<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref,watch} from 'vue';
import {LocalApiError,operationMessage,type LocalLauncherClient} from './local-api';
import type {GameInstance} from './local-types';
import type {LaunchSettings,RuntimeDiscovery,RuntimeListing} from './launch-types';
const props=defineProps<{client:LocalLauncherClient;instances:GameInstance[];initialInstanceId?:string}>();
const instanceId=ref(props.initialInstanceId||props.instances[0]?.id||''),revision=ref(''),home=ref(''),extras=ref(''),error=ref(''),notice=ref(''),busy=ref(false),loading=ref(false),stale=ref(false);
const runtimes=ref<RuntimeListing>({runtimes:[],diagnostics:[],platformBits:64}),discovery=ref<RuntimeDiscovery|null>(null);
const draft=ref<LaunchSettings>({javaId:null,initialMemoryMiB:256,maximumMemoryMiB:2048,width:960,height:540,extraJvmArgs:[],acceptUnknownJava:false});
const instance=computed(()=>props.instances.find(value=>value.id===instanceId.value)),selectedJava=computed(()=>runtimes.value.runtimes.find(value=>value.id===draft.value.javaId));
const missingJava=computed(()=>!!draft.value.javaId&&!selectedJava.value);
const required=computed(()=>instance.value?.installation?.requiredJavaMajor??8);
const incompatible=computed(()=>selectedJava.value&&(selectedJava.value.bits!==(instance.value?.installation?.platform.bits??runtimes.value.platformBits)||instance.value?.installation?.requiredJavaMajor!=null&&selectedJava.value.majorVersion<required.value));
const unknown=computed(()=>selectedJava.value&&!incompatible.value&&selectedJava.value.majorVersion!==required.value);
const parameters=computed(()=>extras.value.split(/\r?\n/).map(value=>value.trim()).filter(Boolean));
const valid=computed(()=>{
 const s=draft.value;return !!revision.value&&!!instance.value&&!stale.value&&!missingJava.value&&!incompatible.value&&(!unknown.value||s.acceptUnknownJava)
 &&[s.initialMemoryMiB,s.maximumMemoryMiB,s.width,s.height].every(Number.isInteger)&&s.initialMemoryMiB>=256&&s.maximumMemoryMiB<=32768&&s.initialMemoryMiB<=s.maximumMemoryMiB&&s.width>=640&&s.width<=7680&&s.height>=480&&s.height<=4320
 &&parameters.value.length<=32&&parameters.value.every(value=>value.startsWith('-')&&value.length<=4096&&!/[\x00-\x1f\x7f]/.test(value))&&parameters.value.join('').length<=16384;
});
const lifetime=new AbortController();let timer:ReturnType<typeof setTimeout>|undefined,sequence=0;
function failed(e:unknown){if(lifetime.signal.aborted)return;error.value=operationMessage(e);if(e instanceof LocalApiError&&(e.status===401||e.status===403))clearTimeout(timer);}
async function readRuntimes(){try{runtimes.value=await props.client.runtimes(lifetime.signal);}catch(e){failed(e);}}
async function readSettings(){const current=++sequence;revision.value='';stale.value=false;error.value='';notice.value='';if(!instanceId.value)return;loading.value=true;
 try{const value=await props.client.launchSettings(instanceId.value,lifetime.signal);if(current!==sequence||lifetime.signal.aborted)return;draft.value={...value.settings};extras.value=value.settings.extraJvmArgs.join('\n');revision.value=value.revision;}
 catch(e){failed(e);}finally{if(current===sequence)loading.value=false;}}
async function save(){if(busy.value||!valid.value)return;busy.value=true;error.value='';notice.value='';try{const value=await props.client.saveLaunchSettings(instanceId.value,revision.value,{...draft.value,javaId:draft.value.javaId||null,extraJvmArgs:parameters.value},lifetime.signal);revision.value=value.revision;notice.value='本实例启动设置已保存；下次启动请重新检查';}catch(e){if(e instanceof LocalApiError&&e.code==='SETTINGS_CHANGED')stale.value=true;failed(e);}finally{busy.value=false;}}
function applyDiscovery(value:RuntimeDiscovery){if(lifetime.signal.aborted)return;discovery.value=value;runtimes.value=value;if(value.state==='RUNNING')timer=setTimeout(poll,750);}
async function poll(){const id=discovery.value?.id;if(!id||lifetime.signal.aborted)return;try{applyDiscovery((await props.client.runtimeDiscovery(id,lifetime.signal)).discovery);}catch(e){failed(e);}}
async function discover(){if(busy.value||discovery.value?.state==='RUNNING')return;busy.value=true;error.value='';clearTimeout(timer);try{applyDiscovery((await props.client.discoverRuntimes(lifetime.signal)).discovery);}catch(e){failed(e);}finally{busy.value=false;}}
async function cancel(){const id=discovery.value?.id;if(!id||busy.value)return;busy.value=true;clearTimeout(timer);try{applyDiscovery((await props.client.cancelRuntimeDiscovery(id,lifetime.signal)).discovery);}catch(e){failed(e);}finally{busy.value=false;}}
async function register(){if(busy.value||!home.value.trim())return;busy.value=true;error.value='';try{runtimes.value=await props.client.registerJavaHome(home.value.trim(),lifetime.signal);notice.value='已实际检测并登记所选 Java';}catch(e){failed(e);}finally{busy.value=false;}}
watch(instanceId,readSettings);watch(()=>props.instances,()=>{if(!props.instances.some(value=>value.id===instanceId.value))instanceId.value=props.instances[0]?.id||'';});
onMounted(()=>{void readRuntimes();void readSettings();});onBeforeUnmount(()=>{++sequence;lifetime.abort();clearTimeout(timer);});
</script>
<template>
 <section class="launcher-local-page"><div class="launcher-section-heading"><div><span class="launcher-overline">RUNTIME SETTINGS</span><h1>给每个世界，合适的环境</h1><p>Java 与启动参数按实例保存。存档、配置和日志各自独立。</p></div><button class="launcher-secondary" :disabled="busy||loading" @click="readSettings">重新读取设置</button></div>
  <p v-if="error" class="launcher-inline-notice" role="alert">{{error}}</p><p v-if="notice" class="launcher-inline-notice" role="status">{{notice}}</p>
  <section class="launcher-glass launcher-runtime-panel"><div class="launcher-modal-title"><h2>本机 Java</h2><div class="launcher-inline-actions"><button class="launcher-secondary" data-discover-java :disabled="busy||discovery?.state==='RUNNING'" @click="discover">检测 Java</button><button v-if="discovery?.state==='RUNNING'" class="launcher-secondary" :disabled="busy" @click="cancel">取消检测</button></div></div>
   <p class="launcher-caption">{{discovery?.message??'点击检测后检查已知安装位置；也可以手选 Java 安装目录。不会自动安装 Java。'}}</p>
   <div v-for="runtime in runtimes.runtimes" :key="runtime.id" class="launcher-runtime-item"><strong>Java {{runtime.majorVersion}} · {{runtime.bits}} 位</strong><span>{{runtime.vendor}}</span></div><p v-if="!runtimes.runtimes.length" class="launcher-caption">尚未登记可用 Java。请检测或选择安装目录。</p>
   <form class="launcher-account-form" @submit.prevent="register"><label>Java 安装目录<input v-model="home" autocomplete="off" placeholder="选择包含 bin/java 的安装目录" :disabled="busy"/></label><button class="launcher-secondary" :disabled="busy||!home.trim()">检测此目录</button></form><details v-if="runtimes.diagnostics.length"><summary>检测说明 · {{runtimes.diagnostics.length}} 项</summary><p v-for="(item,index) in runtimes.diagnostics" :key="index" class="launcher-caption">{{item.message}}</p></details>
  </section>
  <section class="launcher-glass launcher-runtime-panel"><h2>实例启动设置</h2><p v-if="!instances.length" class="launcher-caption">先到版本中心创建实例，再为它保存独立设置。</p>
   <div class="launcher-runtime-grid"><label>游戏实例<select v-model="instanceId" :disabled="busy||!instances.length"><option v-if="!instances.length" value="">尚无实例</option><option v-for="item in instances" :key="item.id" :value="item.id">{{item.name}} · {{item.gameVersion}}</option></select></label>
    <label>Java 环境<select v-model="draft.javaId" data-java-selection :disabled="busy||loading||!instance" @change="draft.acceptUnknownJava=false"><option :value="null">自动选择匹配版本</option><option v-if="missingJava" :value="draft.javaId">上次选择的 Java 尚未检测</option><option v-for="java in runtimes.runtimes" :key="java.id" :value="java.id">Java {{java.majorVersion}} · {{java.bits}} 位 · {{java.vendor}}</option></select></label>
    <label>初始内存（MiB）<input v-model.number="draft.initialMemoryMiB" type="number" min="256" :max="draft.maximumMemoryMiB" :disabled="busy||loading||!instance"/></label><label>最大内存（MiB）<input v-model.number="draft.maximumMemoryMiB" data-maximum-memory type="number" min="256" max="32768" :disabled="busy||loading||!instance"/></label>
    <label>窗口宽度<input v-model.number="draft.width" type="number" min="640" max="7680" :disabled="busy||loading||!instance"/></label><label>窗口高度<input v-model.number="draft.height" type="number" min="480" max="4320" :disabled="busy||loading||!instance"/></label>
   </div><p class="launcher-caption">{{instance?.installation?`该版本要求 Java ${required}，${instance.installation.platform.bits} 位。`:'安装后按官方元数据核对 Java；旧版未知要求默认推荐 Java 8。'}} 内存不足时会要求调整。</p>
   <p v-if="missingJava" class="launcher-inline-notice" role="alert">上次选择的 Java 尚未检测，请先检测或改为自动选择。</p><p v-if="incompatible" class="launcher-inline-notice" role="alert">所选 Java 主版本低于要求或位数不匹配，请重新选择。</p><label v-if="unknown" class="launcher-check-label"><input v-model="draft.acceptUnknownJava" data-accept-unknown-java type="checkbox"/>我知道此 Java 与推荐主版本不同，兼容性未知，仍选择它。</label>
   <label class="launcher-extra-jvm">附加 JVM 参数（每行一个参数，最多 32 个）<textarea v-model="extras" rows="4" placeholder="-Dexample=含空格的值" :disabled="busy||loading||!instance"></textarea></label><p class="launcher-caption">启动器管理内存、类路径、身份和目录；不能添加 Java agent 或参数文件。</p>
   <button class="launcher-primary" data-save-launch-settings :disabled="busy||loading||!valid" @click="save">保存本实例设置</button>
  </section>
 </section>
</template>
