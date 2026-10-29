<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref} from 'vue';
import {LocalApiError,operationMessage,type LocalLauncherClient} from './local-api';
import type {GameOptionsSnapshot,GameOptionValue} from './game-options-types';
import SettingSwitch from './controls/SettingSwitch.vue';
const props=defineProps<{client:LocalLauncherClient}>();
const loaded=ref<GameOptionsSnapshot|null>(null),enabled=ref(false),selected=ref<Record<string,boolean>>({}),values=ref<Record<string,GameOptionValue>>({});
const loading=ref(false),saving=ref(false),stale=ref(false),error=ref(''),notice=ref('');
const category=ref('全部'),previewVersion=ref('');
const compatibility=computed(()=>!previewVersion.value?'请选择版本查看本批字段兼容情况。':loaded.value?.supportedVersions.includes(previewVersion.value)?`Minecraft ${previewVersion.value}：本批 ${loaded.value.fields.length} 项均支持；只同步勾选的字段，启动仍按实际实例核对。`:`Minecraft ${previewVersion.value} 未验证，请关闭同步。`);
const lifetime=new AbortController();
const chosen=computed(()=>Object.fromEntries((loaded.value?.fields??[]).filter(f=>selected.value[f.key]).map(f=>[f.key,values.value[f.key]!])));
const valid=computed(()=>!!loaded.value&&!stale.value&&(loaded.value.fields.every(f=>!selected.value[f.key]||f.type!=='integer'||Number.isInteger(values.value[f.key])&&Number(values.value[f.key])>=f.min&&Number(values.value[f.key])<=f.max&&(Number(values.value[f.key])-f.min)%(f.step??1)===0)));
const groups=computed(()=>[...new Set(loaded.value?.fields.map(f=>f.category)??[])]);
function apply(data:GameOptionsSnapshot){loaded.value=data;enabled.value=data.enabled;selected.value=Object.fromEntries(data.fields.map(f=>[f.key,Object.hasOwn(data.values,f.key)]));values.value=Object.fromEntries(data.fields.map(f=>[f.key,data.values[f.key]??f.defaultValue]));stale.value=false;}
async function read(){if(loading.value||saving.value)return;loading.value=true;loaded.value=null;error.value='';notice.value='';try{const data=await props.client.gameOptions(lifetime.signal);if(!lifetime.signal.aborted)apply(data);}catch(e){if(!lifetime.signal.aborted)error.value=operationMessage(e);}finally{loading.value=false;}}
async function save(){if(!valid.value||loading.value||saving.value||!loaded.value)return;saving.value=true;error.value='';notice.value='';try{const data=await props.client.saveGameOptions(loaded.value.revision,enabled.value,chosen.value,lifetime.signal);if(!lifetime.signal.aborted){apply(data);notice.value='已保存。下次启动前应用所选项目，请重新执行启动检查。';}}catch(e){if(!lifetime.signal.aborted){error.value=operationMessage(e);if(e instanceof LocalApiError&&e.code==='OPTIONS_CHANGED')stale.value=true;}}finally{saving.value=false;}}
onMounted(read);onBeforeUnmount(()=>lifetime.abort());
</script>
<template>
 <section class="launcher-local-page game-options" aria-labelledby="game-options-title">
  <div class="launcher-section-heading"><div><span class="launcher-overline">GAME DEFAULTS</span><h1 id="game-options-title">默认游戏设置</h1><p>让新实例也保留你的操作习惯。只覆盖勾选的项目，其余设置继续由游戏保管。</p></div><button class="launcher-secondary" data-read-game-options :disabled="loading||saving" @click="read">重新读取</button></div>
  <p v-if="error" class="launcher-inline-notice" role="alert">{{error}}</p><p v-if="notice" class="launcher-inline-notice" role="status">{{notice}}</p><p v-if="loading" role="status">正在读取本机默认设置…</p>
  <section v-if="loaded" class="launcher-glass launcher-runtime-panel">
   <div class="launcher-modal-title"><h2>游戏选项</h2><SettingSwitch v-model="enabled" label="启动时同步" data-options-enabled :disabled="saving"/></div>
   <div class="game-options-preview"><span>已选 {{Object.keys(chosen).length}} 项</span><label>兼容性预览<select v-model="previewVersion" data-options-preview><option value="">选择已支持版本</option><option v-for="version in loaded.supportedVersions" :key="version" :value="version">Minecraft {{version}}</option></select></label></div><p class="launcher-caption">首批兼容原版 {{loaded.supportedVersions.join('、')}}。预览不修改游戏版本，启动按实际实例核对；其他版本请关闭同步。已有原文件先备份到本实例 options-backups。</p>
   <p class="launcher-caption" data-options-preview-result>{{compatibility}}</p><p class="launcher-caption">已选 {{Object.keys(chosen).length}} 项 · 取消勾选后不再覆盖，不会恢复以前的游戏值。皮肤部位控制显示，账号换肤另行接入。</p>
   <nav class="game-options-categories" aria-label="游戏选项分类"><button v-for="item in ['全部',...groups]" :key="item" :aria-pressed="category===item" :data-options-category="item" @click="category=item">{{item}}</button></nav><fieldset v-for="group in groups.filter(g=>category==='全部'||g===category)" :key="group" :disabled="saving" class="game-options-group"><legend>{{group}}</legend><div class="game-options-grid"><div v-for="field in loaded.fields.filter(f=>f.category===group)" :key="field.key" class="game-options-row">
    <label class="game-options-toggle"><input v-model="selected[field.key]" type="checkbox" :data-option-select="field.key"/>{{field.label}}</label>
    <div class="game-options-value"><input v-if="field.type==='integer'" v-model.number="values[field.key]" type="number" :min="field.min" :max="field.max" :step="field.step??1" :disabled="!selected[field.key]" :aria-label="field.label+'的值'" :data-option-value="field.key"/><SettingSwitch v-else-if="field.type==='boolean'" :model-value="values[field.key]===true" @update:model-value="values[field.key]=$event" :disabled="!selected[field.key]" :aria-label="field.label+'的值'" :data-option-value="field.key"/><select v-else v-model="values[field.key]" :disabled="!selected[field.key]" :aria-label="field.label+'的值'" :data-option-value="field.key"><option value="right">右手</option><option value="left">左手</option></select></div>
    <small class="launcher-caption">{{selected[field.key]?'下次启动应用此值':'跟随游戏 · 不覆盖'}}</small>
   </div></div></fieldset>
  </section><button class="launcher-primary" data-save-game-options :disabled="!valid||loading||saving" @click="save">{{saving?'正在保存…':'保存默认游戏设置'}}</button><p class="launcher-caption">按键配置与默认资源包同步待后续接入。此处不修改世界难度、存档或服务器规则。</p>
 </section>
</template>
<style scoped>
.game-options-group{border:0;border-top:1px solid var(--launcher-border,rgba(255,255,255,.12));padding:20px 0;margin:12px 0}.game-options-group legend{font-weight:700;padding-right:16px}.game-options-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:16px 28px}.game-options-row{display:grid;grid-template-columns:1fr auto;align-items:center;gap:9px;padding-bottom:12px;border-bottom:1px solid rgba(255,255,255,.08)}.game-options-row small{grid-column:1/-1}.game-options-toggle{display:flex;gap:9px;align-items:center}.game-options-value input[type=number],.game-options-value select{width:94px;background:#18262b;color:inherit;border:1px solid rgba(255,255,255,.2);border-radius:8px;padding:8px}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}
.game-options-preview{display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap;margin:20px 0}.game-options-preview label{display:flex;align-items:center;gap:12px}.game-options-preview select{background:#18262b;color:inherit;padding:10px;border:1px solid #81948b55;border-radius:8px}.game-options-categories{display:flex;gap:10px;flex-wrap:wrap;margin:20px 0}.game-options-categories button{padding:9px 16px;border:1px solid #81948b44;border-radius:8px}.game-options-categories button[aria-pressed=true]{background:#2c6b5466;color:#efcc8b}
</style>
