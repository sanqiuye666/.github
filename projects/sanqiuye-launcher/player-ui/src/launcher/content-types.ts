export interface ContentCategory {id:string;name:string;group:string}
export interface ProjectPresentation {iconUrl:string|null;description:string;categories:ContentCategory[];gallery:string[];gameVersions:string[];loaders:string[];publishedAt?:string|null;updatedAt?:string|null;clientSide?:string|null;serverSide?:string|null;links:Record<string,string>}
