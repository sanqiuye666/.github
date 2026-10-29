// 原生验收统一等待退出IPC与进程结束，不持有业务状态。
export async function waitForNativeExit(requestExit,exited,timeoutMs,onTimeout=()=>{}){
 let timer;
 const deadline=new Promise((_,reject)=>{timer=setTimeout(()=>{
  let cause;try{onTimeout();}catch(error){cause=error;}
  reject(Error('管理端未正常退出',{cause}));
 },timeoutMs);});
 try{return await Promise.race([(async()=>{await requestExit();return await exited;})(),deadline]);}
 finally{clearTimeout(timer);}
}
