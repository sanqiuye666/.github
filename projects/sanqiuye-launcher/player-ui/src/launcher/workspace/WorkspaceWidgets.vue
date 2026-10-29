<script setup lang="ts">
import type {WidgetId} from './layout';
import type {LauncherPage} from '../model';
defineProps<{widget:WidgetId;local:boolean;instanceCount:number;selectedIdentityName:string|null;primaryLaunch?:boolean}>();
const emit=defineEmits<{navigate:[page:LauncherPage];help:[]}>();
</script>
<template>
 <template v-if="widget==='identity'"><small>当前游戏身份</small><h2>{{ local?selectedIdentityName??'还没有选择身份':'预览模式' }}</h2><p>{{ local?'临时离线身份 · 本次会话有效':'进入本机模式后管理游戏身份' }}</p><button @click="emit('navigate','accounts')">选择或添加身份 →</button></template>
 <template v-else-if="widget==='instances'"><small>独立的世界，从这里出发</small><h2>我的游戏实例</h2><p>{{ local?instanceCount+' 个实例':'实例列表在本机模式可用' }} · 版本与存档各自独立</p><button @click="emit('navigate','instances')">管理实例 →</button><button @click="emit('navigate','versions')">安装新版本 ＋</button></template>
 <template v-else-if="widget==='launch'"><small>下一次冒险</small><h2>准备出发</h2><p>选择实例，检查 Java 与身份，再启动游戏。</p><button :class="primaryLaunch===false?'launcher-secondary':'launcher-primary'" @click="emit('navigate','instances')">选择实例启动 →</button></template>
 <template v-else-if="widget==='tasks'"><small>每一步都有迹可循</small><h2>下载与运行记录</h2><p>在实例页查看实际任务进度、运行状态和日志。</p><button @click="emit('navigate','instances')">查看任务与日志 →</button><button @click="emit('navigate','mods')">发现模组 →</button></template>
 <template v-else-if="widget==='environment'"><small>运行环境</small><h2>Java 与内存</h2><p>按游戏版本选择 Java，单独保存实例设置。</p><button @click="emit('navigate','settings')">检查运行环境 →</button></template>
 <template v-else-if="widget==='updates'"><small>熟悉的更新工具</small><h2>更新与备份</h2><p>整合包更新、可选内容与本地文件管理。</p><button @click="emit('navigate','updates')">打开更新工具 →</button></template>
 <template v-else><small>第一次来到这里？</small><h2>从容开始</h2><p>跟着指引，准备好自己的第一个世界。</p><button @click="emit('help')">查看功能指引 →</button></template>
</template>
