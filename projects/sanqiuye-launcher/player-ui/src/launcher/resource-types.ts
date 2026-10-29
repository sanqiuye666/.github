export type ResourceKind='RESOURCEPACKS'|'SHADERS'|'RECORDINGS'|'SCHEMATICS';
export interface ResourceEntry {name:string;size:number;modifiedAt:string;revision:string}
export interface ResourceBackup {id:string;name:string;kind:ResourceKind;size:number;sha256:string;createdAt:string}
export interface ResourceListing {instanceId:string;kind:ResourceKind;directory:string;entries:ResourceEntry[];backups:ResourceBackup[];problems:{name:string;message:string}[]}
export interface PlayerServer {id:string;name:string;address:{host:string;port:number}}
export interface PlayerServerTarget extends PlayerServer {revision:string}
export interface PlayerServersSnapshot {revision:string;servers:PlayerServer[]}
export interface ServerProbeResult {state:'REACHABLE'|'UNREACHABLE'|'TIMEOUT'|'MOCK';latencyMs:number|null;checkedAt:string;message:string}
export const resourceInfo:Record<ResourceKind,{title:string;help:string}>={
 RESOURCEPACKS:{title:'资源包',help:'ZIP导入到此实例，进入游戏内选择启用；不自动改变你的材质顺序。'},
 SHADERS:{title:'光影包',help:'ZIP导入到shaderpacks，游戏内选择启用；需要兼容的Iris或OptiFine组件，导入不等于已启用。'},
 RECORDINGS:{title:'录像',help:'管理ReplayMod的mcpr文件，游戏内通过ReplayMod播放；本页不伪装为视频播放器。'},
 SCHEMATICS:{title:'投影',help:'导入litematic/schem/schematic/nbt到schematics；可复制独立副本供游戏内编辑，需要兼容的Litematica等组件。面板方块预览与编辑仍待后续。'}
};
