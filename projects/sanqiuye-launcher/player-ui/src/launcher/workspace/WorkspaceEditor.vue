<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref} from 'vue';
import SettingSwitch from '../controls/SettingSwitch.vue';
import {arrangeWidgets,createPreset,REQUIRED_WIDGETS,WIDGETS,validateLayout,type WorkspaceLayout,type WidgetId,type Placement} from './layout';
const props=defineProps<{layout:WorkspaceLayout;saveError?:string|null}>();
const emit=defineEmits<{apply:[layout:WorkspaceLayout];cancel:[]}>();
const draft=ref(validateLayout(props.layout)),leaving=ref(false),dialog=ref<HTMLElement>(),board=ref<HTMLElement>();
const initial=JSON.stringify(draft.value),previous=document.activeElement as HTMLElement|null;
const dirty=computed(()=>JSON.stringify(draft.value)!==initial);
const error=computed(()=>{try{validateLayout(draft.value);return '';}catch(e){return (e as Error).message;}});
function cancel(){if(dirty.value)leaving.value=true;else emit('cancel');}
function apply(){if(!error.value)emit('apply',validateLayout(draft.value));}
function reorder(i:number,delta:number){const list=[...draft.value.placements];if(i+delta<0||i+delta>=list.length)return;[list[i],list[i+delta]]=[list[i+delta],list[i]];draft.value=arrangeWidgets({...draft.value,placements:list});}
function remove(widget:WidgetId){if(!REQUIRED_WIDGETS.includes(widget))draft.value.placements=draft.value.placements.filter(p=>p.widget!==widget);}
function add(widget:WidgetId){const y=Math.max(...draft.value.placements.map(p=>p.y+p.height));draft.value.placements.push({widget,x:0,y,width:12,height:3});}
function mode(event:Event){draft.value.arrangement=(event.target as HTMLSelectElement).value as 'forced'|'free';if(draft.value.arrangement==='forced')draft.value=arrangeWidgets(draft.value);}
function coordinate(p:Placement,key:'x'|'y'|'width'|'height',event:Event){p[key]=Number((event.target as HTMLInputElement).value);}
let drag:{id:number;widget:WidgetId;sx:number;sy:number;before:Placement;resize:boolean;cell:number;row:number}|null=null;
function start(event:PointerEvent,p:Placement,resize=false){if(draft.value.arrangement!=='free'||event.button!==0)return;const canvas=board.value?.firstElementChild as HTMLElement|undefined,width=canvas?.clientWidth||board.value?.clientWidth||0,style=canvas?getComputedStyle(canvas):undefined;if(width<900||style?.display==='flex')return;const gap=Number.parseFloat(style?.columnGap??'')||0,row=(Number.parseFloat(style?.gridAutoRows??'')||80)+(Number.parseFloat(style?.rowGap??'')||12);drag={id:event.pointerId,widget:p.widget,sx:event.clientX,sy:event.clientY,before:{...p},resize,cell:(width+gap)/12,row};(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);}
function move(event:PointerEvent){if(!drag||event.pointerId!==drag.id)return;const p=draft.value.placements.find(p=>p.widget===drag!.widget)!;const dx=Math.round((event.clientX-drag.sx)/drag.cell),dy=Math.round((event.clientY-drag.sy)/drag.row);if(drag.resize){p.width=drag.before.width+dx;p.height=drag.before.height+dy;}else{p.x=drag.before.x+dx;p.y=drag.before.y+dy;}}
function end(event:PointerEvent){if(drag?.id===event.pointerId)drag=null;}
function abort(){if(drag){Object.assign(draft.value.placements.find(p=>p.widget===drag!.widget)!,drag.before);drag=null;}}
function key(event:KeyboardEvent){if(event.key==='Escape'){event.preventDefault();if(drag)abort();else cancel();}if(event.key==='Tab'){const items=Array.from(dialog.value?.querySelectorAll<HTMLElement>('button:not(:disabled),input,select')??[]);if(event.shiftKey&&document.activeElement===items[0]){event.preventDefault();items.at(-1)?.focus();}else if(!event.shiftKey&&document.activeElement===items.at(-1)){event.preventDefault();items[0]?.focus();}}}
function unload(event:BeforeUnloadEvent){if(dirty.value){event.preventDefault();event.returnValue='';}}
onMounted(()=>{dialog.value?.querySelector<HTMLElement>('button')?.focus();window.addEventListener('beforeunload',unload);});
onBeforeUnmount(()=>{abort();window.removeEventListener('beforeunload',unload);previous?.focus();});
</script>
<template>
 <div class="workspace-overlay"><section ref="dialog" class="workspace-editor" role="dialog" aria-modal="true" aria-labelledby="editor-title" @keydown="key">
  <header><h2 id="editor-title">编辑 {{layout.name}}</h2><p>修改仅存在草稿中；应用后才保存。基础模块始终保留。</p><label>排列方式 <select data-arrangement :value="draft.arrangement" @change="mode"><option value="forced">强制排列</option><option value="free">自由排列</option></select></label> <button data-auto-arrange @click="draft=arrangeWidgets(draft)">自动整理</button></header>
  <p>自由模式可拖动卡片标题、拖动右下角缩放，也可用下面的数字输入框和方向键调整。窄窗口暂按单列显示，保存坐标不变。自动整理必要时收紧卡片高度；应用前只修改草稿。</p>
  <div class="workspace-slot"><label>强调色 <select v-model="draft.appearance.accent"><option value="jade">青绿</option><option value="amber">秋金</option><option value="violet">鸢紫</option></select></label><label>面板不透明度 <select v-model="draft.appearance.opacity"><option :value="85">85%</option><option :value="92">92%</option><option :value="100">100%</option></select></label><SettingSwitch v-model="draft.appearance.showBackground" label="显示首页背景"/><button data-editor-reset @click="draft={...createPreset(layout.preset),id:layout.id,name:layout.name}">恢复此预设排列</button></div>
  <p v-if="error||saveError" role="alert" class="workspace-editor-error">{{error||saveError}}</p>
  <div ref="board" class="workspace-boundary"><div class="workspace-canvas workspace-edit-board" :class="{'has-error':!!error}"><section v-for="p in draft.placements" :key="p.widget" class="workspace-widget" :style="{gridColumn:`${Math.max(0,p.x)+1} / span ${Math.max(3,p.width)}`,gridRow:`${Math.max(0,p.y)+1} / span ${Math.max(2,p.height)}`}"><button class="workspace-drag" :aria-label="'拖动 '+p.widget" @pointerdown="start($event,p)" @pointermove="move" @pointerup="end" @pointercancel="abort">{{WIDGETS.find(w=>w.id===p.widget)?.name}} ⠿</button><button v-if="draft.arrangement==='free'" class="workspace-resize" :aria-label="'缩放 '+p.widget" @pointerdown="start($event,p,true)" @pointermove="move" @pointerup="end" @pointercancel="abort">↘</button></section></div></div>
  <div v-for="(p,i) in draft.placements" :key="p.widget" class="workspace-slot"><strong>{{WIDGETS.find(w=>w.id===p.widget)?.name}}</strong><button :data-move-up="p.widget" :disabled="i===0" @click="reorder(i,-1)">上移</button><button :data-move-down="p.widget" :disabled="i===draft.placements.length-1" @click="reorder(i,1)">下移</button><button :data-remove-widget="p.widget" :disabled="REQUIRED_WIDGETS.includes(p.widget)" @click="remove(p.widget)">{{REQUIRED_WIDGETS.includes(p.widget)?'基础模块':'移除'}}</button><template v-if="draft.arrangement==='free'"><label v-for="c in (['x','y','width','height'] as const)" :key="c">{{{x:'列',y:'行',width:'宽',height:'高'}[c]}} <input type="number" :data-coordinate="p.widget+'-'+c" :aria-label="p.widget+' '+c" :value="p[c]" step="1" @input="coordinate(p,c,$event)"></label></template></div>
  <div class="workspace-slot"><button v-for="w in WIDGETS.filter(w=>!draft.placements.some(p=>p.widget===w.id))" :key="w.id" @click="add(w.id)">添加 {{w.name}}</button></div>
  <footer class="workspace-editor-actions"><span v-if="leaving">有未保存修改。应用保存，或放弃本次草稿。</span><button v-if="leaving" data-discard-layout @click="emit('cancel')">放弃修改</button><button data-cancel-layout @click="cancel">取消</button><button data-apply-layout :disabled="!!error" @click="apply">应用并保存</button></footer>
 </section></div>
</template>
