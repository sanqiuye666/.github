<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref} from 'vue';
import {LocalApiError,operationMessage,type LocalLauncherClient} from './local-api';
import type {ModFileRef,ModPage,ModProject,ModSource,OfficialVersion,SourceStatus,TaskSnapshot} from './local-types';
import InstanceModDialog from './InstanceModDialog.vue';
import ModFiles from './ModFiles.vue';
import ModLibrary from './ModLibrary.vue';
import DownloadTasks from './DownloadTasks.vue';
import ContentCategories from './ContentCategories.vue';
import ContentProjectCard from './ContentProjectCard.vue';
import ProjectIntroduction from './ProjectIntroduction.vue';
import './content-discovery.css';
const props=defineProps<{client:LocalLauncherClient;installAvailable?:boolean}>();
const installRef=ref<ModFileRef|null>(null),category=ref('');
const source=ref<ModSource>('MODRINTH'),sources=ref<SourceStatus[]>([]),query=ref(''),gameVersion=ref(''),loader=ref(''),sort=ref<'RELEVANCE'|'DOWNLOADS'|'UPDATED'>('RELEVANCE');
const page=ref<ModPage|null>(null),offset=ref(0),versions=ref<OfficialVersion[]>([]),versionsBusy=ref(true),busy=ref(false),error=ref(''),notice=ref(''),expired=ref(false),selected=ref<ModProject|null>(null),view=ref<'search'|'library'>('search'),taskRevision=ref(0),libraryRevision=ref(0),now=ref(Date.now());
const current=computed(()=>sources.value.find(s=>s.source===source.value));const cooldowns=ref<Record<ModSource,number>>({MODRINTH:0,CURSEFORGE:0});const cooldown=computed(()=>Math.max(0,Math.ceil((cooldowns.value[source.value]-now.value)/1000)));const available=computed(()=>current.value?.state==='AVAILABLE'&&!expired.value);
let sequence=0,detailSequence=0,searchController=new AbortController(),detailController=new AbortController(),tick:ReturnType<typeof setInterval>|undefined;const lifetime=new AbortController();
let submittedFilter={category:'',q:'',gameVersion:'',loader:'',sort:sort.value};
function invalidateDetail(){++detailSequence;detailController.abort();}
function selectProject(project:ModProject){selected.value=project;void openProject({source:project.source,projectId:project.projectId});}
function failure(e:unknown){error.value=operationMessage(e);if(e instanceof LocalApiError){if(e.status===401||e.status===403)expired.value=true;if(e.status===429)rateLimit(e.retryAfterSeconds??60);}}
function rateLimit(seconds:number){now.value=Date.now();cooldowns.value={...cooldowns.value,[source.value]:Date.now()+seconds*1000};}
function reset(){++sequence;invalidateDetail();searchController.abort();page.value=null;selected.value=null;offset.value=0;busy.value=false;error.value='';notice.value='';}
function changeSource(value:ModSource,load=true){if(value===source.value)return;reset();source.value=value;loader.value='';category.value='';if(load)void search(true,0,true);}
async function loadVersions(){
 try{for(let attempt=0;attempt<2;attempt++){const all:OfficialVersion[]=[];let snapshot:string|undefined;try{while(true){const value=await props.client.catalog({offset:all.length,limit:100,snapshotId:snapshot},lifetime.signal);snapshot=value.snapshotId;all.push(...value.items);if(all.length>=value.total)break;if(!value.items.length)throw new Error('官方版本分页未完成');}versions.value=all;return;}catch(e){if(e instanceof LocalApiError&&e.code==='CATALOG_CHANGED'&&attempt===0)continue;throw e;}}}catch(e){failure(e);}finally{versionsBusy.value=false;}
}
async function search(resetPage=true,change=0,automatic=false){if(!available.value||Date.now()<cooldowns.value[source.value])return;invalidateDetail();searchController.abort();searchController=new AbortController();const currentSequence=++sequence,targetSource=source.value,next=resetPage?0:Math.max(0,offset.value+change*20);busy.value=true;error.value='';selected.value=null;if(resetPage){page.value=null;submittedFilter={category:category.value,q:automatic?submittedFilter.q:query.value,gameVersion:gameVersion.value,loader:loader.value,sort:sort.value};}
 try{const result=await props.client.searchMods({source:targetSource,...submittedFilter,offset:next,limit:20},searchController.signal);if(currentSequence===sequence&&targetSource===source.value){page.value=result;offset.value=next;}}
 catch(e){if(currentSequence===sequence&&!(e instanceof DOMException&&e.name==='AbortError'))failure(e);}finally{if(currentSequence===sequence)busy.value=false;}}
