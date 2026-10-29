export type GameOptionValue=number|boolean|string;
export interface GameOptionField {key:string;label:string;category:string;type:'integer'|'boolean'|'choice';min:number;max:number;step?:number;defaultValue:GameOptionValue}
export interface GameOptionsSnapshot {revision:string;enabled:boolean;values:Record<string,GameOptionValue>;fields:GameOptionField[];supportedVersions:string[]}
