export type PresetId='workbench'|'simple'|'cards'|'immersive';
export type Arrangement='forced'|'free';
export type WidgetId='identity'|'instances'|'launch'|'tasks'|'environment'|'updates'|'guide';
export interface Placement {widget:WidgetId;x:number;y:number;width:number;height:number}
export interface WorkspaceAppearance {accent:'jade'|'amber'|'violet';opacity:85|92|100;showBackground:boolean}
export interface WorkspaceLayout {id:string;name:string;preset:PresetId;arrangement:Arrangement;placements:Placement[];appearance:WorkspaceAppearance}
export interface WorkspacePreferences {schemaVersion:1;activeId:string;custom:WorkspaceLayout[];introEnabled:boolean;guideCompleted:boolean;theme:'light'|'forest'}
export class LayoutError extends Error {}
export const PRESETS:readonly {id:PresetId;name:string;description:string}[]=[{id:'workbench',name:'工作台',description:'实例与状态集中，操作一目了然'},{id:'simple',name:'简洁',description:'聚焦身份、实例和下一次启动'},{id:'cards',name:'卡片',description:'清晰分组，熟悉的背景主卡片'},{id:'immersive',name:'沉浸',description:'让背景成为舞台，操作始终可达'}];
export const REQUIRED_WIDGETS:readonly WidgetId[]=['identity','instances','launch','tasks'];
export const WIDGETS:readonly {id:WidgetId;name:string}[]=[{id:'identity',name:'游戏身份'},{id:'instances',name:'游戏实例'},{id:'launch',name:'启动入口'},{id:'tasks',name:'下载与运行记录'},{id:'environment',name:'Java 与环境'},{id:'updates',name:'更新与备份'},{id:'guide',name:'使用帮助'}];
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const fail=(message:string):never=>{throw new LayoutError(message);};
function object(value:unknown,keys:string[]):Record<string,unknown>{if(!value||typeof value!=='object'||Array.isArray(value)||Object.keys(value).sort().join()!==keys.sort().join())fail('布局字段无效，请恢复预设。');return value as Record<string,unknown>;}
export function createPreset(id:PresetId):WorkspaceLayout {
 const def=PRESETS.find(p=>p.id===id);if(!def)fail('没有这个预设。');
 const rows:Record<PresetId,[WidgetId,number,number,number,number][]>={
  workbench:[['instances',0,0,8,5],['identity',8,0,4,3],['launch',0,5,8,3],['tasks',8,3,4,5],['environment',0,8,4,3],['updates',4,8,4,3],['guide',8,8,4,3]],
  simple:[['identity',0,0,6,3],['instances',6,0,6,3],['launch',0,3,12,3],['tasks',0,6,12,3]],
  cards:[['identity',0,0,4,3],['instances',4,0,4,3],['launch',8,0,4,3],['tasks',0,3,8,3],['environment',8,3,4,3],['updates',0,6,6,3],['guide',6,6,6,3]],
  immersive:[['identity',0,0,4,3],['launch',4,0,8,3],['instances',0,3,8,3],['tasks',8,3,4,3]]};
 return {id:'preset:'+id,name:def!.name,preset:id,arrangement:'forced',placements:rows[id].map(([widget,x,y,width,height])=>({widget,x,y,width,height})),appearance:{accent:'jade',opacity:92,showBackground:true}};
}
export function validateLayout(input:unknown):WorkspaceLayout {
 const a=object(input,['id','name','preset','arrangement','placements','appearance']);
 const appearance=object(a.appearance,['accent','opacity','showBackground']);if(!['jade','amber','violet'].includes(String(appearance.accent))||![85,92,100].includes(appearance.opacity as number)||typeof appearance.showBackground!=='boolean')fail('外观设置无效，请选择预置选项。');
 if(typeof a.id!=='string'||!(uuid.test(a.id)||PRESETS.some(p=>a.id==='preset:'+p.id)))fail('布局标识无效。');
 if(typeof a.name!=='string'||!a.name.trim()||Array.from(a.name).length>32||/[\x00-\x1f\x7f]/.test(a.name))fail('布局名称须为1–32个字。');
 if(!PRESETS.some(p=>p.id===a.preset)||!['forced','free'].includes(String(a.arrangement))||!Array.isArray(a.placements)||a.placements.length>WIDGETS.length)fail('排列或模块无效。');
 const placements:Placement[]=[];
 for(const value of a.placements as unknown[]){const p=object(value,['widget','x','y','width','height']);if(!WIDGETS.some(w=>w.id===p.widget)||placements.some(w=>w.widget===p.widget))fail('模块未知或重复。');
  const {x,y,width,height}=p as unknown as Placement;if(![x,y,width,height].every(Number.isInteger)||x<0||y<0||width<3||width>12||height<2||height>24||x+width>12||y+height>120)fail('模块超出工作区或尺寸无效。');
  if(placements.some(v=>x<v.x+v.width&&x+width>v.x&&y<v.y+v.height&&y+height>v.y))fail('模块相互遮挡，请移动或自动整理。');placements.push({...p} as unknown as Placement);
 }
 if(REQUIRED_WIDGETS.some(id=>!placements.some(p=>p.widget===id)))fail('基础模块不能移除。');
 return {...a,placements,appearance:{...appearance}} as unknown as WorkspaceLayout;
}
export function defaultPreferences():WorkspacePreferences{return {schemaVersion:1,activeId:'preset:workbench',custom:[],introEnabled:true,guideCompleted:false,theme:'light'};}
export function validatePreferences(input:unknown):WorkspacePreferences {
 let json:string;try{json=JSON.stringify(input);}catch{return fail('配置无法读取。');}if(!json||new TextEncoder().encode(json).length>65536)fail('布局配置超过64KiB。');
 const hasTheme=!!input&&typeof input==='object'&&Object.hasOwn(input,'theme');const a=object(input,['schemaVersion','activeId','custom','introEnabled','guideCompleted',...(hasTheme?['theme']:[])]);if(a.schemaVersion!==1||!Array.isArray(a.custom)||a.custom.length>4||typeof a.introEnabled!=='boolean'||typeof a.guideCompleted!=='boolean'||hasTheme&&!['light','forest'].includes(String(a.theme)))fail('配置无效，最多保存4套自定义布局。');
 const custom=(a.custom as unknown[]).map(validateLayout);if(custom.some(l=>!uuid.test(l.id))||new Set(custom.map(l=>l.id)).size!==custom.length)fail('自定义布局标识无效或重复。');
 if(![...PRESETS.map(p=>'preset:'+p.id),...custom.map(l=>l.id)].includes(String(a.activeId)))fail('当前布局不存在。');return {...a,custom,theme:hasTheme?a.theme:'forest'} as unknown as WorkspacePreferences;
}
export function activeLayout(value:WorkspacePreferences):WorkspaceLayout{return value.custom.find(l=>l.id===value.activeId)??createPreset(value.activeId.slice(7) as PresetId);}
export function arrangeWidgets(layout:WorkspaceLayout):WorkspaceLayout {let y=0;const maxHeight=Math.floor(120/layout.placements.length);return {...layout,placements:layout.placements.map(p=>{const height=Math.min(p.height,maxHeight),next={...p,x:0,y,width:12,height};y+=height;return next;})};}
