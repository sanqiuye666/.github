import {Marked,Renderer} from 'marked';
const labels:Record<string,[string,string]>={
 adventure:['冒险与探索','探索、冒险与角色成长'],challenging:['挑战与硬核','高难度生存和挑战玩法'],combat:['战斗','战斗机制与竞技玩法'], 'kitchen-sink':['综合玩法','汇集多种玩法的大型整合包'],lightweight:['轻量','适合较少模组与简洁体验'],magic:['魔法','法术、仪式和魔法系统'],multiplayer:['多人','面向好友和服务器游玩'],optimization:['性能优化','改善性能、流畅度与资源占用'],quests:['任务','以任务和进度引导游玩'],technology:['科技','机械、能源和自动化'],
 cursed:['趣味与整活','非常规、实验性和娱乐玩法'],decoration:['建筑装饰','建筑方块、家具和装饰内容'],economy:['经济','交易、货币和经济系统'],equipment:['装备','工具、武器、护甲与附加装备'],food:['食物与农业','烹饪、作物和食物内容'],'game-mechanics':['游戏机制','拓展或调整游戏机制'],library:['前置与程序库','供其他模组使用的基础依赖'],management:['管理','游戏或服务器管理工具'],minigame:['小游戏','独立小游戏与趣味模式'],mobs:['生物','新增或改善生物内容'],social:['社交','玩家互动和交流'],storage:['存储','物品整理、仓储和物流'],transportation:['交通运输','车辆、移动与运输系统'],utility:['实用工具','地图、信息显示和辅助工具'],worldgen:['世界生成','群系、地形、维度与结构'],
 'Map and Information':['地图与信息','地图、导航与信息显示'],'World Gen':['世界生成','群系、地形、维度与结构'],'Adventure and RPG':['冒险与角色成长','探索与角色成长内容'],'Technology':['科技','机械与自动化'],'Magic':['魔法','法术与魔法系统'],'Storage':['存储','物品整理与仓储'],'Food':['食物','烹饪与食物内容'],'Library and API':['前置与API','供其他模组使用的基础依赖']
};
export function categoryLabel(id:string,name=id){return labels[id]?.[0]??labels[name]?.[0]??name;}
export function categoryDescription(id:string,name=id){return labels[id]?.[1]??labels[name]?.[1]??`来源分类：${name}`;}
const imageHosts=new Set(['cdn.modrinth.com','media.forgecdn.net','mediafilez.forgecdn.net','cdn.feed-the-beast.com','cdn.atlcdn.net']);
export function projectImage(value?:string|null){try{if(!value||value.length>4096)return '';const u=new URL(value);return u.protocol==='https:'&&!u.username&&!u.password&&(!u.port||u.port==='443')&&imageHosts.has(u.hostname)?u.href:'';}catch{return '';}}
const renderer=new Renderer();renderer.html=()=>'';renderer.image=()=>'';renderer.link=function({tokens}){return this.parser.parseInline(tokens);};
const markdown=new Marked({gfm:true,renderer});
export function projectDescription(value:string){return markdown.parse(value.slice(0,262144),{async:false}) as string;}
export function projectDate(value?:string|null){if(!value)return '未提供';const date=new Date(value);return Number.isNaN(date.getTime())?'未提供':date.toLocaleDateString('zh-CN');}
