<script setup lang="ts">
import {defineAsyncComponent,nextTick,onBeforeUnmount,onMounted,ref} from 'vue';
import {currentEntryContext,readPlayerEntryMode,resolveEntryMode,type EntryMode} from './launcher/entry';
import {closePlayerWindow,showPlayerWindow} from './launcher/window';
import LauncherApp from './launcher/LauncherApp.vue';

const Updater=defineAsyncComponent(()=>import('./App.vue').then(module=>module.default));
const mode=ref<EntryMode|null>(null),error=ref('');
const updaterOpen=ref(false),updaterLoaded=ref(false);
function previewUpdater(){if(mode.value!=='launcher'||currentEntryContext().native)return;updaterLoaded.value=true;updaterOpen.value=true;}
let alive=true,generation=0;
async function start(){
  const request=++generation;
  error.value='';mode.value=null;
  try {
    const resolved=await resolveEntryMode(currentEntryContext(),readPlayerEntryMode);
    if(!alive||request!==generation)return;
    mode.value=resolved;
    if(resolved==='launcher'){await nextTick();if(alive&&request===generation)await showPlayerWindow();}
  } catch(failure){
    if(!alive||request!==generation)return;
    mode.value=null;error.value=failure instanceof Error?failure.message:'无法读取启动上下文';
    await nextTick();
    if(alive&&request===generation)await showPlayerWindow().catch(()=>{error.value+='。窗口显示失败，请重新打开程序。';});
  }
}
function close(){void closePlayerWindow().catch(()=>{error.value='窗口未能关闭，请重试。';});}
onMounted(()=>{void start();});
onBeforeUnmount(()=>{alive=false;generation++;});
</script>
<template>
  <section v-if="error" class="player-entry-status" role="alert"><span class="entry-eyebrow">三秋叶</span><h1>无法打开启动器</h1><p>{{error}}</p><div><button @click="start">重新尝试</button><button data-entry-close @click="close">关闭</button></div></section>
  <div v-else-if="mode==='launcher'" class="launcher-entry" data-launcher-entry><LauncherApp v-show="!updaterOpen" @open-updater-preview="previewUpdater"/><div v-if="updaterLoaded" v-show="updaterOpen" class="legacy-preview-host"><Updater/><div class="legacy-preview-toolbar"><span>更新器预览 · 演示数据</span><button data-return-launcher @click="updaterOpen=false">返回启动器 →</button></div></div></div>
  <Updater v-else-if="mode==='updater'"/>
  <div v-else-if="mode==='preview'" class="legacy-preview-host"><Updater/><div class="legacy-direct-notice" data-preview-notice role="status">更新器预览 · 演示数据</div></div>
  <section v-else class="player-entry-status" role="status"><span class="entry-eyebrow">三秋叶</span><p>正在打开启动器…</p></section>
</template>
<style scoped>
.player-entry-status{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:20px;padding:40px;text-align:center;background:radial-gradient(ellipse at top,#1d3634,#090f10 70%);color:var(--dfs-text);border:1px solid #2ee8df33;border-radius:26px}.entry-eyebrow{font-size:12px;letter-spacing:.25em;color:var(--dfs-accent)}h1{font-size:28px}p{max-width:640px;color:var(--dfs-text-dim);line-height:1.8;overflow-wrap:anywhere}button{margin:8px;padding:10px 22px;border-radius:10px;background:#2ee8df22;border:1px solid #2ee8df44}button:focus-visible{outline:2px solid var(--dfs-accent);outline-offset:3px}
.launcher-entry,.legacy-preview-host{height:100%;width:100%;position:relative}.legacy-preview-toolbar{position:absolute;bottom:14px;left:50%;transform:translateX(-50%);z-index:100;display:flex;align-items:center;gap:16px;padding:5px 12px;background:#0d1d1cf2;border:1px solid #ffbd7355;border-radius:12px;box-shadow:0 5px 25px #0005;white-space:nowrap;font-size:11px;color:#dfc28e}.legacy-preview-toolbar button{margin:0;font-size:11px;padding:8px 12px}
.legacy-direct-notice{position:absolute;right:22px;top:78px;z-index:100;pointer-events:none;padding:7px 12px;border:1px solid #ffbd7355;background:#0d1d1cf2;color:#dfc28e;border-radius:8px;font-size:11px;white-space:nowrap}
</style>
