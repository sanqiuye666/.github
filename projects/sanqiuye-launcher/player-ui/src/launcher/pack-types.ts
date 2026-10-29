export type PackSource='MODRINTH'|'FTB'|'ATLAUNCHER'|'CURSEFORGE'|'TECHNIC'|'BBSMC';
export interface PackSourceStatus {source:PackSource;name:string;state:'AVAILABLE'|'PENDING';reason:string}
export interface PackProject {source:PackSource;id:string;name:string;summary:string;presentation?:import('./content-types').ProjectPresentation;gameVersions:string[];loaders:string[]}
export interface PackVersion {id:string;name:string;gameVersions:string[];loaders:string[];publishedAt:string|null}
export interface PackOrigin {provenance:'NETWORK'|'CACHE'|'MOCK';fetchedAt:string;warning:string|null}
export interface PackPage extends PackOrigin {items:PackProject[];offset:number;limit:number;total:number}
export interface PackDetails extends PackOrigin {project:PackProject;versions:PackVersion[]}
