<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,ref,watch} from 'vue';
import {GUIDE_STEPS,type GuideStep} from './guide';
import type {LauncherPage} from '../model';
const props=defineProps<{open:boolean;local:boolean;step:GuideStep}>();
const emit=defineEmits<{navigate:[page:LauncherPage];close:[];complete:[]}>();
const index=ref(0),region=ref<HTMLElement>();let previous:HTMLElement|null=null;
const current=computed(()=>GUIDE_STEPS[index.value]);
watch(()=>props.open,async open=>{if(open){previous=document.activeElement as HTMLElement|null;index.value=Math.max(0,GUIDE_STEPS.findIndex(s=>s.id===props.step));await nextTick();region.value?.querySelector<HTMLButtonElement>('button')?.focus();}else previous?.focus();},{immediate:true});
onBeforeUnmount(()=>previous?.focus());
</script>
<template><section v-if="open" ref="region" class="workspace-guide" role="region" aria-label="首次使用指引" @keydown.esc.prevent="emit('close')"><header><span>一起准备第一个世界 · {{index+1}}/{{GUIDE_STEPS.length}}</span><button data-guide-skip @click="emit('close')">跳过指引</button></header><h2>{{current.title}}</h2><p>{{current.description}}</p><p v-if="!local">当前是预览模式：这里仅介绍入口，实际操作请先进入本机模式。</p><p>入口暂不可用或内容尚未加载时，可继续浏览或随时退出。</p><div class="workspace-slot"><button data-guide-previous :disabled="index===0" @click="index--">上一步</button><button data-guide-target @click="emit('navigate',current.page)">前往{{current.title}}</button><button v-if="index<GUIDE_STEPS.length-1" data-guide-next @click="index++">下一步</button><button v-else data-guide-complete @click="emit('complete')">看完了</button></div></section></template>
