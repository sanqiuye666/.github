<script setup lang="ts">
import {computed,ref} from 'vue';
import type {GameInstance} from './local-types';
const props=defineProps<{instances:GameInstance[];loading?:boolean;error?:string}>();
const emit=defineEmits<{manage:[id:string];instances:[];browse:[]}>();
const query=ref('');
const installed=computed(()=>props.instances.filter(i=>i.installation));
const items=computed(()=>installed.value.filter(i=>`${i.name} ${i.gameVersion} ${i.loader} ${i.installation?.loaderVersion??i.installation?.fabricVersion??''}`.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase())));
const prepared=computed(()=>props.instances.length-installed.value.length);
</script>
<template>
 <section aria-label="已安装版本">
  <div class="version-installed-toolbar"><label>搜索已安装实例<input v-model="query" data-installed-query placeholder="名称、版本或加载器"/></label><button class="launcher-secondary" @click="emit('instances')">全部实例管理</button></div>
  <p v-if="loading" role="status">正在读取本机安装记录…</p><p v-if="error" class="launcher-inline-notice" role="alert">{{error}}</p>
  <p v-if="!loading&&!error" class="launcher-caption">{{installed.length}} 个实例有安装记录<span v-if="prepared"> · {{prepared}} 个实例尚未安装</span>。实际文件可在实例管理中重新校验。</p>
  <div class="version-installed-list"><article v-for="instance in items" :key="instance.id" class="launcher-glass" :data-installed-version="instance.id"><div><h2>{{instance.name}}</h2><p>{{instance.gameVersion}} · {{instance.loader==='VANILLA'?'原版':instance.loader}} {{instance.installation?.loaderVersion??instance.installation?.fabricVersion??''}}</p><small>{{instance.installation?.fileCount}} 个文件的安装记录</small></div><button class="launcher-secondary" :data-manage-installed="instance.id" @click="emit('manage',instance.id)">管理实例</button></article></div>
  <div v-if="!loading&&!error&&!items.length" class="launcher-glass launcher-empty"><h2>{{installed.length?'没有匹配的已安装实例':'还没有已安装的版本'}}</h2><p>{{installed.length?'调整名称、版本或加载器关键词。':'先选择一个游戏版本，准备实例并完成安装。'}}</p><button class="launcher-secondary" @click="emit('browse')">浏览版本下载</button></div>
 </section>
</template>
