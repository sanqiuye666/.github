<script setup lang="ts">
import {onBeforeUnmount,onMounted,ref} from 'vue';
import type {AnimationAsset} from './media';
const props=defineProps<{asset:AnimationAsset}>(),emit=defineEmits<{finished:[];failed:[]}>();const url=URL.createObjectURL(props.asset.blob),video=ref<HTMLVideoElement>();let timer:ReturnType<typeof setTimeout>|undefined,done=false;
function finish(){if(done)return;done=true;clearTimeout(timer);emit('finished');}
function failed(){emit('failed');finish();}
onMounted(()=>{timer=setTimeout(finish,8000);if(video.value)void video.value.play().catch(failed);});onBeforeUnmount(()=>{done=true;clearTimeout(timer);video.value?.pause();URL.revokeObjectURL(url);});
</script>
<template><div class="intro-custom-media"><img v-if="asset.type==='image/gif'" :src="url" alt="自定义开屏动画" @error="failed"><video v-else ref="video" :src="url" muted playsinline @ended="finish" @error="failed" aria-label="自定义开屏动画"/></div></template>
<style scoped>.intro-custom-media{position:absolute;inset:0;display:grid;place-items:center;pointer-events:none}.intro-custom-media img,.intro-custom-media video{width:100%;height:100%;object-fit:contain;max-width:100%;max-height:100%;border-radius:0;box-shadow:none;filter:none}</style>
