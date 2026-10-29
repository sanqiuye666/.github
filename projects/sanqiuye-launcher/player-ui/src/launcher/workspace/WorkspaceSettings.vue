<script setup lang="ts">
import {defineAsyncComponent,nextTick,ref} from 'vue';
import SettingSwitch from '../controls/SettingSwitch.vue';
import AnimationSettings from './intro/AnimationSettings.vue';
import {PRESETS,activeLayout,defaultPreferences,validateLayout,type WorkspacePreferences,type WorkspaceLayout} from './layout';
const props=defineProps<{preferences:WorkspacePreferences}>();
const emit=defineEmits<{apply:[value:WorkspacePreferences];edit:[layout:WorkspaceLayout]}>();
const name=ref('我的工作台');
const cottageOpen=ref(false),cottageButton=ref<HTMLButtonElement>();
const CottagePreview=defineAsyncComponent(()=>import('../cottage/CottagePreview.vue'));
async function closeCottage(){cottageOpen.value=false;await nextTick();cottageButton.value?.focus();}
function create(){if(props.preferences.custom.length>=4)return;const layout={...validateLayout(activeLayout(props.preferences)),id:crypto.randomUUID(),name:name.value.trim()};emit('apply',{...props.preferences,activeId:layout.id,custom:[...props.preferences.custom,layout]});}
function rename(id:string,event:Event){const value=(event.target as HTMLInputElement).value;emit('apply',{...props.preferences,custom:props.preferences.custom.map(l=>l.id===id?{...l,name:value}:l)});}
function remove(id:string){emit('apply',{...props.preferences,activeId:props.preferences.activeId===id?'preset:workbench':props.preferences.activeId,custom:props.preferences.custom.filter(l=>l.id!==id)});}
</script>
<template>
 <section class="workspace-settings" aria-labelledby="workspace-settings-title">
  <h2 id="workspace-settings-title">工作台外观</h2><p>四种预设共用你的游戏状态。布局仅保存在当前设备的本机面板中。</p>
  <label class="workspace-theme-control">界面主题 <select data-workspace-theme :value="preferences.theme" @change="emit('apply',{...preferences,theme:($event.target as HTMLSelectElement).value as 'light'|'forest'})"><option value="light">秋日浅色</option><option value="forest">森林深色 · 原有外观</option></select></label>
  <div class="workspace-presets"><button v-for="p in PRESETS" :key="p.id" :data-workspace-preset="p.id" :aria-pressed="preferences.activeId==='preset:'+p.id" @click="emit('apply',{...preferences,activeId:'preset:'+p.id})"><strong>{{ p.name }}</strong><span>{{ p.description }}</span></button></div>
  <h3>自定义工作台 · {{ preferences.custom.length }}/4</h3>
  <div v-for="layout in preferences.custom" :key="layout.id" class="workspace-slot"><input :aria-label="'重命名 '+layout.name" :value="layout.name" maxlength="32" @change="rename(layout.id,$event)"><button :data-use-layout="layout.id" @click="emit('apply',{...preferences,activeId:layout.id})">{{ preferences.activeId===layout.id?'正在使用':'使用' }}</button><button @click="emit('edit',layout)">编辑排列</button><button :data-delete-layout="layout.id" @click="remove(layout.id)">删除布局</button></div>
  <label>新布局名称 <input v-model="name" maxlength="32" aria-label="新布局名称"></label><button data-create-layout :disabled="preferences.custom.length>=4||!name.trim()" @click="create">复制当前布局</button><p v-if="preferences.custom.length>=4">四个位置已满，请先删除一套自定义布局。</p>
  <div class="workspace-switch-row"><SettingSwitch :model-value="preferences.introEnabled" label="下次打开显示开屏动画" @update:model-value="emit('apply',{...preferences,introEnabled:$event})"/></div>
  <AnimationSettings/>
  <p>枫叶小屋先提供独立Demo，确认后再接真实开屏。</p><button ref="cottageButton" data-preview-cottage @click="cottageOpen=true">预览枫叶小屋</button>
  <CottagePreview v-if="cottageOpen" @close="closeCottage"/>
  <button data-reset-workspace @click="emit('apply',{...defaultPreferences(),theme:preferences.theme,custom:preferences.custom,introEnabled:preferences.introEnabled,guideCompleted:preferences.guideCompleted})">恢复默认预设</button>
 </section>
</template>
