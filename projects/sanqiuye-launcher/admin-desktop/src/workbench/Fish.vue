<script setup lang="ts">
import { useId } from 'vue';
import { brandLogo, leafPath } from '../brand-logo';
// Retain the component name and CSS hooks for existing callers; every variant renders the three-leaf brand.
withDefaults(defineProps<{variant?:'glyph'|'mark'|'scene';size?:number}>(),{variant:'glyph',size:16});
const id=useId();
</script>
<template>
 <svg v-if="variant==='glyph'" class="fish-glyph" :width="size" :height="size" viewBox="64 64 384 384" aria-hidden="true">
  <path :d="leafPath" fill="currentColor"/><path d="M205 422C228 382 245 339 264 292L310 161" fill="none" stroke="currentColor" stroke-width="16" stroke-linecap="round"/>
 </svg>
 <span v-else-if="variant==='mark'" class="fish-mark" :style="{width:`${size}px`,height:`${size}px`}" aria-hidden="true" v-html="brandLogo(id)"></span>
 <svg v-else class="fish-scene" viewBox="0 0 120 96" aria-hidden="true">
  <defs><linearGradient :id="`${id}-body`" x1="0" y1="0" x2="1" y2="1"><stop offset="0" style="stop-color:var(--fish-a)"/><stop offset="1" style="stop-color:var(--fish-b)"/></linearGradient></defs>
  <circle cx="58" cy="50" r="38" class="scene-water"/>
  <g class="scene-fish" transform="translate(10 1) scale(.19)"><path :d="leafPath" :fill="`url(#${id}-body)`"/><path d="M205 422C228 382 245 339 264 292L310 161M242 348L145 263M255 383L371 296" fill="none" stroke="currentColor" stroke-opacity=".55" stroke-width="13" stroke-linecap="round"/></g>
  <circle cx="99" cy="25" r="3" class="scene-bubble"/><circle cx="105" cy="13" r="2" class="scene-bubble b2"/>
 </svg>
</template>