async function openProject(ref:{source:ModSource;projectId:string}){if(expired.value)return;changeSource(ref.source,false);view.value='search';detailController.abort();detailController=new AbortController();const current=++detailSequence;try{const result=await props.client.modProject(ref.source,ref.projectId,detailController.signal);if(current===detailSequence&&ref.source===source.value)if(result.project?.projectId===ref.projectId&&result.project.source===ref.source)selected.value=result.project;}catch(e){if(current===detailSequence)failure(e);}}
function submitted(_task:TaskSnapshot){++taskRevision.value;installRef.value=null;notice.value='已加入后台任务，请查看下载与校验结果。';}
onMounted(()=>{void loadVersions();void props.client.modSources(lifetime.signal).then(value=>{if(lifetime.signal.aborted)return;sources.value=value.sources;void search(true,0,true);}).catch(e=>{if(!lifetime.signal.aborted)failure(e);});tick=setInterval(()=>now.value=Date.now(),1000);});
onBeforeUnmount(()=>{++sequence;++detailSequence;lifetime.abort();searchController.abort();detailController.abort();clearInterval(tick);});
</script>
<template>
 <section class="launcher-local-page"><div class="launcher-section-heading"><div><span class="launcher-overline">MOD DISCOVERY</span><h1>让世界，多一些可能</h1><p>从官方来源寻找模组，收藏到本机库，或选择兼容实例与必需依赖后装入游戏。</p></div><div class="launcher-mod-view-tabs"><button class="launcher-secondary" :class="{selected:view==='search'}" data-mod-search-tab @click="view='search'">发现模组</button><button class="launcher-secondary" :class="{selected:view==='library'}" data-mod-library-tab @click="view='library'">我的模组库</button></div></div>
 <template v-if="view==='search'"><div class="launcher-mod-sources"><button v-for="item in (['MODRINTH','CURSEFORGE'] as const)" :key="item" class="launcher-glass" :class="{selected:source===item}" :data-mod-source="item" @click="changeSource(item)"><strong>{{item==='MODRINTH'?'Modrinth':'CurseForge'}}</strong><small>{{sources.find(s=>s.source===item)?.state==='AVAILABLE'?'官方读取接口':sources.find(s=>s.source===item)?.reason||'正在检查来源…'}}</small></button></div><p v-if="current?.reason" class="launcher-inline-notice" role="status">{{current.reason}}</p><p v-if="current?.warning" class="launcher-inline-notice" role="status">{{current.warning}}</p>
 <div v-show="!selected" class="content-discovery-grid"><main class="content-discovery-main"><form class="launcher-glass content-discovery-form" data-mod-search @submit.prevent="search()"><label class="content-search-field">模组关键词<input v-model="query" data-mod-query placeholder="搜索 Sodium、地图、性能优化…" maxlength="256"/></label><label>游戏版本<select v-model="gameVersion" data-mod-game-version :disabled="versionsBusy||expired" @change="reset()"><option value="">{{versionsBusy?'正在读取全部官方版本…':'全部游戏版本'}}</option><option v-for="version in versions" :key="version.id" :value="version.id">{{version.id}}</option></select></label><label>加载器<select v-model="loader" data-mod-loader :disabled="!available||(source==='CURSEFORGE'&&!gameVersion)" @change="reset()"><option value="">全部加载器</option><option v-for="tag in current?.loaders" :key="tag" :value="tag">{{tag}}</option></select></label><label>排序<select v-model="sort" data-mod-sort :disabled="!available" @change="reset()"><option value="RELEVANCE">相关程度</option><option value="DOWNLOADS">下载量</option><option value="UPDATED">最近更新</option></select></label><button class="launcher-primary" type="submit" data-search-mods :disabled="!available||busy||cooldown>0">{{cooldown?`等待 ${cooldown} 秒`:busy?'查询中…':'搜索模组'}} →</button></form>
 <p class="content-filter-summary">进入页面自动读取目录。分类、版本和加载器可组合筛选，修改后点击搜索应用；下载前需明确兼容条件。</p>
 <p v-if="busy" role="status" data-discovery-loading>正在读取模组目录…</p>
 <p v-if="page" class="launcher-local-meta">来源共 {{page.total}} 个结果 <span v-if="page.platformLimited">· 平台仅允许访问前 {{page.accessibleTotal}} 个</span></p>
 <div class="content-discovery-results"><ContentProjectCard v-for="project in page?.items" :key="project.source+project.projectId" :data-mod-project="project.projectId" :title="project.title||project.projectId" :summary="project.summary" :source="project.source==='MODRINTH'?'Modrinth':'CurseForge'" :author="project.author" :downloads="project.downloads" :presentation="project.presentation" @click="selectProject(project)"/></div>
 <p v-if="page&&!page.items.length" class="launcher-caption">当前条件没有结果。可以修改筛选后重新查询。</p>
 <div v-if="page&&page.accessibleTotal>20" class="launcher-task-pagination"><button class="launcher-secondary" :disabled="!offset||busy||cooldown>0||expired" @click="search(false,-1)">上一页</button><span>{{offset+1}}–{{Math.min(offset+20,page.accessibleTotal)}} / {{page.accessibleTotal}}</span><button class="launcher-secondary" data-mod-next :disabled="offset+20>=page.accessibleTotal||busy||cooldown>0||expired" @click="search(false,1)">下一页</button></div>
 <section v-if="!page&&!busy&&available&&!error" class="launcher-glass launcher-empty"><h2>找到适合你的下一种玩法</h2><p>选择分类或输入关键词，查看官方项目。</p></section>
 </main><ContentCategories :client="client" kind="mods" :source="source" :enabled="!!available" :model-value="category" @update:model-value="category=$event;reset()"/></div>
 <p v-if="error" class="launcher-inline-notice" role="alert">{{error}}</p><p v-if="notice" class="launcher-inline-notice" role="status">{{notice}}</p>
 <ProjectIntroduction v-if="selected" :key="selected.source+selected.projectId" :title="selected.title||selected.projectId" :summary="selected.summary" :source="selected.source==='MODRINTH'?'Modrinth':'CurseForge'" :author="selected.author" :downloads="selected.downloads" :presentation="selected.presentation" @close="invalidateDetail();selected=null"/>
 <ModFiles v-if="selected" :key="selected.source+selected.projectId" :client="client" :project="selected" :game-version="gameVersion" :loader="loader" :install-available="installAvailable" @install="installRef=$event" @submitted="submitted" @open-project="openProject" @rate-limit="rateLimit"/></template>
 <ModLibrary v-else :client="client" :revision="libraryRevision" :install-available="installAvailable" @install="installRef=$event" @submitted="submitted" @open-project="openProject"/>
 <InstanceModDialog v-if="installRef" :client="client" :ref-value="installRef" @close="installRef=null" @submitted="submitted"/>
 <DownloadTasks :client="client" :revision="taskRevision" @changed="libraryRevision++"/>
 </section>
</template>
