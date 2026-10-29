/** 双端共用公开契约，不含游戏凭据或本地文件路径。 */
export interface ServerStatus {enabled:boolean;source:'MOCK'|'LIVE'|'DISABLED';role:string;scenario:string;message:string}
export interface CloudAccount {id:string;username:string;displayName:string;groupId:string;createdAt:string;lastSeenAt:string}
export interface CloudAccountDirectory {accounts:CloudAccount[];total:number;limit:number;checkedAt:string}
export interface PlayerReport {packId:string;releaseId:string;status:string;updatedAt:string;source:'MOCK'|'LIVE'}
export interface PanelProfile {id:string;username:string;displayName:string;groupId:string;revision:number;report?:PlayerReport}
export interface ManagedPack {id:string;name:string;fileName:string;sizeBytes:number;gameVersion:string;loader:string;status:'PREVIEW'|'PUBLISHED'|'UNLISTED'|'DELETED';revision:number;releaseId?:string;assignments:{type:'player'|'group';targetId:string}[];source:'MOCK'|'LIVE'}
export interface PlayerServerView {status:ServerStatus;profile:PanelProfile;packs:ManagedPack[]}
export interface AdminServerView {status:ServerStatus;players:PanelProfile[];groups:{id:string;name:string}[];packs:ManagedPack[]}
export class ServerCallError extends Error {
 constructor(readonly code:string,message:string,readonly requestId:string){super(`${code} · ${message}（请求 ${requestId}）`);}
}
export function unwrapServerResult<T>(value:unknown):T {
 const v=value as {schemaVersion?:number;source?:string;requestId?:string;data?:T;error?:{code?:string;message?:string};attempts?:number;durationMs?:number};
 if(!v||v.schemaVersion!==1||!['MOCK','LIVE','DISABLED'].includes(v.source??'')||typeof v.requestId!=='string'||!Number.isInteger(v.attempts)||!Number.isFinite(v.durationMs))throw new ServerCallError('INVALID_DATA','返回数据无法验证','unavailable');
 if(v.error){if(typeof v.error.code!=='string'||typeof v.error.message!=='string')throw new ServerCallError('INVALID_DATA','返回错误无法验证',v.requestId);throw new ServerCallError(v.error.code,v.error.message,v.requestId);}
 if(v.data===undefined||v.data===null)throw new ServerCallError('INVALID_DATA','缺少结果数据',v.requestId);
 return v.data;
}
export const packStatusLabel=(status:ManagedPack['status'])=>({PREVIEW:'待发布',PUBLISHED:'已发布',UNLISTED:'已下架',DELETED:'保留区'})[status];
export const reportStatusLabel=(status:string)=>({UPDATED:'模拟更新完成',FAILED:'模拟更新失败',SIMULATED_LAUNCH:'已演练启动流程'}[status]??'尚未上报');

export interface PendingServerWrite {path:string;body:Record<string,unknown>}
type JournalStorage=Pick<Storage,'getItem'|'setItem'|'removeItem'>;
const operationFields:Record<string,readonly string[]>={
 '/register':['username','displayName'],'/login':['username'],'/logout':[],
 '/report':['packId','status'],'/profile':['revision','displayName'],
 '/upload':['name','fileName','sizeBytes','gameVersion','loader'],'/publish':['packId','revision'],
 '/assign':['packId','revision','targetType','targetId'],'/unlist':['packId','revision'],
 '/delete':['packId','revision','confirmation'],'/restore':['packId','revision'],
 '/set-group':['playerId','revision','groupId']
};
/** 只保存公开模拟写入元信息。离开页面后保留编号；刷新永远不重放写入。 */
export class PendingServerWriteJournal {
 pending:PendingServerWrite|null=null;problem='';readonly key:string;
 constructor(readonly role:'player'|'admin',readonly storage:JournalStorage){
  this.key='sqy-server-mock-pending-v1-'+role;
  try{const raw=storage.getItem(this.key);if(raw){if(raw.length>8192)throw Error();const value=JSON.parse(raw);this.validate(value);this.pending=value;}}
  catch{this.problem='未决操作记录无法读取，请保留记录并核对结果；暂不允许新增写入。';}
 }
 private validate(value:PendingServerWrite){
  if(!value||typeof value.path!=='string'||!operationFields[value.path]||!value.body||typeof value.body!=='object'||Array.isArray(value.body))throw Error('未决记录格式错误');
  const player=['/register','/login','/logout','/report','/profile'].includes(value.path);
  if(player!==(this.role==='player'))throw Error('未决记录角色不符');
  if(Object.entries(value.body).some(([k,v])=>![...operationFields[value.path]!,'operationId'].includes(k)||!['string','number','boolean'].includes(typeof v)||typeof v==='string'&&(v.length>4096||/[\u0000-\u001f]/.test(v))))throw Error('未决记录包含不允许的字段');
  if(!['/login','/logout'].includes(value.path)&&!/^[-a-f0-9]{36}$/.test(String(value.body.operationId)))throw Error('未决编号无效');
 }
 begin(path:string,body:Record<string,unknown>):PendingServerWrite{
  if(this.problem)throw Error(this.problem);if(this.pending)throw Error('存在未决操作，请先复查或使用原编号重试。');
  const value={path,body:{...body,...(!['/login','/logout'].includes(path)?{operationId:crypto.randomUUID()}:{})}};
  this.validate(value);const raw=JSON.stringify(value);if(raw.length>8192)throw Error('未决操作记录过大');
  this.storage.setItem(this.key,raw);this.pending=value;return value;
 }
 completed(){this.storage.removeItem(this.key);this.pending=null;}
 failed(error:unknown){if(error instanceof ServerCallError&&error.requestId!=='unavailable'&&['CONFLICT','AUTH_FAILED','FORBIDDEN','INVALID_DATA','CONFIRMATION_REQUIRED','FEATURE_DISABLED'].includes(error.code))this.completed();}
}
