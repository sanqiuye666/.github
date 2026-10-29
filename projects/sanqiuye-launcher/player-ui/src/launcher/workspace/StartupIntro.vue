<script setup lang="ts">
import {onBeforeUnmount,onMounted,ref,watch} from 'vue';
import {MAID_AVATAR} from './maid';
import {animationRepository} from './intro/repository';
import type {AnimationAsset} from './intro/media';
import AnimationPlayer from './intro/AnimationPlayer.vue';
const props=defineProps<{enabled:boolean}>();const emit=defineEmits<{finished:[]}>();
const custom=ref<AnimationAsset|null>(null),visible=ref(props.enabled);let alive=true,timer:ReturnType<typeof setTimeout>|undefined,defaultTimer:ReturnType<typeof setTimeout>|undefined,done=false,media:MediaQueryList|undefined;
function finish(){if(done)return;done=true;clearTimeout(timer);clearTimeout(defaultTimer);visible.value=false;emit('finished');}
function motion(){if(media?.matches)finish();}
watch(()=>props.enabled,value=>{if(!value)finish();});
onMounted(()=>{media=window.matchMedia?.('(prefers-reduced-motion: reduce)');media?.addEventListener?.('change',motion);if(!props.enabled||media?.matches)finish();else{
 timer=setTimeout(finish,8000);
 // 先等有界的本机读取，再决定默认或自选素材；总时限始终从挂载时计算。
 void animationRepository.read().catch(()=>null).then(asset=>{if(!alive||done)return;if(asset)custom.value=asset;else defaultTimer=setTimeout(finish,900);});
}});
onBeforeUnmount(()=>{alive=false;clearTimeout(timer);clearTimeout(defaultTimer);media?.removeEventListener?.('change',motion);});
</script>
<template><div v-if="visible" data-startup-intro class="workspace-intro" aria-label="三秋叶开屏"><AnimationPlayer v-if="custom" :asset="custom" @finished="finish"/><img v-else :src="MAID_AVATAR" alt="三秋叶女仆头像"><div v-if="!custom"><strong>三秋叶</strong><p>准备好，去你的世界。</p></div><button @click="finish">跳过动画</button></div></template>
