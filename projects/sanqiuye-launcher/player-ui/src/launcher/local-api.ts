import {currentEntryContext,type EntryContext} from './entry';
import {nativeRequest,nativeSession} from './native-api';
import type {CatalogPage,CatalogQuery,CreateInstance,GameInstance,InstanceListing,LocalSession,InstallationPlan,InstallationSummary,TaskSnapshot,TaskPage} from './local-types';
import type {ModSource,SourceStatus,ModProject,ModPage,ModSearchParams,ModFileRef,ModFileQuery,ModFilesPage,ModLibraryEntry,RecordProblem} from './local-types';
import type {RuntimeListing,RuntimeDiscovery,IdentityListing,LaunchSettings,VersionedLaunchSettings,LaunchCheck,LaunchSnapshot,LaunchLogPage} from './launch-types';
import type {PackSource,PackSourceStatus,PackPage,PackDetails} from './pack-types';

const API='/api/launcher/v1';
const baseHeaders={'X-SQY-Client':'launcher',Accept:'application/json'};
export class LocalApiError extends Error {
  constructor(readonly code:string,message:string,readonly status:number,readonly retryAfterSeconds?:number){super(message);}
}
export async function discoverLocalSession(context:EntryContext,signal?:AbortSignal):Promise<LocalSession|null>{
  const query=new URLSearchParams(context.search);
  if(context.embedded||context.pathname.startsWith('/player-preview/')||query.get('adminPreview')==='1'||query.get('updaterPreview')==='1')return null;
  if(context.native){
    const value=await nativeSession(signal);
    if(value?.schemaVersion!==1||value.mode!=='LOCAL'||typeof value.sessionToken!=='string'||!/^[a-f0-9]{64}$/.test(value.sessionToken)||!value.capabilities||typeof value.capabilities!=='object')throw new Error('本机核心会话无效，请重新尝试');
    return value;
  }
  const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),4000);
  const abort=()=>controller.abort();signal?.addEventListener('abort',abort,{once:true});
  try{
    if(signal?.aborted)return null;
    const response=await fetch(`${API}/session`,{headers:baseHeaders,mode:'same-origin',credentials:'omit',cache:'no-store',redirect:'error',signal:controller.signal});
    if(!response.ok||!response.headers.get('Content-Type')?.includes('application/json'))return null;
    const value=await response.json();
    if(value?.schemaVersion!==1||value.mode!=='LOCAL'||typeof value.sessionToken!=='string'||!/^[a-f0-9]{64}$/.test(value.sessionToken)||!value.capabilities||typeof value.capabilities!=='object')return null;
    return value as LocalSession;
  }catch{return null;}finally{clearTimeout(timeout);signal?.removeEventListener('abort',abort);}
}

