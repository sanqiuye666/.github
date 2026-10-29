<script setup lang="ts">
import type {InstanceListing} from './local-types';
import type {LocalLauncherClient} from './local-api';
import DownloadTasks from './DownloadTasks.vue';
import LaunchCenter from './LaunchCenter.vue';
import InstanceMods from './InstanceMods.vue';
import {computed,ref} from 'vue';
const props=defineProps<{listing:InstanceListing;loading:boolean;error:string;client?:LocalLauncherClient;revision?:number;modsAvailable?:boolean;launchAvailable?:boolean;selectedId?:string;serverTarget?:import('./resource-types').PlayerServerTarget}>();
const modsId=ref(''),modsRevision=ref(0),modTaskRevision=ref(0);const modsInstance=computed(()=>props.listing.instances.find(i=>i.id===modsId.value));
const selected=computed(()=>props.listing.instances.find(instance=>instance.id===props.selectedId));
const loaderNames:Record<string,string>={FABRIC:'Fabric',QUILT:'Quilt',FORGE:'Forge',NEOFORGE:'NeoForge'};
const emit=defineEmits<{refresh:[];'open-versions':[];install:[id:string];verify:[id:string];launch:[id:string];'open-accounts':[];'open-settings':[]}>();
</script>
<template>
  <section class="launcher-local-page"><div class="launcher-section-heading"><div><span class="launcher-overline">YOUR WORLDS</span><h1>每个世界，各有一片天地</h1><p>这里的实例来自本机目录，存档与配置彼此隔离。</p></div><button class="launcher-secondary" data-refresh-instances :disabled="loading" @click="emit('refresh')">刷新实例 ↻</button></div>
    <p v-if="error" class="launcher-inline-notice" role="alert">{{error}}</p><p v-if="loading" class="launcher-local-loading" role="status">正在读取本机实例…</p>
    <div class="launcher-instance-grid"><article v-for="instance in listing.instances" :key="instance.id" :data-instance-id="instance.id" class="launcher-glass launcher-instance-card"><span class="launcher-overline">MINECRAFT JAVA</span><h2>{{instance.name}}</h2><p>{{instance.gameVersion}} <span class="launcher-mini-tag">{{instance.loader==='VANILLA'?'原版':(loaderNames[instance.loader]??instance.loader)+' '+(instance.installation?.loaderVersion??instance.installation?.fabricVersion??'')}}</span></p><div class="launcher-instance-state"><i></i>{{instance.installation?(launchAvailable?'游戏文件已安装 · 可进行启动检查':'游戏文件已安装 · 启动能力待接入'):'目录已准备 · 游戏文件尚未安装'}}</div><small v-if="instance.installation">安装记录 · {{instance.installation.fileCount}} 个文件；可重新校验实际内容</small><small>实例 ID · {{instance.id}}</small><p v-for="problem in instance.installationProblems" :key="problem.installationId" class="launcher-caption">{{problem.reason}}</p><button v-if="instance.installation&&launchAvailable" class="launcher-primary launcher-full" data-open-launch :disabled="!client" @click="emit('launch',instance.id)">启动与日志 →</button><button class="launcher-secondary launcher-full launcher-verify-button" data-plan-install :disabled="!client" @click="emit('install',instance.id)">{{instance.installation?'安装/切换加载器':'安装游戏与加载器'}}</button><button v-if="instance.installation&&modsAvailable" class="launcher-secondary launcher-full launcher-verify-button" data-open-instance-mods :disabled="!client" @click="modsId=instance.id">管理实例模组</button><button v-if="instance.installation" class="launcher-secondary launcher-full launcher-verify-button" data-verify-install :disabled="!client" @click="emit('verify',instance.id)">重新校验文件</button></article></div>
    <div v-if="!loading&&!listing.instances.length" class="launcher-glass launcher-empty"><h2>第一个世界，从这里开始</h2><p>前往版本中心，为你喜欢的版本准备一个独立实例。</p><button class="launcher-primary" @click="emit('open-versions')">选择游戏版本 →</button></div>
    <section v-if="listing.problems.length" class="launcher-inline-notice" role="status"><strong>部分实例记录需要检查，原文件已保留</strong><p v-for="problem in listing.problems" :key="problem.directoryId">{{problem.directoryId}} · {{problem.reason}}</p></section>
    <InstanceMods v-if="modsInstance&&client&&modsAvailable" :key="modsInstance.id" :instance="modsInstance" :client="client" :revision="modsRevision" @submitted="modTaskRevision++;emit('refresh')"/>
    <LaunchCenter v-if="selected?.installation&&client&&launchAvailable" :key="selected.id+':'+(serverTarget?.id??'')+':'+(serverTarget?.revision??'')" :server-target="serverTarget" :instance="selected" :client="client" @open-accounts="emit('open-accounts')" @open-settings="emit('open-settings')"/>
    <DownloadTasks v-if="client" :client="client" :revision="(revision??0)+modTaskRevision" @changed="modsRevision++;emit('refresh')"/>
  </section>
</template>
