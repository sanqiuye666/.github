import type {AnimationAsset,AnimationRepository} from './media';
class IndexedAnimationRepository implements AnimationRepository {
 private async database():Promise<IDBDatabase>{
  return new Promise((resolve,reject)=>{
   if(typeof indexedDB==='undefined'){reject(Error('当前窗口无法使用本机动画存储。'));return;}
   const request=indexedDB.open('sqy-launcher-intro-v1',1);let completed=false;
   const failed=(message:string)=>{if(completed)return;completed=true;clearTimeout(timer);reject(Error(message));};
   const timer=setTimeout(()=>failed('本机动画存储读取超时，请稍后重试。'),2000);
   request.onupgradeneeded=()=>{if(completed){request.transaction?.abort();return;}request.result.createObjectStore('animations');};
   request.onerror=()=>failed('无法打开本机动画存储，原动画保留。');
   request.onblocked=()=>failed('动画存储被其他窗口占用，请稍后重试。');
   request.onsuccess=()=>{clearTimeout(timer);if(completed){request.result.close();return;}completed=true;resolve(request.result);};
  });
 }
 private async run<T>(mode:IDBTransactionMode,work:(store:IDBObjectStore)=>IDBRequest<T>):Promise<T>{const db=await this.database();try{return await new Promise<T>((resolve,reject)=>{const tx=db.transaction('animations',mode),request=work(tx.objectStore('animations'));tx.oncomplete=()=>resolve(request.result);tx.onabort=()=>reject(Error('动画保存失败，原设置保留。'));tx.onerror=()=>reject(Error('动画保存失败，原设置保留。'));});}finally{db.close();}}
 async read(){const value=await this.run<AnimationAsset|undefined>('readonly',s=>s.get('active'));if(!value)return null;if(!(value.blob instanceof Blob)||value.blob.size>30*1024*1024||!['image/gif','video/mp4','video/webm'].includes(value.type)||typeof value.name!=='string')throw Error('本机动画记录损坏，请恢复默认。');return value;}
 async write(asset:AnimationAsset){await this.run('readwrite',s=>s.put(asset,'active'));}
 async clear(){await this.run('readwrite',s=>s.delete('active'));}
}
export const animationRepository=new IndexedAnimationRepository();
