<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref} from 'vue';
import {operationMessage,type LocalLauncherClient} from './local-api';
import type {IdentityListing} from './launch-types';
const props=defineProps<{client:LocalLauncherClient}>();const emit=defineEmits<{changed:[value:IdentityListing]}>();
const listing=ref<IdentityListing>({identities:[],selectedId:null,temporary:true}),name=ref(''),error=ref(''),busy=ref(false);
const selected=computed(()=>listing.value.identities.find(identity=>identity.id===listing.value.selectedId));
const lifetime=new AbortController();
function accept(value:IdentityListing){if(lifetime.signal.aborted)return;listing.value=value;emit('changed',value);}
async function load(){try{accept(await props.client.identities(lifetime.signal));}catch(e){if(!lifetime.signal.aborted)error.value=operationMessage(e);}}
async function action(kind:'create'|'select'|'remove',id=''){
 if(busy.value)return;if(kind==='create'&&!/^[A-Za-z0-9_]{3,16}$/.test(name.value)){error.value='离线名字须为 3–16 位字母、数字或下划线';return;}
 busy.value=true;error.value='';try{const value=kind==='create'?await props.client.createIdentity(name.value,lifetime.signal):kind==='select'?await props.client.selectIdentity(id,lifetime.signal):await props.client.removeIdentity(id,lifetime.signal);accept(value);if(kind==='create')name.value='';}catch(e){if(!lifetime.signal.aborted)error.value=operationMessage(e);}finally{busy.value=false;}
}
onMounted(load);onBeforeUnmount(()=>lifetime.abort());
</script>
<template>
 <section class="launcher-local-page"><div class="launcher-section-heading"><div><span class="launcher-overline">YOUR IDENTITY</span><h1>选择下一次冒险的名字</h1><p>临时离线身份，本次会话有效。切换仅影响下次启动。</p></div></div>
  <p v-if="error" class="launcher-inline-notice" role="alert">{{error}}</p>
  <section class="launcher-glass launcher-account-panel"><h2>本次会话的游戏身份</h2><p data-selected-identity>当前选择 · {{selected?.name??'尚未选择'}}</p>
   <form class="launcher-account-form" @submit.prevent="action('create')"><label>离线游戏名<input v-model="name" data-offline-name autocomplete="off" placeholder="3–16 位字母、数字或下划线" maxlength="16" :disabled="busy"/></label><button class="launcher-primary" :disabled="busy||listing.identities.length>=20">添加临时身份</button></form>
   <p v-if="!listing.identities.length" class="launcher-caption">还没有身份。添加一个名字后，再到游戏实例进行启动检查。</p>
   <div v-for="identity in listing.identities" :key="identity.id" class="launcher-task-row"><div><strong>{{identity.name}}</strong><span class="launcher-mini-tag">{{identity.id===listing.selectedId?'已选择':'离线身份'}}</span></div><div class="launcher-inline-actions"><button class="launcher-secondary" :data-select-identity="identity.id" :disabled="busy||identity.id===listing.selectedId" @click="action('select',identity.id)">选择</button><button class="launcher-secondary" :disabled="busy" @click="action('remove',identity.id)">移除</button></div></div>
  </section><p class="launcher-caption">重新启动本机服务后需要重新添加。微软与第三方登录、加密账号保存和云端面板账号在后续批次接入；此处不表示正版或管理端认证。</p>
 </section>
</template>
