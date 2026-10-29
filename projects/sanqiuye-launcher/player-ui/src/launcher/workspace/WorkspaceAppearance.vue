<script setup lang="ts">
import {onMounted,ref} from 'vue';
import WorkspaceSettings from './WorkspaceSettings.vue';
import type {WorkspaceLayout,WorkspacePreferences} from './layout';
defineProps<{preferences:WorkspacePreferences;warning:string|null}>();
const emit=defineEmits<{apply:[value:WorkspacePreferences];edit:[layout:WorkspaceLayout];close:[]}>();
const dialog=ref<HTMLElement>();
function key(event:KeyboardEvent){
 if(event.key==='Escape'){event.preventDefault();emit('close');}
 if(event.key==='Tab'){
  const items=Array.from(dialog.value?.querySelectorAll<HTMLElement>('button:not(:disabled),input,select')??[]);
  if(event.shiftKey&&document.activeElement===items[0]){event.preventDefault();items.at(-1)?.focus();}
  else if(!event.shiftKey&&document.activeElement===items.at(-1)){event.preventDefault();items[0]?.focus();}
 }
}
onMounted(()=>dialog.value?.querySelector<HTMLElement>('button')?.focus());
</script>
<template>
 <div class="workspace-overlay"><section ref="dialog" class="workspace-editor workspace-appearance" role="dialog" aria-modal="true" aria-label="工作台外观" @keydown="key">
  <button data-close-appearance @click="emit('close')">关闭外观设置</button>
  <p v-if="warning" class="workspace-warning" role="alert">{{warning}}</p>
  <WorkspaceSettings :preferences="preferences" @apply="emit('apply',$event)" @edit="emit('edit',$event)"/>
 </section></div>
</template>
