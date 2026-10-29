<script setup lang="ts">
import type {WorkspaceLayout} from './layout';
import type {LauncherPage} from '../model';
import WorkspaceWidgets from './WorkspaceWidgets.vue';
defineProps<{layout:WorkspaceLayout;local:boolean;instanceCount:number;selectedIdentityName:string|null;primaryLaunch?:boolean}>();
const emit=defineEmits<{navigate:[page:LauncherPage];help:[]}>();
</script>
<template><div class="workspace-boundary"><p v-if="layout.arrangement==='free'" class="workspace-compact-note">当前窗口或字号下采用单列显示，原自由坐标保留。</p><div class="workspace-canvas" :data-workspace-layout="layout.preset"><section v-for="p in layout.placements" :key="p.widget" :data-widget="p.widget" class="workspace-widget" :style="{gridColumn:`${p.x+1} / span ${p.width}`,gridRow:`${p.y+1} / span ${p.height}`}"><WorkspaceWidgets :widget="p.widget" :primary-launch="primaryLaunch??true" :local="local" :instance-count="instanceCount" :selected-identity-name="selectedIdentityName" @navigate="emit('navigate',$event)" @help="emit('help')"/></section></div></div></template>
