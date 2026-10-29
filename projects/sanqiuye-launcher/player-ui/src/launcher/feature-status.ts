import type {CapabilityState} from './model';

export type FeatureState='available'|'partial'|'pending'|'error'|'unchecked';
export interface FeatureStatusInput {
  local:boolean;capabilities:Record<string,CapabilityState>;instanceError?:string;
  instancesLoading?:boolean;instanceProblems?:number;workspaceWarning?:string|null;
}
export interface FeatureStatusRow {id:string;group:string;title:string;state:FeatureState;detail:string}
interface Definition extends FeatureStatusRow {capability?:string}
export const featureStateLabel:Record<FeatureState,string>={available:'可用',partial:'部分可用',pending:'未接入',error:'异常',unchecked:'未检测'};
const definitions:Definition[]=[
  {id:'workspace',group:'首页与工作台',title:'四预设、四自定义布局、指引与开屏',state:'available',detail:'本机展示功能；外观切换不改变游戏运行状态。'},
  {id:'overview',group:'首页与工作台',title:'账号概览、实例卡片与收藏排序',state:'partial',detail:'已有临时身份与实例入口；收藏、排序和完整角色卡片待接入。'},
  {id:'launch',group:'首页与工作台',title:'启动、结束、重启与启动失败提示',capability:'launch',state:'unchecked',detail:'已接入原版启动、结束和失败日志；需在实例中检查 Java 与文件。一键重启待接入。'},
  {id:'worlds',group:'首页与工作台',title:'命令世界、截图中心与游玩洞察',state:'pending',detail:'尚未接入世界创建、截图管理、日历与真实游玩统计。'},
  {id:'background',group:'首页与工作台',title:'启动图、背景与轮播',state:'partial',detail:'已有默认背景、女仆与有限外观选项；多图轮播待接入。'},
  {id:'tasks',group:'首页与工作台',title:'后台下载任务',capability:'vanilla',state:'unchecked',detail:'已有任务队列、取消/重试及校验；实时进度与失败原因请在任务列表查看。'},
  {id:'catalog',group:'版本与实例',title:'正式版、快照与旧版目录',capability:'catalog',state:'unchecked',detail:'已接入官方 Java 版目录与离线缓存；本区未检测目录源网络。'},
  {id:'vanilla',group:'版本与实例',title:'原版下载、校验与隔离安装',capability:'vanilla',state:'unchecked',detail:'已接入原版文件安装；具体版本结果以安装任务和收据为准。'},
  {id:'fabric',group:'版本与实例',title:'Fabric Loader安装与启动',capability:'fabric',state:'partial',detail:'已接官方兼容版本、固定文件计划、隔离安装与启动；具体结果以任务和游戏日志为准。Fabric API与模组另在实例模组入口操作。'},
  {id:'loaders',group:'版本与实例',title:'Forge、NeoForge与Quilt',capability:'loaders',state:'partial',detail:'可查询官方兼容版本、生成固定计划、隔离安装及启动。仅支持已识别的客户端描述格式，具体结果以任务和日志为准；OptiFine与完整整合包仍待后续。'},
  {id:'instances',group:'版本与实例',title:'本机实例与版本隔离',capability:'instances',state:'available',detail:'可创建、读取隔离实例；空列表不代表读取异常。'},
  {id:'instance-tools',group:'版本与实例',title:'删除、重命名、复制、备份、恢复、诊断',state:'pending',detail:'高级实例管理尚未接入。'},
  {id:'crash-diagnosis',group:'版本与实例',title:'启动与崩溃诊断',capability:'launch',state:'partial',detail:'可在异常启动记录中读取本机日志片段，识别退出类型及常见候选原因；只读、不上传、不自动修复。完整报告/尾部分析和更多规则待接入。'},
  {id:'migration',group:'版本与实例',title:'共享目录、文件夹扫描与迁移',state:'pending',detail:'当前采用隔离实例；共享目录和迁移待接入。'},
  {id:'java',group:'版本与实例',title:'Java 检测、自动匹配、内存与分辨率',capability:'runtimes',state:'unchecked',detail:'已有检测、手选及每实例设置；本区未执行 Java 检测，自动下载待接入。'},
  {id:'offline',group:'账号与皮肤',title:'离线身份切换',capability:'identities',state:'partial',detail:'仅本次后端会话有效；关闭后端后不保留，不是持久账号库。'},
  {id:'microsoft',group:'账号与皮肤',title:'微软 Device Code 正版登录',state:'pending',detail:'官方登录、刷新和游戏所有权检查待接入。'},
  {id:'yggdrasil',group:'账号与皮肤',title:'Yggdrasil 外置认证与多角色导入',state:'pending',detail:'JSON/TXT/URL/拖拽、提供商和多角色待接入。'},
  {id:'credentials',group:'账号与皮肤',title:'加密持久账号库',state:'pending',detail:'尚未接入；布局存储不保存账号密码或令牌。'},
  {id:'skins',group:'账号与皮肤',title:'皮肤编辑、3D预览、上传、模型、披风与历史',state:'partial',detail:'创作工具已接本机PNG像素编辑/导出、3D预览和标准/纤细模型；需要WebGL。账号换肤、披风与历史尚未接入，不代表游戏中皮肤生效。'},
  {id:'modrinth',group:'社区与资源',title:'Modrinth 模组查询与校验入库',capability:'mods',state:'unchecked',detail:'已接官方分类筛选、项目图片/简介/图库、兼容文件、依赖展示与校验下载；本区未检测平台网络。'},
  {id:'curseforge',group:'社区与资源',title:'CurseForge 模组源',capability:'mods',state:'unchecked',detail:'已有适配器，需要合法应用密钥；本区未检测源配置，无密钥时不能查询。'},
  {id:'dependencies',group:'社区与资源',title:'递归依赖、模组装入实例与失败回滚',capability:'instanceMods',state:'partial',detail:'可预览平台必需依赖、校验并事务装入隔离实例、移除全部受管模组；保留手动文件和原目录备份。手动模组兼容性、整合包和通用依赖求解待后续。'},
  {id:'community-types',group:'社区与资源',title:'平台资源包、光影、数据包、地图与收藏',state:'pending',detail:'模组与整合包的图文分类已接；本机资源包/光影文件管理另列，其他类型的平台浏览与自动下载仍待接入。'},
  {id:'local-resources',group:'社区与资源',title:'资源包、光影与录像管理',capability:'resources',state:'partial',detail:'可按实例导入、复制、改名、备份移除和恢复；游戏内选择启用，光影/ReplayMod需对应组件。尚不支持面板录像播放、平台自动下载或全版本兼容检查。'},
  {id:'packs',group:'社区与资源',title:'Modrinth、FTB、ATLauncher整合包目录',capability:'packs',state:'partial',detail:'已接公开目录、图片与简介；Modrinth有详细分类/版本/加载器筛选，FTB和ATLauncher暂不支持完整分类过滤。本区未检测平台网络，整合包下载安装及其他平台待接入。'},
  {id:'gradient-text',group:'社区与资源',title:'游戏彩字生成',state:'available',detail:'本机Unicode渐变、tellraw JSON、MiniMessage与传统16色输出；只生成/复制，不发送游戏命令，需目标版本或插件支持。'},
  {id:'seed-map',group:'社区与资源',title:'种子地图预览',state:'pending',detail:'群系、结构与坐标预览尚未接入；不创建游戏存档。'},
  {id:'mod-translation',group:'社区与资源',title:'模组翻译',state:'pending',detail:'汉化资源和独立翻译包尚未接入，不静默修改MOD原文件。'},
  {id:'mod-management',group:'社区与资源',title:'模组拖入、去重、跨实例清理与哈希更新',state:'pending',detail:'尚未接入；现有模组库不是实例内模组管理。'},
  {id:'defaults',group:'社区与资源',title:'默认游戏设置、按键与资源包同步',capability:'gameOptions',state:'partial',detail:'游戏选项可逐项选择并在原版1.20.6/1.21.1启动前备份合并；按键、资源包与其他版本待接入。此处不执行文件同步检查。'},
  {id:'schematics',group:'社区与资源',title:'投影管理、编辑与游戏同步',capability:'resources',state:'partial',detail:'可导入、复制、改名、备份移除和恢复到实例schematics；面板方块预览/编辑待后续，游戏内需要Litematica等兼容组件。'},
  {id:'resource-details',group:'社区与资源',title:'资源分类、多选、图库与中文说明',state:'partial',detail:'已有模组搜索及文件信息；完整筛选、图库和翻译待接入。'},
  {id:'servers',group:'联机与扩展',title:'服务器收藏、延迟检测与启动直连',capability:'playerServers',state:'partial',detail:'本机收藏、手动TCP连接延迟已接；端口可达不代表可入服。1.20.6/1.21.1单次直连仍经身份/Java/完整性检查，其余版本游戏内加入。'},
  {id:'multiplayer',group:'联机与扩展',title:'FRP、VoxLink、Terracotta 与好友直连',state:'pending',detail:'IPv4/IPv6/UPnP/邀请及对应联机服务尚未接入。'},
  {id:'bridge',group:'联机与扩展',title:'Fabric MOD 参数桥接面板',state:'pending',detail:'实时读取/修改 MOD 参数的游戏端组件尚未接入。'},
  {id:'plugins',group:'联机与扩展',title:'插件安装、启停与移除',state:'pending',detail:'独立插件宿主与权限尚未接入。'},
  {id:'news',group:'管理与交付',title:'Minecraft/MUA 新闻与后台优先度',state:'pending',detail:'新闻聚合、管理链接与排序尚未接入。'},
  {id:'cloud',group:'管理与交付',title:'云端面板账号、玩家组、下发与上报',state:'pending',detail:'当前仅保留接口边界；服务器和云端最后接入。'},
  {id:'updater',group:'管理与交付',title:'外置整合包更新组件',state:'partial',detail:'原更新引擎保留；可安装、可移除的外置隔离尚未完成。'},
  {id:'self-update',group:'管理与交付',title:'启动器自更新与回滚',state:'pending',detail:'三秋叶自身更新待接入，与整合包更新不同。'},
  {id:'settings',group:'管理与交付',title:'下载/日志目录、网络、隐私和完整设置',state:'partial',detail:'已有实例运行设置及有限外观；完整设置、日志导出和按类型目标目录待接入。'},
  {id:'delivery',group:'管理与交付',title:'Windows/macOS 桌面发行',state:'partial',detail:'采用 Tauri 独立窗口；各平台成品、安装器与更新能力分别验收。'}
];
// 只组合已有状态，不探测远程服务，也不触发下载、认证或游戏操作。
export function featureStatus(input:FeatureStatusInput):FeatureStatusRow[]{
  return definitions.map(definition=>{
    const {capability,...row}=definition;
    if(input.capabilities.server==='LIVE'&&row.id==='cloud')return {...row,state:'partial',detail:'已配置真实面板账号服务，注册/登录与管理资料查询已接；本次连接结果看首页。整合包下发、游戏状态上报及其余云端能力待接入。'};
    if(input.capabilities.server==='MOCK'&&row.id==='cloud')return {...row,state:'partial',detail:'离线演练可验证账号选择、分组、模拟包下发和报告；未连接真实服务器，不代表真实认证、上传或游戏已可用。'};
    if(row.id==='workspace'&&input.workspaceWarning)return {...row,state:'error',detail:input.workspaceWarning};
    if(!capability)return row;
    if(!input.local)return {...row,state:'unchecked',detail:'当前为预览，未连接本机能力。'+row.detail};
    const state=input.capabilities[capability];
    if(row.id==='loaders'&&state===undefined)return {...row,state:'pending',detail:'当前后端未提供更多核心安装接口，请使用同版本完整程序包。'};
    if(state==='UNAVAILABLE')return {...row,state:'error',detail:'本机当前未提供该能力。'};
    if(state!=='AVAILABLE')return {...row,state:state==='PENDING'||state==='UNCONFIGURED'?'pending':'unchecked',detail:'本机尚未提供或确认该能力。'};
    if(row.id==='instances'){
      if(input.instanceError)return {...row,state:'error',detail:input.instanceError};
      if(input.instancesLoading)return {...row,state:'unchecked',detail:'正在读取本机实例。'};
      if(input.instanceProblems)return {...row,state:'partial',detail:`检测到 ${input.instanceProblems} 条实例记录问题，请在实例页查看。`};
    }
    return row;
  });
}
