<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref} from 'vue';
import type {SkinViewer} from 'skinview3d';
import SettingSwitch from '../controls/SettingSwitch.vue';
import {checkSkinPng} from '../tools/skin-pixels';
import {cottageFrame,mapleLeaf} from './scene';
import CottageHouse from './CottageHouse.vue';
import './cottage.css';
const emit=defineEmits<{close:[]}>();
const dialog=ref<HTMLElement>(),canvas=ref<HTMLCanvasElement>(),seconds=ref(0),readyAt=ref<number|null>(null),reduced=ref(false),checker=ref(false),busy=ref(false),error=ref(''),skinLabel=ref('示例皮肤 · 未读取游戏账号');
const frame=computed(()=>cottageFrame(seconds.value,readyAt.value,reduced.value));
const leaves=computed(()=>Array.from({length:reduced.value?5:25},(_,i)=>({id:i,...mapleLeaf(i,frame.value.leafTime)})));
const phaseLabel=computed(()=>frame.value.phase==='loading'?'演示等待中 · 枫叶随风':frame.value.phase==='walking'?'已停止发叶 · 小人正在回家':'演示结束 · 欢迎回家');
const skinStyle=computed(()=>({left:`${(frame.value.characterX-60)/9}%`,top:`${(frame.value.characterY-160)/5.5}%`,opacity:frame.value.characterOpacity,transform:`scale(${.7*frame.value.characterScale})`}));
let alive=true,viewer:SkinViewer|undefined,raf=0,previous:number|undefined,skinGeneration=0,media:MediaQueryList|undefined;
function demoSkin(){
 const c=document.createElement('canvas');c.width=c.height=64;const g=c.getContext('2d');if(!g)throw Error('无法创建皮肤画布');
 const fill=(x:number,y:number,w:number,h:number,color:string)=>{g.fillStyle=color;g.fillRect(x,y,w,h);};
 fill(0,0,32,16,'#dfb28d');fill(0,0,32,8,'#654532');fill(8,8,8,3,'#654532');fill(10,12,1,1,'#344138');fill(13,12,1,1,'#344138');
 fill(16,16,24,16,'#698166');fill(40,16,16,16,'#c99876');fill(32,48,16,16,'#c99876');fill(0,16,16,16,'#4e5e66');fill(16,48,16,16,'#4e5e66');return c;
}
function paint(delta=0){if(!viewer)return;if(viewer.animation){viewer.animation.paused=!frame.value.walk;viewer.animation.update(viewer.playerObject,delta);}viewer.render();}
function step(stamp:number){raf=0;if(!alive||document.hidden)return;const delta=previous===undefined?0:Math.min(.08,(stamp-previous)/1000);previous=stamp;if(!reduced.value)seconds.value+=delta;paint(delta);if(!reduced.value&&frame.value.phase!=='entered')raf=requestAnimationFrame(step);}
function resume(){if(raf)cancelAnimationFrame(raf);raf=0;previous=undefined;if(alive&&!document.hidden){paint();if(!reduced.value&&frame.value.phase!=='entered')raf=requestAnimationFrame(step);}}
function motionChanged(){reduced.value=media?.matches??false;resume();}
function ready(){if(busy.value||readyAt.value!==null)return;readyAt.value=seconds.value;paint();resume();}
function replay(){readyAt.value=null;seconds.value=0;if(viewer?.animation)viewer.animation.progress=0;resume();}
async function loadSkin(event:Event){
 const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file)return;const attempt=++skinGeneration;busy.value=true;error.value='';
 try{const dimensions=checkSkinPng(new Uint8Array(await file.slice(0,33).arrayBuffer()),file.size);const bitmap=await createImageBitmap(file);try{
  if(!alive||attempt!==skinGeneration)return;if(bitmap.width!==dimensions.width||bitmap.height!==dimensions.height)throw Error('PNG解码尺寸不一致');
  const skin=document.createElement('canvas');skin.width=64;skin.height=dimensions.height;const ctx=skin.getContext('2d');if(!ctx)throw Error('无法创建皮肤画布');ctx.drawImage(bitmap,0,0);
  if(!viewer)throw Error('3D预览尚未准备好，请稍后再试');await viewer.loadSkin(skin);if(!alive||attempt!==skinGeneration)return;skinLabel.value='本机皮肤 · '+file.name;replay();
 }finally{bitmap.close();}}catch(e){if(alive&&attempt===skinGeneration)error.value=e instanceof Error?e.message:'皮肤预览失败，保留当前演示';}finally{if(alive&&attempt===skinGeneration)busy.value=false;}
}
function key(e:KeyboardEvent){if(e.key==='Escape'){e.preventDefault();e.stopPropagation();emit('close');}if(e.key==='Tab'){const items=Array.from(dialog.value?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled)')??[]).filter(el=>el.tabIndex!==-1);if(!dialog.value?.contains(document.activeElement)){e.preventDefault();(e.shiftKey?items.at(-1):items[0])?.focus();}else if(e.shiftKey&&document.activeElement===items[0]){e.preventDefault();items.at(-1)?.focus();}else if(!e.shiftKey&&document.activeElement===items.at(-1)){e.preventDefault();items[0]?.focus();}}}
onMounted(async()=>{
 dialog.value?.querySelector<HTMLButtonElement>('[data-cottage-close]')?.focus();if(typeof matchMedia==='function'){media=matchMedia('(prefers-reduced-motion: reduce)');reduced.value=media.matches;media.addEventListener?.('change',motionChanged);}document.addEventListener('visibilitychange',resume);document.addEventListener('keydown',key,true);resume();
 try{const lib=await import('skinview3d');if(!alive||!canvas.value)return;viewer=new lib.SkinViewer({canvas:canvas.value,width:240,height:320,renderPaused:true});viewer.controls.enableRotate=false;viewer.controls.enableZoom=false;viewer.controls.enablePan=false;viewer.animation=new lib.WalkingAnimation();viewer.playerObject.rotation.y=Math.PI/2;await viewer.loadSkin(demoSkin());if(alive)paint();}
 catch{if(alive)error.value='皮肤3D未能启用，仍可查看小屋和落叶；请检查显卡与WebGL支持。';}
});
onBeforeUnmount(()=>{alive=false;++skinGeneration;cancelAnimationFrame(raf);document.removeEventListener('visibilitychange',resume);document.removeEventListener('keydown',key,true);media?.removeEventListener?.('change',motionChanged);viewer?.dispose();});
</script>
<template>
 <Teleport to=".launcher-shell"><div class="cottage-overlay"><section ref="dialog" class="cottage-preview" role="dialog" aria-modal="true" aria-label="枫叶小屋独立演示">
  <header class="cottage-header"><div><span class="launcher-overline">A LITTLE WAY HOME</span><h2>枫叶小屋</h2><p>独立动画Demo · 尚未替换启动开屏</p></div><button class="launcher-secondary" data-cottage-close @click="emit('close')">关闭预览</button></header>
  <div class="cottage-stage" :class="{checker}" data-cottage-stage :data-phase="frame.phase" :style="{'--cottage-camera':frame.cameraScale}">
   <div class="cottage-world">
    <svg viewBox="0 0 900 550" class="cottage-scene" aria-label="透明背景的方块小屋与秋叶"><defs><path id="cottage-maple-candidate" d="M0-34 7-20 15-25 14-9 26-18 22-4 35-2 25 7 29 16 13 14 10 27 3 22 1 39-2 39-4 22-12 27-15 15-30 17-25 8-36-2-23-5-27-19-15-9-16-25-8-20Z"/></defs><CottageHouse/>
     <g :opacity="frame.leafOpacity"><g v-for="leaf in leaves" :key="leaf.id" :transform="`translate(${leaf.x},${leaf.y}) rotate(${leaf.angle}) scale(${leaf.scale*leaf.flip},${leaf.scale})`"><use href="#cottage-maple-candidate" :fill="['#ba4b38','#d77948','#da9a50','#ad3d36'][leaf.id%4]"/><path d="M0-27V30M0 6 19-6M0 6-19-6M0 13 15 17M0 13-15 17" fill="none" stroke="#f4bc8f" stroke-width="1.2" opacity=".6"/></g></g>
     <g fill="#f6d99a" opacity=".55"><circle v-for="i in 7" :key="i" :cx="285+i*51" :cy="270+(i%3)*39" :r="1+i%2"/></g>
    </svg>
    <div class="cottage-walker" :style="skinStyle"><canvas ref="canvas" aria-label="本机皮肤行走预览"/></div>
   </div>
  </div>
  <div class="cottage-controls"><p role="status" data-cottage-status>{{phaseLabel}}</p><div class="cottage-actions"><button class="launcher-primary" data-cottage-ready :disabled="readyAt!==null||busy" @click="ready">模拟加载完成</button><button class="launcher-secondary" data-cottage-replay @click="replay">重新演示</button><label class="launcher-secondary cottage-skin-input">导入我的皮肤<input type="file" accept="image/png,.png" :disabled="busy" @change="loadSkin"/></label></div><p class="launcher-caption">{{skinLabel}}。PNG仅在本机预览，不上传、不更改游戏账号。</p><p v-if="error" role="alert" class="launcher-inline-notice">{{error}}</p><div class="cottage-preferences"><SettingSwitch v-model="checker" label="显示透明网格"/><SettingSwitch v-model="reduced" label="减少动态" @update:model-value="resume"/></div><p class="launcher-caption">枫叶为候选轮廓，待你确认参考。点击完成只演示停叶与回家，不代表真实程序已加载；最终版须由真实加载状态触发，且可以禁用。</p></div>
 </section></div></Teleport>
</template>
