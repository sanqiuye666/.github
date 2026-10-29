<script setup lang="ts">
import {computed,ref} from 'vue';
import AppIcon from '../components/AppIcon.vue';
import {LAUNCHER_PAGES,type LauncherPage} from './model';
import {PENDING_RESOURCE_IDS,RESOURCE_PAGE_IDS} from './navigation';
import './resource-navigation.css';
const props=defineProps<{page:LauncherPage;capabilities?:Record<string,string>}>();
const emit=defineEmits<{navigate:[page:LauncherPage]}>();
const expanded=ref(true),warning=ref('');
const key='sqy.launcher.resources.expanded.v1';
function pending(id:LauncherPage){if(id==='servers')return props.capabilities?.playerServers!=='AVAILABLE';if(['resourcepacks','shaders','recordings','schematics'].includes(id))return props.capabilities?.resources!=='AVAILABLE';return PENDING_RESOURCE_IDS.includes(id);}
try{const value=window.localStorage.getItem(key);if(value==='0')expanded.value=false;else if(value!==null&&value!=='1')warning.value='资源导航偏好无法读取，已展开；可重新选择。';}catch{warning.value='资源导航偏好无法读取，仍可临时使用。';}
const primary=LAUNCHER_PAGES.filter(p=>!RESOURCE_PAGE_IDS.includes(p.id));
const resources=RESOURCE_PAGE_IDS.map(id=>LAUNCHER_PAGES.find(p=>p.id===id)!);
const selectedResource=computed(()=>resources.find(p=>p.id===props.page));
function toggle(){expanded.value=!expanded.value;try{window.localStorage.setItem(key,expanded.value?'1':'0');warning.value='';}catch{warning.value='资源导航偏好未保存，本次展开状态仍可使用。';}}
</script>
<template>
 <nav class="launcher-resource-navigation" aria-label="启动器导航">
  <template v-for="item in primary" :key="item.id">
   <button :data-launcher-page="item.id" :class="{active:page===item.id}" :aria-current="page===item.id?'page':undefined" :title="item.label" @click="emit('navigate',item.id)"><AppIcon :name="item.icon"/><span>{{item.label}}</span></button>
   <div v-if="item.id==='versions'" class="launcher-resource-group">
    <button class="launcher-resource-toggle" data-resource-toggle :class="{active:selectedResource&&!expanded}" :aria-expanded="expanded" aria-controls="launcher-resource-pages" title="展开或收起资源管理" @click="toggle"><AppIcon name="folder"/><span>资源管理<small v-if="selectedResource&&!expanded" class="launcher-resource-current">当前：{{selectedResource.label}}</small></span><AppIcon class="resource-chevron" :class="{expanded}" name="chevron"/></button>
    <div v-if="expanded" id="launcher-resource-pages" class="launcher-resource-children" aria-label="资源管理子项">
     <button v-for="resource in resources" :key="resource.id" :data-launcher-page="resource.id" :class="{active:page===resource.id}" :aria-current="page===resource.id?'page':undefined" :title="resource.label+(pending(resource.id)?' · 待接入':'')" @click="emit('navigate',resource.id)"><AppIcon :name="resource.icon"/><span>{{resource.label}}</span><small v-if="pending(resource.id)" class="resource-pending">待接入</small></button>
    </div>
   </div>
  </template>
  <p v-if="warning" class="resource-navigation-warning" role="status">{{warning}}</p>
 </nav>
</template>
