import type {IconName} from '../lib/icons';
export type CapabilityState='AVAILABLE'|'PENDING'|'UNCONFIGURED'|'UNAVAILABLE'|'MOCK'|'DISABLED'|'LIVE';
export type LauncherPage='home'|'versions'|'mods'|'packs'|'resourcepacks'|'shaders'|'recordings'|'schematics'|'bridge'|'servers'|'instances'|'accounts'|'updates'|'defaults'|'tools'|'settings';
export interface LauncherPageDefinition {id:LauncherPage;label:string;icon:IconName;state:CapabilityState;description:string}
export interface LauncherSession {mode:'login'|'preview'|'local';panelIdentity:null;gameIdentity:null}
export const createLauncherSession=():LauncherSession=>({mode:'login',panelIdentity:null,gameIdentity:null});
export const stateLabel=(state:CapabilityState)=>({AVAILABLE:'已就绪',PENDING:'待接入',UNCONFIGURED:'未配置',UNAVAILABLE:'不可用',MOCK:'离线演练',DISABLED:'未启用',LIVE:'真实服务'})[state];
export const LAUNCHER_PAGES:readonly LauncherPageDefinition[]=[
  {id:'home',label:'首页',icon:'play',state:'AVAILABLE',description:'下一场冒险，从这里开始。'},
  {id:'versions',label:'版本中心',icon:'archive',state:'PENDING',description:'为每一个世界，选择合适的游戏版本。'},
  {id:'mods',label:'模组中心',icon:'files',state:'PENDING',description:'发现新玩法，让你的世界更有意思。'},
  {id:'packs',label:'整合包',icon:'archive',state:'PENDING',description:'从不同官方来源，寻找新的游戏玩法。'},
  {id:'resourcepacks',label:'资源包',icon:'folder',state:'PENDING',description:'资源包的本地管理、导入与启用待接入。'},
  {id:'shaders',label:'光影包',icon:'play',state:'PENDING',description:'光影包的本地管理与兼容检查待接入。'},
  {id:'recordings',label:'录像',icon:'history',state:'PENDING',description:'游戏录像的浏览和播放待接入。'},
  {id:'schematics',label:'投影',icon:'archive',state:'PENDING',description:'投影文件导入、预览、编辑副本和同步游戏待接入。'},
  {id:'bridge',label:'MOD 面板',icon:'logs',state:'PENDING',description:'游戏内模组参数读取与修改需要独立桥接组件，尚未接入。'},
  {id:'servers',label:'服务器',icon:'info',state:'PENDING',description:'服务器收藏、延迟检测与启动直连待接入。'},
  {id:'instances',label:'游戏实例',icon:'folder',state:'PENDING',description:'不同版本、不同玩法，各有自己的空间。'},
  {id:'accounts',label:'账号',icon:'info',state:'PENDING',description:'管理面板账号与本机游戏身份。'},
  {id:'updates',label:'更新与本地管理',icon:'history',state:'PENDING',description:'看清每一次变化，也保留自己的选择。'},
  {id:'defaults',label:'默认配置',icon:'logs',state:'PENDING',description:'让新实例保留自己的游戏设置。'},
  {id:'tools',label:'创作工具',icon:'files',state:'AVAILABLE',description:'编辑皮肤，生成彩字，把自己的想法带进世界。'},
  {id:'settings',label:'设置',icon:'logs',state:'PENDING',description:'让启动器适合你的电脑与使用习惯。'},
];
