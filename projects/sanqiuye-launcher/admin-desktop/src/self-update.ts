export interface DesktopUpdate {version:string;notes:string;installable:boolean}

/** 配置状态来自原生核心；未配置不能冒充最新版或发出联网检查。 */
export async function queryDesktopUpdate(configured:boolean,query:()=>Promise<DesktopUpdate|null>):Promise<DesktopUpdate|null>{
  if(!configured)throw new Error('三秋叶自更新服务尚未配置，当前请使用已验证的完整便携包。');
  return query();
}
