<script setup lang="ts">
import {ref} from 'vue';
import {MAID_STATES,MAID_SPRITES,type MaidState} from './maid';
defineProps<{state:MaidState;message:string;collapsed:boolean}>();
const emit=defineEmits<{toggle:[]}>();const failed=ref(false);
</script>
<template><aside class="workspace-maid" :data-maid-state="state" :class="{'is-collapsed':collapsed}"><div v-if="!collapsed&&!failed" class="workspace-maid-figure" aria-hidden="true"><img :src="MAID_SPRITES" alt="" :style="{transform:`translateX(-${MAID_STATES[state].index*20}%)`}" @error="failed=true"></div><div><strong>{{MAID_STATES[state].label}}</strong><p>{{message}}</p></div><button @click="emit('toggle')" :aria-expanded="!collapsed">{{collapsed?'展开女仆':'收起女仆'}}</button></aside></template>
