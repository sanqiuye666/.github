<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref} from 'vue';
import VersionInstalled from './VersionInstalled.vue';
import './version-center.css';
import {LocalApiError,operationMessage,type LocalLauncherClient} from './local-api';
import type {CatalogPage,GameInstance,OfficialType,OfficialVersion} from './local-types';
const props=defineProps<{client:LocalLauncherClient;instances?:GameInstance[];instancesLoading?:boolean;instancesError?:string}>();
const emit=defineEmits<{created:[instance:GameInstance];manage:[id:string];instances:[]}>();
const pane=ref<'downloads'|'installed'>('downloads');
const query=ref(''),type=ref<OfficialType|''>(''),data=ref<CatalogPage|null>(null),items=ref<OfficialVersion[]>([]);
const busy=ref(false),creating=ref(false),error=ref(''),notice=ref(''),name=ref(''),selected=ref<OfficialVersion|null>(null);
const labels:Record<OfficialType,string>={release:'正式版',snapshot:'快照 / 预发布',old_alpha:'早期 Alpha',old_beta:'早期 Beta'};
const category=computed(()=>type.value.startsWith('old_')?'legacy':type.value||'all');
const categories=[{id:'release',label:'正式版'},{id:'all',label:'全部'},{id:'snapshot',label:'快照'},{id:'legacy',label:'旧版'}] as const;
let sequence=0,request:AbortController|undefined;
let refreshed:Pick<CatalogPage,'snapshotId'|'source'|'warning'|'fetchedAt'>|undefined;
const submittedQuery=ref(''),submittedType=ref<OfficialType|''>('');
const draft=computed(()=>query.value!==submittedQuery.value);
function count(id:string){const counts=data.value?.typeCounts;if(!counts)return null;return id==='all'?data.value!.total:id==='legacy'?counts.old_alpha+counts.old_beta:counts[id as OfficialType];}
function chooseType(value:OfficialType|''){type.value=value;submittedType.value=value;void load(true,false,true);}
function chooseCategory(value:string){chooseType(value==='legacy'?(type.value.startsWith('old_')?type.value:'old_beta'):value==='all'?'':value as OfficialType);}
function date(value:string){const parsed=new Date(value);return Number.isNaN(parsed.getTime())?'时间未提供':parsed.toLocaleString('zh-CN',{hour12:false});}
async function load(reset=true,refresh=false,keepFilter=false){
  request?.abort();const controller=new AbortController();request=controller;const current=++sequence;
  if(reset&&!keepFilter){submittedQuery.value=query.value;submittedType.value=type.value;}
  const params={q:submittedQuery.value,type:submittedType.value,offset:reset?0:items.value.length,limit:50,snapshotId:reset?undefined:data.value?.snapshotId};
  busy.value=true;error.value='';
  if(reset){items.value=[];data.value=null;selected.value=null;}
  try{
    if(refresh){const result=await props.client.refreshCatalog(controller.signal);if(current!==sequence)return;refreshed={snapshotId:result.snapshotId,source:result.source,warning:result.warning,fetchedAt:result.fetchedAt};}
    const result=await props.client.catalog(params,controller.signal);
    if(current!==sequence)return;
    // 同一快照的普通筛选读取不能覆盖本次强制刷新的来源与回退说明。
    if(refreshed?.snapshotId!==result.snapshotId||result.source!=='CACHE'||result.warning)refreshed=undefined;
    data.value=refreshed?{...result,...refreshed}:result;items.value=reset?result.items:[...items.value,...result.items];
  }catch(reason){
    if(current!==sequence)return;
    if(reason instanceof LocalApiError&&reason.code==='CATALOG_CHANGED'&&!reset){notice.value='官方目录已更新，已重新载入。';void load(true,false,true);return;}
    if(!(reason instanceof DOMException&&reason.name==='AbortError'))error.value=operationMessage(reason);
  }finally{if(current===sequence)busy.value=false;}
}
function refresh(){notice.value='';void load(true,true,true);}
async function create(){
  if(creating.value||!selected.value||!data.value)return;
  if(!name.value.trim()||Array.from(name.value.trim()).length>64){error.value='实例名称需要 1–64 个字符';return;}
  creating.value=true;error.value='';
  try{
    const result=await props.client.createInstance({name:name.value.trim(),versionId:selected.value.id,snapshotId:data.value.snapshotId});
    notice.value='实例已准备，游戏文件尚未安装。';emit('created',result.instance);
  }catch(reason){error.value=operationMessage(reason);}finally{creating.value=false;}
}
onMounted(()=>load());onBeforeUnmount(()=>{++sequence;request?.abort();});
</script>
<template>
  <section class="launcher-local-page version-center">
    <div class="launcher-section-heading"><div><span class="launcher-overline">MINECRAFT JAVA</span><h1>游戏版本</h1><p>浏览官方目录，为不同玩法准备独立实例。</p></div></div>
    <div class="version-tabs" aria-label="版本中心视图"><button data-version-tab="downloads" :aria-pressed="pane==='downloads'" @click="pane='downloads'">版本下载</button><button data-version-tab="installed" :aria-pressed="pane==='installed'" @click="pane='installed'">已安装</button></div>
    <VersionInstalled v-if="pane==='installed'" :instances="props.instances??[]" :loading="instancesLoading" :error="instancesError" @manage="emit('manage',$event)" @instances="emit('instances')" @browse="pane='downloads'"/>
    <template v-else>
    <form class="version-search" data-version-search @submit.prevent="load(true)"><input v-model="query" data-version-query aria-label="搜索游戏版本" placeholder="搜索版本号，例如 1.20.6" maxlength="256"/><button class="launcher-secondary" type="submit" :disabled="busy">搜索</button><button class="launcher-secondary" type="button" :disabled="busy" data-refresh-catalog @click="refresh">刷新官方目录</button></form>
    <div class="version-categories" aria-label="版本类型"><button v-for="item in categories" :key="item.id" :data-version-category="item.id" :aria-pressed="category===item.id" @click="chooseCategory(item.id)">{{item.label}}<small v-if="count(item.id)!==null">{{count(item.id)}}</small></button></div>
    <div v-if="category==='legacy'" class="version-legacy" aria-label="旧版子类"><span>旧版类型</span><button v-for="key in (['old_beta','old_alpha'] as const)" :key="key" :data-version-legacy="key" :aria-pressed="type===key" @click="chooseType(key)">{{labels[key]}} · {{data?.typeCounts[key]??'…'}}</button><small>分别查看官方早期 Beta 与 Alpha。</small></div>
    <p v-if="draft&&data" class="launcher-caption" role="status">搜索尚未提交，列表仍使用上次搜索条件。</p>
    <div v-if="data" class="launcher-local-meta"><span>官方目录 {{data.total}} 个版本 · 当前筛选 {{data.filteredTotal}} 个</span><span>{{data.source==='MOCK'?'MOCK · 离线示例':data.source==='NETWORK'?'官方实时数据':data.source==='STALE_CACHE'?'缓存回退':'本机有效缓存'}} · 获取于 {{date(data.fetchedAt)}}</span></div>
    <p v-if="data?.warning" class="launcher-inline-notice" role="status">{{data.warning}}</p><p v-if="error" class="launcher-inline-notice" role="alert">{{error}}</p><p v-if="notice" class="launcher-inline-notice" role="status">{{notice}}</p>
    <div class="launcher-version-layout"><div><div class="launcher-version-grid"><button v-for="version in items" :key="version.id" :data-version-id="version.id" :data-official-type="version.type" class="launcher-version-row launcher-glass" :class="{selected:selected?.id===version.id}" :aria-pressed="selected?.id===version.id" @click="selected=version;name=name||`我的世界 ${version.id}`"><span class="version-row-content"><strong>{{version.id}}</strong><small><span class="version-type-label">{{labels[version.type]}}</span>发布于 {{date(version.releaseTime)}}</small></span><span class="launcher-version-arrow">{{selected?.id===version.id?'已选择':'选择'}}</span></button></div><div v-if="busy" class="launcher-local-loading" role="status">正在读取官方目录…</div><p v-else-if="!items.length" class="launcher-local-loading">没有符合条件的版本。</p><button v-if="data&&items.length<data.filteredTotal" class="launcher-secondary launcher-full launcher-load-more" data-load-more :disabled="busy" @click="load(false)">加载更多 · 已显示 {{items.length}} / {{data.filteredTotal}}</button></div>
      <aside class="launcher-glass launcher-selection"><span class="launcher-overline">独立实例</span><h2>{{selected?.id||'选择你的版本'}}</h2><p>存档与配置拥有自己的目录。准备后，到实例库安装原版或加载器。</p><form data-create-instance @submit.prevent="create"><label>实例名称<input v-model="name" data-instance-name maxlength="128" placeholder="为这个世界起个名字" :disabled="!selected||creating"/></label><button type="submit" class="launcher-primary launcher-full" :disabled="!selected||creating||!name.trim()">{{creating?'正在准备…':'准备此版本实例'}} →</button></form><small>创建不会自动下载或启动游戏。</small></aside>
    </div>
    </template>
  </section>
</template>