export class LocalLauncherClient {
  resources(instanceId:string,kind:import('./resource-types').ResourceKind,signal?:AbortSignal){return this.#request<import('./resource-types').ResourceListing>('/resources?'+this.#params({instanceId,kind}),'GET',undefined,signal);}
  importResource(instanceId:string,kind:import('./resource-types').ResourceKind,source:string,signal?:AbortSignal){return this.#request<import('./resource-types').ResourceListing>('/resources/import','POST',{instanceId,kind,source},signal);}
  changeResource(instanceId:string,kind:import('./resource-types').ResourceKind,entry:import('./resource-types').ResourceEntry,operation:'COPY'|'RENAME'|'REMOVE',target:string|null,signal?:AbortSignal){return this.#request<import('./resource-types').ResourceListing>('/resources/change','POST',{instanceId,kind,name:entry.name,revision:entry.revision,operation,target},signal);}
  restoreResource(instanceId:string,kind:import('./resource-types').ResourceKind,backupId:string,signal?:AbortSignal){return this.#request<import('./resource-types').ResourceListing>('/resources/restore','POST',{instanceId,kind,backupId},signal);}
  playerServers(signal?:AbortSignal){return this.#request<import('./resource-types').PlayerServersSnapshot>('/player-servers','GET',undefined,signal);}
  savePlayerServer(revision:string,id:string|null,name:string,host:string,port:number,signal?:AbortSignal){return this.#request<import('./resource-types').PlayerServersSnapshot>('/player-servers/save','POST',{revision,id,name,host,port},signal);}
  removePlayerServer(revision:string,id:string,signal?:AbortSignal){return this.#request<import('./resource-types').PlayerServersSnapshot>('/player-servers/remove','POST',{revision,id},signal);}
  probePlayerServer(id:string,signal?:AbortSignal){return this.#request<import('./resource-types').ServerProbeResult>('/player-servers/probe','POST',{id},signal);}
  serverCall(path:string,body?:Record<string,unknown>,signal?:AbortSignal){return this.#request<unknown>('/server'+path,body===undefined?'GET':'POST',body,signal);}
  gameOptions(signal?:AbortSignal){return this.#request<import('./game-options-types').GameOptionsSnapshot>('/game-options','GET',undefined,signal);}
  saveGameOptions(revision:string,enabled:boolean,values:Record<string,import('./game-options-types').GameOptionValue>,signal?:AbortSignal){return this.#request<import('./game-options-types').GameOptionsSnapshot>('/game-options','POST',{revision,enabled,values},signal);}
  #token:string;#closed=false;#requests=new Set<AbortController>();#onInvalid?:()=>void;
  constructor(token:string,onInvalid?:()=>void){this.#token=token;this.#onInvalid=onInvalid;}
  close(){this.#closed=true;this.#token='';for(const request of this.#requests)request.abort();this.#requests.clear();}
  async #request<T>(path:string,method:'GET'|'POST',data?:unknown,signal?:AbortSignal):Promise<T>{
    if(this.#closed)throw new LocalApiError('SESSION_REQUIRED','本机会话已关闭，请重新连接',403);
    const controller=new AbortController(),abort=()=>controller.abort();
    if(signal?.aborted)throw new DOMException('操作已取消','AbortError');
    signal?.addEventListener('abort',abort,{once:true});this.#requests.add(controller);
    const timeout=setTimeout(abort,40000);
    const headers:Record<string,string>={...baseHeaders,'X-SQY-Session':this.#token};
    const native=currentEntryContext().native;
    const cancelPath=method==='POST'&&['/resources/import','/resources/change','/resources/restore'].includes(path)?'/resources/cancel':(path.startsWith('/mods/instance?')||path==='/mods/instance/plan')?'/mods/instance/cancel-query':method==='GET'&&path.startsWith('/packs/')&&path!=='/packs/sources'?'/packs/cancel':(path.startsWith('/installations/fabric/versions')||path.startsWith('/installations/loader/versions'))||method==='POST'&&path==='/installations/plan'?'/installations/cancel-query':null;
    const requestId=cancelPath?crypto.randomUUID():null;
    if(requestId)path+=(path.includes('?')?'&':'?')+'requestId='+requestId;
    const cancelPack=()=>{
      if(!requestId)return;
      const cleanup=new AbortController(),deadline=setTimeout(()=>cleanup.abort(),3000);
      const stopping=native?nativeRequest('POST',cancelPath!,{requestId},cleanup.signal):fetch(API+cancelPath,{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({requestId}),signal:cleanup.signal,mode:'same-origin',credentials:'omit',cache:'no-store',redirect:'error'});
      void stopping.catch(()=>{}).finally(()=>clearTimeout(deadline));
    };
    controller.signal.addEventListener('abort',cancelPack,{once:true});
    try{
      if(method==='POST')headers['Content-Type']='application/json';
      const response=native
        ?await nativeRequest(method,path,method==='POST'?data??{}:null,controller.signal)
        :await fetch(API+path,{method,headers,body:method==='POST'?JSON.stringify(data??{}):undefined,signal:controller.signal,mode:'same-origin',credentials:'omit',cache:'no-store',redirect:'error'});
      if(!response.headers.get('Content-Type')?.includes('application/json'))throw new LocalApiError('INVALID_RESPONSE','本机服务返回了无法识别的结果',response.status);
      const value=await response.json();
      if(!response.ok){
        const error=new LocalApiError(value.error?.code??'REQUEST_FAILED',value.error?.message??'操作未完成，请重试',response.status,value.error?.retryAfterSeconds);
        if(response.status===401||response.status===403){this.close();this.#onInvalid?.();}
        throw error;
      }
      return value as T;
    }finally{clearTimeout(timeout);signal?.removeEventListener('abort',abort);controller.signal.removeEventListener('abort',cancelPack);this.#requests.delete(controller);}
  }
  catalog(query:CatalogQuery={},signal?:AbortSignal){
    const params=new URLSearchParams();for(const [key,value] of Object.entries(query))if(value!==undefined&&value!=='')params.set(key,String(value));
    return this.#request<CatalogPage>('/catalog'+(params.size?'?'+params.toString():''),'GET',undefined,signal);
  }
  refreshCatalog(signal?:AbortSignal){return this.#request<CatalogPage>('/catalog/refresh','POST',{},signal);}
  instances(signal?:AbortSignal){return this.#request<InstanceListing>('/instances','GET',undefined,signal);}
  createInstance(input:CreateInstance,signal?:AbortSignal){return this.#request<{instance:GameInstance}>('/instances','POST',input,signal);}
  planInstallation(instanceId:string,signal?:AbortSignal){return this.#request<InstallationPlan>('/installations/plan','POST',{instanceId},signal);}
  fabricVersions(instanceId:string,signal?:AbortSignal){return this.#request<{versions:{version:string;stable:boolean}[]}>('/installations/fabric/versions?'+new URLSearchParams({instanceId}),'GET',undefined,signal);}
  planFabricInstallation(instanceId:string,loaderVersion:string,signal?:AbortSignal){return this.#request<InstallationPlan>('/installations/plan','POST',{instanceId,loader:'FABRIC',loaderVersion},signal);}
  loaderVersions(instanceId:string,loader:string,signal?:AbortSignal){return this.#request<{versions:{version:string;stable:boolean}[]}>('/installations/loader/versions?'+new URLSearchParams({instanceId,loader}),'GET',undefined,signal);}
  planLoaderInstallation(instanceId:string,loader:string,loaderVersion:string,signal?:AbortSignal){return this.#request<InstallationPlan>('/installations/plan','POST',{instanceId,loader,loaderVersion},signal);}
  install(planId:string,signal?:AbortSignal){return this.#request<{task:TaskSnapshot}>('/installations','POST',{planId},signal);}
  installations(instanceId:string,signal?:AbortSignal){return this.#request<{installations:InstallationSummary[];problems:{installationId:string;reason:string}[]}>('/installations?'+new URLSearchParams({instanceId}),'GET',undefined,signal);}
  verifyInstallation(instanceId:string,signal?:AbortSignal){return this.#request<{task:TaskSnapshot}>('/installations/verify','POST',{instanceId},signal);}
  tasks(query:{offset?:number;limit?:number}={},signal?:AbortSignal){return this.#request<TaskPage>('/tasks?'+new URLSearchParams(Object.entries(query).map(([k,v])=>[k,String(v)])),'GET',undefined,signal);}
  task(id:string,signal?:AbortSignal){return this.#request<{task:TaskSnapshot}>('/tasks/'+encodeURIComponent(id),'GET',undefined,signal);}
  cancelTask(id:string,signal?:AbortSignal){return this.#request<{task:TaskSnapshot}>('/tasks/'+encodeURIComponent(id)+'/cancel','POST',{},signal);}
  retryTask(id:string,signal?:AbortSignal){return this.#request<{task:TaskSnapshot}>('/tasks/'+encodeURIComponent(id)+'/retry','POST',{},signal);}
  #params(query:object){return new URLSearchParams(Object.entries(query).filter(([,v])=>v!==undefined&&v!=='').map(([k,v])=>[k,String(v)])).toString();}
  modSources(signal?:AbortSignal){return this.#request<{sources:SourceStatus[]}>('/mods/sources','GET',undefined,signal);}
  packSources(signal?:AbortSignal){return this.#request<{sources:PackSourceStatus[]}>('/packs/sources','GET',undefined,signal);}
  contentCategories(kind:'mods'|'packs',source:string,signal?:AbortSignal){return this.#request<{categories:import('./content-types').ContentCategory[]}>('/'+kind+'/categories?'+this.#params({source}),'GET',undefined,signal);}
  searchPacks(query:{source:PackSource;q?:string;offset?:number;limit?:number;category?:string;gameVersion?:string;loader?:string;sort?:string},signal?:AbortSignal){return this.#request<PackPage>('/packs/search?'+this.#params(query),'GET',undefined,signal);}
  packDetails(source:PackSource,id:string,signal?:AbortSignal){return this.#request<PackDetails>('/packs/'+source+'/'+encodeURIComponent(id),'GET',undefined,signal);}
  searchMods(query:ModSearchParams,signal?:AbortSignal){return this.#request<ModPage>('/mods/search?'+this.#params(query),'GET',undefined,signal);}
  modProject(source:ModSource,projectId:string,signal?:AbortSignal){return this.#request<{project:ModProject}>('/mods/project?'+this.#params({source,projectId}),'GET',undefined,signal);}
  modFiles(source:ModSource,projectId:string,query:ModFileQuery={},signal?:AbortSignal){return this.#request<ModFilesPage>('/mods/files?'+this.#params({source,projectId,...query}),'GET',undefined,signal);}
  downloadMod(ref:ModFileRef,gameVersion:string,loader:string,signal?:AbortSignal){return this.#request<{task:TaskSnapshot}>('/mods/downloads','POST',{ref,gameVersion,loader},signal);}
  instanceMods(instanceId:string,signal?:AbortSignal){return this.#request<import('./local-types').InstanceModListing>('/mods/instance?'+this.#params({instanceId}),'GET',undefined,signal);}
  planInstanceMods(instanceId:string,operation:'INSTALL'|'REMOVE'|'FABRIC_API',ref?:ModFileRef,signal?:AbortSignal){return this.#request<import('./local-types').InstanceModPlan>('/mods/instance/plan','POST',{instanceId,operation,...(ref?{ref}:{})},signal);}
  applyInstanceMods(planId:string,signal?:AbortSignal){return this.#request<{task:TaskSnapshot}>('/mods/instance/apply','POST',{planId},signal);}
  modLibrary(signal?:AbortSignal){return this.#request<{entries:ModLibraryEntry[];problems:RecordProblem[]}>('/mods/library','GET',undefined,signal);}
  verifyMod(id:string,signal?:AbortSignal){return this.#request<{task:TaskSnapshot}>('/mods/library/'+encodeURIComponent(id)+'/verify','POST',{},signal);}
  runtimes(signal?:AbortSignal){return this.#request<RuntimeListing>('/runtimes','GET',undefined,signal);}
  discoverRuntimes(signal?:AbortSignal){return this.#request<{discovery:RuntimeDiscovery}>('/runtimes/discover','POST',{},signal);}
  runtimeDiscovery(id:string,signal?:AbortSignal){return this.#request<{discovery:RuntimeDiscovery}>('/runtimes/discover/'+encodeURIComponent(id),'GET',undefined,signal);}
  cancelRuntimeDiscovery(id:string,signal?:AbortSignal){return this.#request<{discovery:RuntimeDiscovery}>('/runtimes/discover/'+encodeURIComponent(id)+'/cancel','POST',{},signal);}
  registerJavaHome(home:string,signal?:AbortSignal){return this.#request<RuntimeListing>('/runtimes','POST',{home},signal);}
  identities(signal?:AbortSignal){return this.#request<IdentityListing>('/identities','GET',undefined,signal);}
  createIdentity(name:string,signal?:AbortSignal){return this.#request<IdentityListing>('/identities','POST',{name},signal);}
  selectIdentity(id:string,signal?:AbortSignal){return this.#request<IdentityListing>('/identities/'+encodeURIComponent(id)+'/select','POST',{},signal);}
  removeIdentity(id:string,signal?:AbortSignal){return this.#request<IdentityListing>('/identities/'+encodeURIComponent(id)+'/remove','POST',{},signal);}
  launchSettings(instanceId:string,signal?:AbortSignal){return this.#request<VersionedLaunchSettings>('/instances/'+encodeURIComponent(instanceId)+'/launch-settings','GET',undefined,signal);}
  saveLaunchSettings(instanceId:string,revision:string,settings:LaunchSettings,signal?:AbortSignal){return this.#request<VersionedLaunchSettings>('/instances/'+encodeURIComponent(instanceId)+'/launch-settings','POST',{revision,settings},signal);}
  checkLaunch(instanceId:string,identityId:string,signal?:AbortSignal,server?:{id:string;revision:string}){return this.#request<{check:LaunchCheck}>('/launch-checks','POST',{instanceId,identityId,...(server?{serverId:server.id,serverRevision:server.revision}:{})},signal);}
  launchCheck(id:string,signal?:AbortSignal){return this.#request<{check:LaunchCheck}>('/launch-checks/'+encodeURIComponent(id),'GET',undefined,signal);}
  cancelLaunchCheck(id:string,signal?:AbortSignal){return this.#request<{check:LaunchCheck}>('/launch-checks/'+encodeURIComponent(id)+'/cancel','POST',{},signal);}
  startLaunch(checkId:string,signal?:AbortSignal){return this.#request<{launch:LaunchSnapshot}>('/launches','POST',{checkId},signal);}
  launches(instanceId:string,signal?:AbortSignal){return this.#request<{launches:LaunchSnapshot[]}>('/launches?'+new URLSearchParams({instanceId}),'GET',undefined,signal);}
  launchLogs(id:string,cursor?:string,limit=500,signal?:AbortSignal){return this.#request<LaunchLogPage>('/launches/'+encodeURIComponent(id)+'/logs?'+this.#params({cursor,limit}),'GET',undefined,signal);}
  stopLaunch(id:string,confirmed:boolean,signal?:AbortSignal){return this.#request<{launch:LaunchSnapshot}>('/launches/'+encodeURIComponent(id)+'/stop','POST',{confirmed},signal);}
}
export function operationMessage(error:unknown){return error instanceof Error?error.message:'操作未完成，请稍后重试';}
