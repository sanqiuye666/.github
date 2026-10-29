<script setup lang="ts">
import {computed,reactive,ref} from 'vue';
import {Users,ShieldCheck,Layers,Send,UserRound,Clock,Package,Search,ArrowRight,Unplug} from '@lucide/vue';
import {desk,openView,safely} from './store';
import {dispatchOverview,filterPlayers,playerStatusLabel,playerValue,type PlayerFilters} from './players-model';
import './players.css';
import ServerConsole from './server/ServerConsole.vue';
const cloudEnabled=ref(false);
const data=reactive(dispatchOverview());
const filters=reactive<PlayerFilters>({query:'',groupId:'',status:'ALL'});
const players=computed(()=>filterPlayers(data.players,filters));
const sections=[{id:'overview',label:'玩家看板',icon:Users},{id:'accounts',label:'账号管理',icon:ShieldCheck},{id:'groups',label:'分组与下发',icon:Layers}];
</script>
<template>
 <ServerConsole @enabled="cloudEnabled=$event"/>
 <div v-if="!cloudEnabled" class="stage players-view"><header class="players-header"><div><span class="players-eyebrow">三秋叶 · 调度中心</span><h1>玩家与账号</h1><p>从玩家加入，到整合包到达。</p></div><span class="players-pending"><Unplug :size="13"/>调度服务待接入</span></header>
  <nav class="players-tabs" aria-label="玩家运营分区"><button v-for="section in sections" :key="section.id" :class="{active:desk.section===section.id}" :aria-current="desk.section===section.id?'page':undefined" @click="safely(()=>openView('players',section.id))"><component :is="section.icon" :size="15"/>{{section.label}}</button></nav>
  <div class="players-connection-note"><Unplug :size="18"/><div><strong>运营台已准备，等待连接玩家</strong><p>账号、分组、整合包下发与玩家上报尚未接入。接入后可查看玩家状态并按分组下发整合包。</p></div><span>待接入</span></div>
  <template v-if="desk.section==='overview'"><div class="players-metrics"><article v-for="metric in [{title:'玩家账号',icon:Users,note:'尚未接入账号库'},{title:'最近在线',icon:UserRound,note:'尚未接收在线状态'},{title:'累计游玩',icon:Clock,note:'尚未接收游戏会话'},{title:'整合包分发',icon:Package,note:'尚未配置下发策略'}]" :key="metric.title"><div><component :is="metric.icon" :size="17"/><span>{{metric.title}}</span></div><strong>—</strong><small>{{metric.note}}</small></article></div>
   <section class="players-table-card"><header><div><h2>玩家列表</h2><p>查看游戏版本、整合包与最近的游玩情况。</p></div><button class="accent" disabled title="账号服务待接入"><Users :size="14"/>添加玩家</button></header><div class="players-filters"><label class="players-search"><Search :size="15"/><input v-model="filters.query" aria-label="搜索玩家" placeholder="搜索玩家昵称或 ID"/></label><select v-model="filters.groupId" aria-label="按玩家组筛选"><option value="">全部玩家组</option><option v-for="group in data.groups" :key="group.id" :value="group.id">{{group.name}}</option></select><select v-model="filters.status" aria-label="按账号状态筛选"><option value="ALL">全部账号状态</option><option value="ACTIVE">启用</option><option value="DISABLED">禁用</option><option value="UNKNOWN">未知</option></select></div><div class="players-table-scroll"><table><thead><tr><th>玩家</th><th>账号 ID</th><th>分组</th><th>账号状态</th><th>游戏版本</th><th>整合包版本</th><th>游玩时长</th><th>最后在线</th></tr></thead><tbody><tr v-for="player in players" :key="player.id"><td>{{player.username}}</td><td>{{player.id}}</td><td>{{playerValue(player.groupId)}}</td><td>{{playerStatusLabel(player.status)}}</td><td>{{playerValue(player.gameVersion)}}</td><td>{{playerValue(player.packVersion)}}</td><td>{{playerValue(player.playTimeSeconds)}}</td><td>{{playerValue(player.lastSeen)}}</td></tr><tr v-if="!players.length"><td colspan="8"><div class="players-empty"><span><Users :size="32"/></span><h3>尚未接入玩家上报</h3><p>调度中心接入后，玩家的版本与游玩情况会出现在这里。</p><small>玩家登录并开启上报后，可在此查看记录。</small></div></td></tr></tbody></table></div></section>
  </template>
  <div v-else-if="desk.section==='accounts'" class="players-operation-grid"><section class="players-operation"><span class="players-operation-icon"><ShieldCheck :size="23"/></span><h2>面板账号管理</h2><p>管理玩家在调度中心的身份、所属组与会话状态。</p><div class="players-account-actions"><button v-for="label in ['创建账号','重置面板凭据','禁用 / 恢复','移动玩家分组','结束会话','下发更新指令']" :key="label" disabled>{{label}}<ArrowRight :size="13"/></button></div><small>账号服务待接入，所有操作暂不可用。</small></section><section class="players-operation"><span class="players-operation-icon"><UserRound :size="23"/></span><h2>游戏身份与本机账号</h2><p>微软游戏授权由玩家在本机完成，与面板身份分别管理。</p><ul><li>管理端只管理本系统的面板账号</li><li>不接收微软密码或刷新令牌</li><li>账号完整性检测由玩家端执行</li></ul><div class="players-soft-note">本机加密账号库与设备绑定待接入。</div></section></div>
  <div v-else class="players-operation-grid"><section class="players-operation"><span class="players-operation-icon"><Layers :size="23"/></span><h2>玩家分组</h2><p>新玩家由服务端分配默认组，按组获取可见整合包。</p><div class="players-soft-note">尚未连接组服务，暂无玩家组。</div><button class="accent" disabled>创建玩家组</button><small>玩家组与整合包的“可选内容”分组分别管理。</small></section><section class="players-operation"><span class="players-operation-icon"><Send :size="23"/></span><h2>分组与下发策略</h2><p>将已发布整合包分配给玩家组，也可为单个玩家设置覆盖规则。</p><dl><dt>目标玩家组</dt><dd>尚未选择</dd><dt>整合包项目</dt><dd>{{desk.project?.displayName||'尚未选择'}}</dd><dt>当前策略</dt><dd>待接入</dd></dl><button class="accent" disabled>配置下发策略</button></section></div>
  <footer class="players-footer"><ShieldCheck :size="13"/><span>使用当前管理端身份 · 调度账号与上报服务待接入</span></footer>
 </div>
</template>
