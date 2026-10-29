<script setup lang="ts">
defineOptions({inheritAttrs:false});
defineProps<{modelValue:boolean;label?:string;disabled?:boolean}>();
const emit=defineEmits<{'update:modelValue':[value:boolean]}>();
</script>
<template>
 <label class="setting-switch" :class="{'is-disabled':disabled}">
  <span v-if="label" class="setting-switch-label">{{label}}</span>
  <span class="setting-switch-control">
   <input v-bind="$attrs" type="checkbox" role="switch" :checked="modelValue" :disabled="disabled" @change="emit('update:modelValue',($event.target as HTMLInputElement).checked)">
   <span class="setting-switch-track" aria-hidden="true"></span>
   <span class="setting-switch-state" aria-hidden="true">{{modelValue?'开启':'关闭'}}</span>
  </span>
 </label>
</template>
<style scoped>
.setting-switch{display:inline-flex!important;flex-direction:row!important;align-items:center;justify-content:space-between;gap:18px!important;min-height:34px;cursor:pointer}.setting-switch-label{line-height:1.6}.setting-switch-control{position:relative;display:inline-flex;align-items:center;gap:10px;flex-shrink:0}.setting-switch input{position:absolute;left:0;top:0;width:46px;height:28px;margin:0;opacity:0;z-index:1;cursor:inherit}.setting-switch-track{width:46px;height:28px;display:block;flex-shrink:0;border-radius:16px;background:var(--switch-off,#52615c);box-shadow:inset 0 0 0 1px #ffffff15;transition:background .18s}.setting-switch-track::after{content:'';display:block;width:20px;height:20px;margin:4px;border-radius:50%;background:#fff;box-shadow:0 1px 3px #0002;transform:translateX(0);transition:transform .18s}.setting-switch input:checked+.setting-switch-track{background:var(--launcher-accent,#458a70)}.setting-switch input:checked+.setting-switch-track::after{transform:translateX(18px)}.setting-switch input:focus-visible+.setting-switch-track{outline:2px solid var(--launcher-accent,#76d6bf);outline-offset:3px}.setting-switch-state{font-size:12px;min-width:2em;color:var(--launcher-muted,#bccbc2)}.setting-switch.is-disabled,.setting-switch:has(input:disabled){opacity:.5;cursor:default}@media(prefers-reduced-motion:reduce){.setting-switch-track,.setting-switch-track::after{transition:none}}
</style>
