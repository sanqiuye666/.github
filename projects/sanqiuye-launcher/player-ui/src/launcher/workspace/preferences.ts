import {defaultPreferences,validatePreferences,type WorkspacePreferences} from './layout';
const KEY='sqy.launcher.workspace.v1';
export function loadPreferences(storage:Storage):{value:WorkspacePreferences;warning:string|null}{
 let raw:string|null=null;
 try {raw=storage.getItem(KEY);if(raw===null)return {value:defaultPreferences(),warning:null};
  if(new TextEncoder().encode(raw).length>65536)throw new Error();
  return {value:validatePreferences(JSON.parse(raw)),warning:null};
 }catch{if(raw!==null)try{storage.setItem('sqy.launcher.workspace.invalid',raw.slice(0,16000));}catch{/* 保留当前页面可用。 */}
  return {value:defaultPreferences(),warning:'工作台配置无法读取，已恢复默认；可在设置重新保存。'};}
}
export function savePreferences(storage:Storage,value:WorkspacePreferences):void{
 const valid=validatePreferences(value);
 const {theme,...legacy}=valid;
 try{storage.setItem(KEY,JSON.stringify(theme==='forest'?legacy:valid));}catch{throw new Error('工作台未保存：本机存储不可用或空间不足。');}
}
