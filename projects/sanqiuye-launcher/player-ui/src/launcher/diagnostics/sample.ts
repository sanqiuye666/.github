import type {LocalLauncherClient} from '../local-api';
export interface LogSample {lines:string[];partial:boolean;readError:boolean}
export const SAMPLE_CHARACTERS=128*1024;
export async function sampleLaunchLogs(client:Pick<LocalLauncherClient,'launchLogs'>,id:string,signal?:AbortSignal):Promise<LogSample>{
 const result:LogSample={lines:[],partial:false,readError:false};let cursor:string|undefined,characters=0;
 for(let page=0;page<4;page++){
  signal?.throwIfAborted();
  try{
   const value=await client.launchLogs(id,cursor,100,signal);signal?.throwIfAborted();
   if(!Array.isArray(value.lines)||value.lines.some(line=>typeof line!=='string')||typeof value.nextCursor!=='string')throw Error('日志响应无效');
   result.partial ||= value.truncated||value.lines.length>100;
   for(const line of value.lines.slice(0,100)){
    const remaining=SAMPLE_CHARACTERS-characters;
    if(line.length>remaining){if(remaining)result.lines.push(line.slice(0,remaining));result.partial=true;return result;}
    result.lines.push(line);characters+=line.length;
   }
   if(value.lines.length<100)return result;
   if(value.nextCursor===cursor){result.partial=true;return result;}
   cursor=value.nextCursor;
  }catch(error){signal?.throwIfAborted();result.partial=true;result.readError=true;return result;}
 }
 result.partial=true;return result;
}
