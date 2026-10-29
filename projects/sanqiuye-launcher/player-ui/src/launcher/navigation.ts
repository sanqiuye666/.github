import type {LauncherPage} from './model';
/** 导航分组只决定入口排列，不代表资源类型已实现。 */
export const PENDING_RESOURCE_IDS:readonly LauncherPage[]=['resourcepacks','shaders','recordings','schematics','bridge','servers'];
export const RESOURCE_PAGE_IDS:readonly LauncherPage[]=['mods','packs',...PENDING_RESOURCE_IDS];
