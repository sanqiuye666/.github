<script setup lang="ts">
import {MAID_AVATAR as brand} from './workspace/maid';
import {currentEntryContext} from './entry';
import {closePlayerWindow,minimizePlayerWindow,togglePlayerMaximize} from './window';
defineProps<{preview:boolean}>();
const emit=defineEmits<{error:[message:string]}>();
const native=currentEntryContext().native&&!currentEntryContext().embedded;
async function run(action:()=>Promise<void>){try{await action();}catch{emit('error','窗口操作未完成，请重试。');}}
</script>
<template>
  <header class="launcher-titlebar" data-tauri-drag-region @dblclick.self="native&&run(togglePlayerMaximize)">
    <div class="launcher-brand" data-tauri-drag-region><img :src="brand" alt=""/><strong>三秋叶</strong><span>SanQiuYe</span><i></i><small>调度中心</small></div>
    <span class="launcher-titlebar-spacer" data-tauri-drag-region></span>
    <span class="launcher-preview-label">{{preview?'框架预览':'玩家端'}}</span>
    <div class="launcher-window-actions"><button :disabled="!native" aria-label="最小化" @click="run(minimizePlayerWindow)">−</button><button :disabled="!native" aria-label="最大化或还原" @click="run(togglePlayerMaximize)">□</button><button :disabled="!native" class="launcher-window-close" aria-label="关闭启动器" @click="run(closePlayerWindow)">×</button></div>
  </header>
</template>
