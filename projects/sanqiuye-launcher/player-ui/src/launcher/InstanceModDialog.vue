<script setup lang="ts">
import {nextTick,onBeforeUnmount,onMounted,ref} from 'vue';
import {operationMessage,type LocalLauncherClient} from './local-api';
import type {GameInstance,InstanceModPlan,ModFileRef,TaskSnapshot} from './local-types';
const props=withDefaults(defineProps<{client:LocalLauncherClient;instance?:GameInstance;refValue?:ModFileRef;operation?:'INSTALL'|'REMOVE'|'FABRIC_API'}>(),{operation:'INSTALL'});
const emit=defineEmits<{close:[];submitted:[task:TaskSnapshot]}>();
const instances=ref<GameInstance[]>(props.instance?[props.instance]:[]),target=ref(props.instance?.id??''),plan=ref<InstanceModPlan|null>(null),loading=ref(false),busy=ref(false),error=ref(''),dialog=ref<HTMLElement>();
let request=new AbortController(),sequence=0,alive=true;const prior=document.activeElement;
function invalidate(){request.abort();request=new AbortController();++sequence;plan.value=null;loading.value=false;error.value='';}
async function preview(){if(!target.value||busy.value)return;invalidate();const current=sequence;loading.value=true;try{const value=await props.client.planInstanceMods(target.value,props.operation,props.refValue,request.signal);if(alive&&current===sequence)plan.value=value;}catch(e){if(alive&&current===sequence)error.value=operationMessage(e);}finally{if(alive&&current===sequence)loading.value=false;}}
async function apply(){if(!plan.value||busy.value||loading.value)return;if(Date.parse(plan.value.expiresAt)<=Date.now()){invalidate();error.value='计划已过期，请重新预览。';return;}busy.value=true;error.value='';try{const result=await props.client.applyInstanceMods(plan.value.planId,request.signal);if(alive)emit('submitted',result.task);}catch(e){if(alive){plan.value=null;error.value=operationMessage(e);busy.value=false;}}}
function keyboard(e:KeyboardEvent){if(e.key==='Escape'&&!busy.value){e.preventDefault();emit('close');}if(e.key==='Tab'){const buttons=Array.from(dialog.value?.querySelectorAll<HTMLElement>('button:not(:disabled),select:not(:disabled),[tabindex="0"]')??[]);const first=buttons[0],last=buttons.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}}}
onMounted(async()=>{await nextTick();dialog.value?.focus();if(props.instance)return;loading.value=true;const current=sequence;try{const result=await props.client.instances(request.signal);if(alive&&current===sequence){instances.value=result.instances.filter(i=>i.installation&&['FABRIC','QUILT','FORGE','NEOFORGE'].includes(i.loader));target.value=instances.value[0]?.id??'';}}catch(e){if(alive)error.value=operationMessage(e);}finally{if(alive&&current===sequence)loading.value=false;}});
onBeforeUnmount(()=>{alive=false;invalidate();if(prior instanceof HTMLElement&&prior.isConnected)prior.focus();});
</script>
<template>
 <div class="launcher-modal-backdrop"><section ref="dialog" class="launcher-modal launcher-glass" role="dialog" aria-modal="true" aria-labelledby="instance-mod-title" tabindex="-1" data-instance-mod-dialog @keydown="keyboard">
  <div class="launcher-modal-title"><span class="launcher-overline">实例模组</span><button class="launcher-secondary" aria-label="关闭模组计划" :disabled="busy" @click="emit('close')">×</button></div>
  <h2 id="instance-mod-title">{{operation==='REMOVE'?'移除全部受管模组':operation==='FABRIC_API'?'安装 Fabric API':'装入你的游戏实例'}}</h2>
  <p>只作用于选中的独立实例。手动文件保留；变更前保留原模组目录备份。</p>
  <label v-if="!instance" class="mod-target">目标实例<select v-model="target" data-mod-target :disabled="busy" @change="invalidate"><option v-for="item in instances" :key="item.id" :value="item.id">{{item.name}} · {{item.gameVersion}} · {{item.loader}}</option></select></label><p v-else>{{instance.name}} · {{instance.gameVersion}} · {{instance.loader}}</p>
  <p v-if="!target&&!loading" class="launcher-inline-notice">请先在实例库安装游戏与加载器，再选择兼容模组。</p><p v-if="error" class="launcher-inline-notice" role="alert">{{error}}</p><p v-if="loading" role="status">正在核对实例、官方文件与必需依赖…</p>
  <template v-if="plan"><div class="mod-plan-summary"><strong>{{plan.removal?'将移除':'将装入'}} {{plan.files.length}} 个文件</strong><span>{{plan.gameVersion}} · {{plan.loader}}<template v-if="!plan.removal"> · {{(plan.totalBytes/1048576).toFixed(2)}} MiB</template></span></div><ul class="mod-plan-files"><li v-for="file in plan.files" :key="file.source+file.projectId"><strong>{{file.fileName}}</strong><small>{{file.source}} · {{file.versionId}}</small><code>{{file.digest}}</code></li></ul><p v-for="warning in plan.warnings" :key="warning" class="launcher-caption">{{warning}}</p><p class="launcher-caption">仅自动加入平台声明的必需依赖，不放宽游戏或加载器条件；手动模组兼容性需要自行核对。下载或校验失败时保留当前目录。</p><button class="launcher-primary launcher-full" data-confirm-instance-mods :disabled="busy||loading" @click="apply">{{busy?'正在提交…':plan.removal?'确认移除全部受管模组':'确认下载并装入实例'}} →</button></template>
  <button v-else class="launcher-primary launcher-full" data-preview-instance-mods :disabled="!target||busy||loading" @click="preview">{{loading?'正在核对…':'查看变更计划'}} →</button>
 </section></div>
</template>
<style scoped>
.mod-target{display:grid;gap:10px;margin:24px 0}.mod-target select{padding:12px;border-radius:10px;background:var(--launcher-surface);color:inherit;border:1px solid var(--launcher-line);font:inherit}.mod-plan-summary{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;margin:24px 0 12px}.mod-plan-files{padding:0;list-style:none}.mod-plan-files li{display:grid;gap:4px;padding:12px 0}.mod-plan-files small{color:var(--launcher-muted)}.mod-plan-files code{font-size:11px;overflow-wrap:anywhere;opacity:.7}
</style>
