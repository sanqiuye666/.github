<script setup lang="ts">
import {computed,ref,watch} from 'vue';
import {projectImage} from './content-labels';
const props=defineProps<{url?:string|null;title:string}>();
const failed=ref(false),source=computed(()=>projectImage(props.url));
watch(()=>props.url,()=>failed.value=false);
</script>
<template><span class="content-image"><img v-if="source&&!failed" data-project-image :src="source" :alt="title+' 项目图片'" loading="lazy" decoding="async" referrerpolicy="no-referrer" @error="failed=true"/><span v-else class="content-image-fallback" data-project-image-fallback>{{failed?'图片暂不可用':'来源未提供图片'}}</span></span></template>
<style scoped>
.content-image{display:grid;place-items:center;width:104px;aspect-ratio:1;flex-shrink:0;overflow:hidden;border-radius:18px;background:var(--launcher-line);color:var(--launcher-muted)}.content-image img{width:100%;height:100%;object-fit:cover}.content-image-fallback{max-width:80px;padding:12px;font-size:12px;line-height:1.6;text-align:center}
@media(max-width:800px){.content-image{width:72px;border-radius:12px}}
</style>
