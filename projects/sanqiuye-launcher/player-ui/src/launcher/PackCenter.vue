<script setup lang="ts">
import {computed,onMounted,onBeforeUnmount,ref} from 'vue';
import {LocalApiError,operationMessage,type LocalLauncherClient} from './local-api';
import ContentCategories from './ContentCategories.vue';
import ContentProjectCard from './ContentProjectCard.vue';
import ProjectIntroduction from './ProjectIntroduction.vue';
import './content-discovery.css';
import type {PackSource,PackSourceStatus,PackProject,PackPage,PackDetails} from './pack-types';
const props=defineProps<{client:LocalLauncherClient}>();
const sources=ref<PackSourceStatus[]>([]),source=ref<PackSource>('MODRINTH'),query=ref(''),page=ref<PackPage|null>(null),detail=ref<PackDetails|null>(null);
const category=ref(''),gameVersion=ref(''),loader=ref(''),sort=ref('RELEVANCE');
const error=ref(''),busy=ref(false),detailBusy=ref(false),expired=ref(false),now=ref(Date.now()),cooldowns=ref<Partial<Record<PackSource,number>>>({});
const current=computed(()=>sources.value.find(item=>item.source===source.value));
const cooldown=computed(()=>Math.max(0,Math.ceil(((cooldowns.value[source.value]??0)-now.value)/1000)));
const available=computed(()=>current.value?.state==='AVAILABLE'&&!expired.value);
let sequence=0,detailSequence=0,submitted={q:'',category:'',gameVersion:'',loader:'',sort:'RELEVANCE'},searchController=new AbortController(),detailController=new AbortController();
const lifetime=new AbortController();let tick:ReturnType<typeof setInterval>|undefined;
function abortDetail(){++detailSequence;detailController.abort();detailBusy.value=false;detail.value=null;}
function changeSource(){category.value='';gameVersion.value='';loader.value='';sort.value='RELEVANCE';++sequence;searchController.abort();busy.value=false;page.value=null;error.value='';abortDetail();void search(0,true,true);}
function failure(failure:unknown,target:PackSource){error.value=operationMessage(failure);if(failure instanceof LocalApiError){if(failure.status===401||failure.status===403)expired.value=true;if(failure.status===429){now.value=Date.now();cooldowns.value={...cooldowns.value,[target]:now.value+Math.max(1,Math.min(3600,failure.retryAfterSeconds??60))*1000};}}}
function cancelled(failure:unknown){return failure instanceof DOMException&&failure.name==='AbortError';}
async function search(offset=0,submit=true,automatic=false){
 if(!available.value||busy.value||cooldown.value)return;
 abortDetail();searchController.abort();searchController=new AbortController();const version=++sequence,target=source.value;busy.value=true;error.value='';if(submit){submitted={q:automatic?submitted.q:query.value,category:category.value,gameVersion:gameVersion.value,loader:loader.value,sort:sort.value};page.value=null;}
 try{const result=await props.client.searchPacks({source:target,...submitted,offset,limit:20},searchController.signal);if(version===sequence&&target===source.value)page.value=result;}
 catch(e){if(version===sequence&&!cancelled(e))failure(e,target);}finally{if(version===sequence)busy.value=false;}
}
async function select(project:PackProject){
 if(expired.value||cooldown.value)return;
 abortDetail();detailController=new AbortController();const version=++detailSequence,target=source.value;detailBusy.value=true;error.value='';
 try{const result=await props.client.packDetails(project.source,project.id,detailController.signal);if(version===detailSequence&&target===source.value)detail.value=result;}
 catch(e){if(version===detailSequence&&!cancelled(e))failure(e,target);}finally{if(version===detailSequence)detailBusy.value=false;}
}
onMounted(()=>{void props.client.packSources(lifetime.signal).then(result=>{if(lifetime.signal.aborted)return;sources.value=result.sources;void search(0,true,true);}).catch(e=>{if(!lifetime.signal.aborted)failure(e,source.value);});tick=setInterval(()=>now.value=Date.now(),1000);});
onBeforeUnmount(()=>{++sequence;++detailSequence;lifetime.abort();searchController.abort();detailController.abort();clearInterval(tick);});
</script>
<template>
 <section class="launcher-local-page launcher-pack-center">
  <div class="launcher-section-heading"><div><span class="launcher-overline">MODPACK DISCOVERY</span><h1>下一站，新的世界</h1><p>从官方目录寻找整合包，查看它支持的游戏版本。</p></div></div>
  <div v-show="!detail" class="content-discovery-grid"><main class="content-discovery-main"><form class="launcher-glass content-discovery-form" @submit.prevent="search()">
   <label>整合包来源<select v-model="source" data-pack-source @change="changeSource"><option v-for="item in sources" :key="item.source" :value="item.source">{{item.name}}{{item.state==='PENDING'?' · 待接入':''}}</option></select></label>
   <label class="content-search-field">搜索整合包<input v-model="query" data-pack-query maxlength="128" placeholder="整合包名称或关键词"/></label>
   <template v-if="source==='MODRINTH'"><label>游戏版本<input v-model="gameVersion" data-pack-game-version placeholder="全部版本，如 1.20.6" maxlength="128"/></label><label>加载器<select v-model="loader" data-pack-loader><option value="">全部加载器</option><option v-for="v in ['fabric','forge','neoforge','quilt']" :key="v" :value="v">{{v}}</option></select></label><label>排序<select v-model="sort" data-pack-sort><option value="RELEVANCE">相关程度</option><option value="DOWNLOADS">下载量</option><option value="UPDATED">最近更新</option></select></label></template>
   <button class="launcher-primary" data-pack-search type="submit" :disabled="!available||busy||cooldown>0">{{cooldown?`等待 ${cooldown} 秒`:busy?'查询中…':'搜索整合包'}}</button>
  </form>
  <p class="launcher-caption" role="status">{{current?.reason??'正在读取来源…'}} · 目录和版本查询；安装待接入。</p>
  <p v-if="error" class="launcher-inline-notice" role="alert">{{error}}</p>
  <p v-if="busy" role="status" data-discovery-loading>正在读取整合包目录…</p>
  <p v-if="page" class="launcher-local-meta">{{page.provenance==='MOCK'?'MOCK · 本地示例':page.provenance==='CACHE'?'缓存目录':'本次官方查询'}} · {{page.total}} 个结果 <span v-if="page.warning">· {{page.warning}}</span></p>
  <p v-if="page&&(query!==submitted.q||category!==submitted.category||gameVersion!==submitted.gameVersion||loader!==submitted.loader||sort!==submitted.sort)" class="launcher-caption">筛选已修改；当前仍显示上次搜索结果，点击搜索后应用。</p>
  <div class="content-discovery-results"><ContentProjectCard v-for="project in page?.items" :key="project.source+project.id" :data-pack-project="project.id" :disabled="expired||cooldown>0||detailBusy" :title="project.name" :summary="project.summary" :source="sources.find(item=>item.source===project.source)?.name??project.source" :presentation="project.presentation" :loaders="project.loaders" @click="select(project)"/></div>
  <p v-if="page&&!page.items.length" class="launcher-caption">当前条件没有结果，可修改关键词重新查询。</p>
  <div v-if="page&&page.total>20" class="launcher-task-pagination"><button class="launcher-secondary" :disabled="busy||!page.offset||expired||cooldown>0" @click="search(Math.max(0,page.offset-20),false)">上一页</button><span>{{page.offset+1}}–{{Math.min(page.offset+page.items.length,page.total)}} / {{page.total}}</span><button class="launcher-secondary" data-pack-next :disabled="busy||page.offset+20>=page.total||page.offset+20>10000||expired||cooldown>0" @click="search(page.offset+20,false)">下一页</button></div>
  </main><ContentCategories :client="client" kind="packs" :source="source" :enabled="!!available" v-model="category"/></div>
  <p v-if="detail&&error" class="launcher-inline-notice" role="alert">{{error}}</p>
  <p v-if="detailBusy" role="status">正在读取公开版本…</p>
  <ProjectIntroduction v-if="detail" :key="detail.project.source+detail.project.id" :title="detail.project.name" :summary="detail.project.summary" :source="source" :presentation="detail.project.presentation" @close="abortDetail"/>
  <section v-if="detail" class="launcher-glass launcher-pack-detail" aria-label="整合包版本"><div class="launcher-section-heading"><div><span class="launcher-overline">{{detail.provenance==='MOCK'?'MOCK · 本地示例详情':detail.provenance==='CACHE'?'缓存详情':'本次官方详情'}}</span><h2>{{detail.project.name}}</h2></div><button class="launcher-secondary" @click="abortDetail">关闭版本</button></div><p v-if="detail.warning" class="launcher-caption">{{detail.warning}}</p><p v-if="!detail.versions.length">来源未提供可显示的公开版本。</p><div class="launcher-pack-versions"><article v-for="version in detail.versions" :key="version.id"><strong>{{version.name}}</strong><span>Minecraft {{version.gameVersions.join(' / ')||'未提供'}} · {{version.loaders.join(' / ')||'加载器未提供'}}</span><small>{{version.publishedAt?.slice(0,10)??'日期未提供'}}</small></article></div><p class="launcher-caption">安装待接入。此处查询不会下载文件或修改游戏实例。</p></section>
 </section>
</template>
<style scoped>
.launcher-pack-detail{margin-top:24px;padding:24px}.launcher-pack-versions{display:grid;max-height:480px;overflow:auto}.launcher-pack-versions article{display:grid;gap:6px;padding:15px 0;border-bottom:1px solid var(--launcher-line)}.launcher-pack-versions span,.launcher-pack-versions small{color:var(--launcher-muted);overflow-wrap:anywhere}
</style>
