<script setup lang="ts">
import WorkspaceCanvas from './workspace/WorkspaceCanvas.vue';
import {computed} from 'vue';
import {createPreset,type WorkspaceLayout} from './workspace/layout';
import {DEFAULT_BACKGROUND_URL} from '../lib/appearance';
import type {LauncherPage} from './model';
const emit=defineEmits<{navigate:[page:LauncherPage];help:[]}>();
const props=defineProps<{local?:boolean;instanceCount?:number;layout?:WorkspaceLayout;selectedIdentityName?:string|null}>();
const homeLayout=computed(()=>props.layout??createPreset('workbench'));
const showHero=computed(()=>homeLayout.value.appearance.showBackground&&!['simple','immersive'].includes(homeLayout.value.preset));
const hero=DEFAULT_BACKGROUND_URL;
</script>
<template>
  <div class="launcher-home">
    <div class="launcher-section-heading"><div><h1>我的工作台</h1><p class="launcher-caption">选择实例，开始今天的冒险。</p></div><span class="launcher-status"><i></i>调度中心未接入</span></div>
    <section v-if="showHero" class="launcher-hero"><img :src="hero" alt="三秋叶默认背景：霓虹城市插画"/><div class="launcher-hero-shade"></div><div class="launcher-hero-copy"><h2>下一段旅程，从这里开始。</h2><div class="launcher-hero-actions"><button class="launcher-primary" data-home-launch @click="emit('navigate','instances')">选择实例启动 <span>→</span></button><button class="launcher-hero-link" @click="emit('navigate','versions')">浏览游戏版本 <span>→</span></button></div></div></section>
    <WorkspaceCanvas :layout="homeLayout" :primary-launch="!showHero" :local="!!local" :instance-count="instanceCount??0" :selected-identity-name="selectedIdentityName??null" @navigate="emit('navigate',$event)" @help="emit('help')"/>
  </div>
</template>
