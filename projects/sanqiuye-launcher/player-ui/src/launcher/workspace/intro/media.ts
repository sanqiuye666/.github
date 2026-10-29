export interface AnimationAsset {name:string;type:'image/gif'|'video/mp4'|'video/webm';blob:Blob;updatedAt:number}
export interface AnimationRepository {read():Promise<AnimationAsset|null>;write(value:AnimationAsset):Promise<void>;clear():Promise<void>}
export async function prepareAnimation(file:File):Promise<AnimationAsset>{
 if(!file.size||file.size>30*1024*1024)throw Error('动画须大于0且不超过30MiB。');
 const extension=file.name.split('.').at(-1)?.toLowerCase(),bytes=new Uint8Array(await file.slice(0,16).arrayBuffer());let type:AnimationAsset['type'];
 if(extension==='gif'&&bytes.length>=13&&String.fromCharCode(...bytes.slice(0,6)).match(/^GIF8[79]a$/))type='image/gif';
 else if(extension==='mp4'&&bytes.length>=12&&String.fromCharCode(...bytes.slice(4,8))==='ftyp')type='video/mp4';
 else if(extension==='webm'&&bytes.length>=8&&bytes[0]===26&&bytes[1]===69&&bytes[2]===223&&bytes[3]===163)type='video/webm';
 else throw Error('请选择有效的GIF、MP4或WebM动画，不支持网页和脚本。');
 return {name:file.name.slice(0,180),type,blob:new Blob([file],{type}),updatedAt:Date.now()};
}
export class AnimationLibrary {
 constructor(private readonly repository:AnimationRepository){}
 read(){return this.repository.read();}
 async import(file:File){const asset=await prepareAnimation(file);await this.repository.write(asset);return asset;}
 restoreDefault(){return this.repository.clear();}
}
/** 浏览器先解码候选，失败时不保存、不覆盖已有动画。 */
export async function decodeAnimation(asset:AnimationAsset):Promise<void>{
 const url=URL.createObjectURL(asset.blob),element=asset.type==='image/gif'?new Image():document.createElement('video');
 try{await new Promise<void>((resolve,reject)=>{
  const timer=setTimeout(()=>{cleanup();reject(Error('动画解码超时，请换一个文件。'));},10000);
  const cleanup=()=>{clearTimeout(timer);element.onload=null;element.onerror=null;if(element instanceof HTMLVideoElement)element.onloadedmetadata=null;};
  const loaded=()=>{const width=element instanceof HTMLVideoElement?element.videoWidth:element.naturalWidth,height=element instanceof HTMLVideoElement?element.videoHeight:element.naturalHeight;cleanup();if(width<1||height<1||width>8192||height>8192)reject(Error('动画尺寸无效或超过8192像素。'));else resolve();};
  element.onerror=()=>{cleanup();reject(Error('此动画损坏或当前系统不支持它的编码，请更换文件。'));};
  if(element instanceof HTMLVideoElement){element.preload='metadata';element.muted=true;element.onloadedmetadata=loaded;}else element.onload=loaded;element.src=url;
 });}finally{element.removeAttribute('src');if(element instanceof HTMLVideoElement)element.load();URL.revokeObjectURL(url);}
}
