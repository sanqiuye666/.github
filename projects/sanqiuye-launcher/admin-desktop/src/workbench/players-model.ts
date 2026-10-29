export type PlayerStatus='ACTIVE'|'DISABLED'|'UNKNOWN';
export interface PlayerSummary {id:string;username:string;groupId:string|null;status:PlayerStatus;gameVersion:string|null;packVersion:string|null;playTimeSeconds:number|null;lastSeen:string|null}
export interface PlayerFilters {query:string;groupId:string;status:'ALL'|PlayerStatus}
export interface DispatchOverview {state:'PENDING';players:PlayerSummary[];groups:{id:string;name:string;defaultGroup:boolean}[];policies:{groupId:string;projectId:string}[]}
export const dispatchOverview=():DispatchOverview=>({state:'PENDING',players:[],groups:[],policies:[]});
export function filterPlayers(rows:readonly PlayerSummary[],filters:PlayerFilters):PlayerSummary[]{
 const query=filters.query.trim().toLowerCase();
 return rows.filter(player=>(!query||player.username.toLowerCase().includes(query)||player.id.toLowerCase().includes(query))&&(!filters.groupId||player.groupId===filters.groupId)&&(filters.status==='ALL'||player.status===filters.status));
}
export const playerValue=(value:string|number|null|undefined):string=>value==null?'—':String(value);
export const playerStatusLabel=(status:PlayerStatus):string=>({ACTIVE:'启用',DISABLED:'禁用',UNKNOWN:'未知'})[status];
