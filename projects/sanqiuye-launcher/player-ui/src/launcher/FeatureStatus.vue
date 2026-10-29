<script setup lang="ts">
import {computed,nextTick,ref} from 'vue';
import {featureStatus,featureStateLabel,type FeatureStatusInput,type FeatureState} from './feature-status';
const props=defineProps<FeatureStatusInput>();
const open=ref(false),trigger=ref<HTMLButtonElement>(),dialog=ref<HTMLElement>();
const rows=computed(()=>featureStatus(props));
const groups=computed(()=>[...new Set(rows.value.map(row=>row.group))]);
const counts=computed(()=>{const value:Record<FeatureState,number>={available:0,partial:0,pending:0,error:0,unchecked:0};for(const row of rows.value)value[row.state]++;return value;});
async function show(){open.value=true;await nextTick();dialog.value?.focus();}
async function close(){open.value=false;await nextTick();trigger.value?.focus();}
function keyboard(event:KeyboardEvent){
 if(event.key==='Escape'){event.preventDefault();event.stopPropagation();void close();}
 if(event.key==='Tab'){
  const targets=Array.from(dialog.value?.querySelectorAll<HTMLElement>('button,[tabindex="0"]')??[]);
  const current=targets.indexOf(document.activeElement as HTMLElement);
  const next=current<0?(event.shiftKey?targets.length-1:0):(current+(event.shiftKey?-1:1)+targets.length)%targets.length;
  event.preventDefault();targets[next]?.focus();
 }
}
</script>
<template>
 <div class="feature-status">
  <button ref="trigger" class="feature-status-trigger" data-feature-status-open aria-haspopup="dialog" :aria-expanded="open" @click="show"><i :class="{error:counts.error>0}"></i><span><strong>功能运行情况</strong><small>{{ counts.error?counts.error+' 项异常 · ':'' }}{{ counts.pending }} 项未接入 · {{ counts.unchecked }} 项未检测</small></span><span aria-hidden="true">›</span></button>
  <div v-if="open" class="feature-status-backdrop" @click.self="close">
   <section ref="dialog" class="feature-status-dialog" tabindex="-1" role="dialog" aria-modal="true" aria-label="功能运行情况" @keydown="keyboard">
    <header><div><h2>功能运行情况</h2><p>{{ local?'本机模式':'框架预览' }} · 仅展示已有信息，不自动执行检测或游戏操作。</p></div><button aria-label="关闭功能运行情况" @click="close">×</button></header>
    <div class="feature-status-summary"><span v-for="(count,state) in counts" :key="state" :class="'feature-state-'+state">{{ featureStateLabel[state] }} {{ count }}</span></div>
    <p class="feature-status-note">已提供入口不代表网络已连通或游戏已经启动。未检测项请到对应页面检查；未接入项是后续目标。</p>
    <div class="feature-status-list" tabindex="0" role="region" aria-label="按功能分组的运行情况"><section v-for="group in groups" :key="group"><h3>{{ group }}</h3><ul><li v-for="row in rows.filter(item=>item.group===group)" :key="row.id" :data-feature-status="row.id"><div><strong>{{ row.title }}</strong><span :class="'feature-state-'+row.state">{{ featureStateLabel[row.state] }}</span></div><p>{{ row.detail }}</p></li></ul></section></div>
   </section>
  </div>
 </div>
</template>
<style>
.feature-status{margin-top:auto;padding-top:16px;flex-shrink:0}.feature-status-trigger{width:100%;min-height:52px;display:flex;align-items:center;gap:9px;border:1px solid var(--launcher-line)!important;border-radius:11px;padding:12px;color:#dbe8d8;background:#ffffff04}.feature-status-trigger>i{width:8px;height:8px;border-radius:50%;background:#d6b475;flex-shrink:0}.feature-status-trigger>i.error{background:#ed9c99}.feature-status-trigger>span:nth-child(2){text-align:left;flex:1}.feature-status-trigger strong{display:block;font-size:12px;font-weight:500}.feature-status-trigger small{display:block;font-size:10px;color:#a8bbad;margin-top:5px;line-height:1.5}.feature-status-backdrop{position:fixed;inset:0;z-index:90;display:grid;place-items:center;padding:20px;background:#050a09d9}.feature-status-dialog{display:flex;flex-direction:column;width:min(820px,100%);max-height:90vh;box-sizing:border-box;background:#12201f;border:1px solid #819c8d55;border-radius:18px;padding:24px;box-shadow:0 20px 80px #0008;color:#e2eadb;outline:none}.feature-status-dialog header{display:flex;align-items:flex-start;justify-content:space-between;gap:15px}.feature-status-dialog h2{font-size:21px;margin:0 0 10px}.feature-status-dialog header p,.feature-status-note{font-size:12px;line-height:1.8;color:#a8bfae}.feature-status-dialog header button{font-size:25px;min-width:36px;min-height:36px;border:1px solid #74897c55!important;border-radius:8px}.feature-status-summary{display:flex;flex-wrap:wrap;gap:10px;font-size:12px;margin:16px 0 8px}.feature-status-summary span{padding:5px 9px;border:1px solid currentColor;border-radius:15px}.feature-status-list{overflow-y:auto;min-height:0;margin-top:12px;padding-right:8px;scrollbar-width:thin}.feature-status-list h3{font-size:15px;color:#dec599;margin:20px 0 10px}.feature-status-list ul{list-style:none;margin:0;padding:0}.feature-status-list li{padding:14px 0;border-top:1px solid #8d9c8b26}.feature-status-list li>div{display:flex;justify-content:space-between;align-items:flex-start;gap:18px}.feature-status-list strong{font-size:13px;line-height:1.7;font-weight:500}.feature-status-list li span{font-size:11px;white-space:nowrap}.feature-status-list li p{color:#a8bfae;font-size:12px;line-height:1.8;margin:7px 0 0;overflow-wrap:anywhere}.feature-state-available{color:#80d2b4}.feature-state-partial{color:#dfbf82}.feature-state-pending{color:#a5afa7}.feature-state-error{color:#efa6a1}.feature-state-unchecked{color:#99bfd7}.feature-status-trigger:focus-visible,.feature-status-dialog button:focus-visible{outline:2px solid #7ed8c3;outline-offset:3px}@media(max-width:820px){.feature-status-trigger{padding:11px 5px;justify-content:center}.feature-status-trigger small,.feature-status-trigger>i,.feature-status-trigger>span:last-child{display:none}.feature-status-trigger strong{font-size:11px;writing-mode:vertical-rl;letter-spacing:.15em}.feature-status-trigger>span:nth-child(2){flex:0}.feature-status-dialog{padding:17px}.feature-status-backdrop{padding:12px}}
</style>
