<script setup lang="ts">
import {onBeforeUnmount,ref} from 'vue';
defineProps<{localAvailable?:boolean;localBusy?:boolean;localError?:string}>();
const emit=defineEmits<{preview:[];local:[];retry:[]}>();
const username=ref(''),password=ref(''),message=ref('');
function submit(){password.value='';message.value='尚未连接调度中心，面板登录待接入。你可以先预览启动器。';}
function microsoft(){password.value='';message.value='微软游戏账号授权待接入。面板账号与游戏账号分别管理。';}
function preview(){password.value='';username.value='';emit('preview');}
onBeforeUnmount(()=>{password.value='';});
</script>
<template>
  <main class="launcher-login">
    <section class="launcher-login-intro"><span class="launcher-overline"><i></i> 为每一个世界，做好准备</span><h1>你的世界，<br/>从三秋叶出发<span>。</span></h1><p>把版本、模组与服务器的更新，<br/>放在一个熟悉的地方。</p><div class="launcher-login-tags"><span>独立游戏实例</span><span>整合包同步</span><span>模组探索</span></div><small>三秋叶启动器 · 启动器框架</small></section>
    <section class="launcher-login-card"><span class="launcher-overline">WELCOME ABOARD</span><h2>欢迎来到三秋叶</h2><p>使用面板账号，连接你的服务器。</p>
      <form @submit.prevent="submit"><label>面板账号<input v-model="username" name="username" autocomplete="username" placeholder="输入面板账号" maxlength="64" required/></label><label>密码<input v-model="password" name="password" type="password" autocomplete="current-password" placeholder="输入密码" maxlength="256" required/></label><button class="launcher-primary launcher-full" type="submit">登录面板 <span>→</span></button></form>
      <p class="launcher-login-note">尚未连接调度中心 · 登录待接入</p><div class="launcher-divider"><span>游戏身份</span></div><button class="launcher-secondary launcher-full" @click="microsoft"><span class="launcher-ms-mark" aria-hidden="true">▦</span> 微软游戏账号 <span class="launcher-mini-tag">待接入</span></button><p class="launcher-caption">游戏授权独立保存，面板账号用于分组与整合包同步。</p>
      <p v-if="localError" class="launcher-inline-notice" role="alert">{{localError}}</p><button v-if="localError" type="button" class="launcher-secondary launcher-full" :disabled="localBusy" data-retry-local @click="emit('retry')">重新连接本机核心</button>
      <p v-if="message" class="launcher-inline-notice" role="status">{{message}}</p><button v-if="localAvailable||localBusy" type="button" class="launcher-secondary launcher-full launcher-local-entry" data-enter-local :disabled="localBusy||!localAvailable" @click="password='';username='';emit('local')">{{localBusy?'正在连接本机…':'进入本机启动器'}} →</button><button type="button" class="launcher-preview-link" data-enter-preview @click="preview">先预览启动器 <span>→</span></button>
    </section>
  </main>
</template>
