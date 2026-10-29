import type {LocalSession} from './local-types';

function failure(reason:unknown):Error {
  return reason instanceof Error?reason:new Error(typeof reason==='string'?reason:'本机核心未能连接，请重新尝试');
}
export async function nativeSession(signal?:AbortSignal):Promise<LocalSession> {
  if(signal?.aborted)throw new DOMException('操作已取消','AbortError');
  try {
    const {invoke}=await import('@tauri-apps/api/core');
    return await invoke<LocalSession>('launcher_local_session');
  } catch(reason) {throw failure(reason);}
}
export async function nativeRequest(method:'GET'|'POST',path:string,body:unknown,signal:AbortSignal):Promise<Response> {
  if(signal.aborted)throw new DOMException('操作已取消','AbortError');
  const {invoke}=await import('@tauri-apps/api/core');
  if(signal.aborted)throw new DOMException('操作已取消','AbortError');
  let abort:()=>void=()=>{};
  const cancelled=new Promise<never>((_,reject)=>{abort=()=>reject(new DOMException('操作已取消','AbortError'));signal.addEventListener('abort',abort,{once:true});});
  try {
    const result=await Promise.race([invoke<{status:number;body:unknown}>('launcher_local_request',{method,path,body:body??null}),cancelled]);
    if(!Number.isInteger(result.status)||result.status<200||result.status>599)throw new Error('本机核心返回了无效状态');
    return new Response(JSON.stringify(result.body),{status:result.status,headers:{'Content-Type':'application/json'}});
  } catch(reason) {throw failure(reason);}
  finally {signal.removeEventListener('abort',abort);}
}
