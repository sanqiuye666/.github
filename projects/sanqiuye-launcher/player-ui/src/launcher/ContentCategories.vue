<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue';
import {operationMessage,type LocalLauncherClient} from './local-api';
import type {ContentCategory} from './content-types';
import {categoryLabel,categoryDescription} from './content-labels';
const props=defineProps<{client:LocalLauncherClient;kind:'mods'|'packs';source:string;modelValue:string;enabled:boolean}>();
const emit=defineEmits<{'update:modelValue':[value:string]}>();
const categories=ref<ContentCategory[]>([]),error=ref(''),loading=ref(false),term=ref('');let sequence=0,controller=new AbortController();
const supported=computed(()=>props.kind==='mods'||props.source==='MODRINTH');
const visible=computed(()=>categories.value.filter(c=>(categoryLabel(c.id,c.name)+' '+c.name+' '+categoryDescription(c.id,c.name)).toLowerCase().includes(term.value.toLowerCase())));
async function load(){controller.abort();controller=new AbortController();const current=++sequence;categories.value=[];error.value='';term.value='';loading.value=false;if(!props.enabled||!supported.value)return;loading.value=true;
 try{const value=await props.client.contentCategories(props.kind,props.source,controller.signal);if(current===sequence)categories.value=Array.isArray(value.categories)?value.categories:[];}catch(e){if(current===sequence&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=operationMessage(e);}finally{if(current===sequence)loading.value=false;}}
watch([()=>props.source,()=>props.enabled],()=>void load(),{immediate:true});
onBeforeUnmount(()=>{++sequence;controller.abort();});
</script>
<template><aside class="launcher-glass content-categories" aria-label="内容分类"><div class="content-category-heading"><h2>分类</h2><button v-if="modelValue" class="launcher-text-button" @click="emit('update:modelValue','')">清除</button></div><p v-if="!supported" class="launcher-caption">此来源暂不支持完整分类筛选，仍可按名称搜索；项目标签以来源详情为准。</p><p v-else-if="!enabled" class="launcher-caption">此来源尚不可用，配置完成后可读取分类。</p><template v-else><label class="content-category-search">查找分类<input v-model="term" placeholder="科技、优化、地图…" maxlength="80"/></label><p v-if="loading" role="status">正在读取来源分类…</p><p v-if="error" role="status">{{error}} <button class="launcher-text-button" @click="load">重试分类</button></p><button class="content-category" :aria-pressed="!modelValue" data-content-category="" @click="emit('update:modelValue','')"><strong>全部分类</strong><small>覆盖此来源的全部内容</small></button><button v-for="item in visible" :key="item.id" class="content-category" :aria-pressed="modelValue===item.id" :data-content-category="item.id" @click="emit('update:modelValue',item.id)"><strong>{{categoryLabel(item.id,item.name)}}</strong><small>{{categoryDescription(item.id,item.name)}}</small></button><p v-if="!loading&&!error&&!visible.length" class="launcher-caption">{{term?'没有匹配的分类':'来源未提供分类'}}</p></template></aside></template>
<style scoped>
.content-categories{padding:20px;min-width:0;align-self:start}.content-category-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.content-category-heading h2{font-size:18px;margin:0}.content-category-search{display:grid;gap:8px;margin:16px 0;color:var(--launcher-muted);font-size:13px}.content-category-search input{min-width:0;width:100%;padding:10px 12px;border:1px solid var(--launcher-line);border-radius:12px;color:inherit;background:transparent}.content-category{display:grid;gap:5px;width:100%;text-align:left;padding:12px;border:0;border-radius:12px;background:transparent;color:inherit;cursor:pointer}.content-category strong{font-size:14px}.content-category small{color:var(--launcher-muted);font-size:12px;line-height:1.5}.content-category[aria-pressed=true]{background:var(--launcher-line);box-shadow:inset 3px 0 var(--launcher-gold)}.content-category:hover{background:var(--launcher-line)}
@media(max-width:1000px){.content-categories{max-height:330px;overflow:auto}}
</style>
