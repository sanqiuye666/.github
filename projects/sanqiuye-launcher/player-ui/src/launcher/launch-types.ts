export interface JavaRuntime {id:string;majorVersion:number;bits:number;vendor:string}
export interface RuntimeListing {runtimes:JavaRuntime[];diagnostics:{message:string}[];platformBits:number}
export interface RuntimeDiscovery extends RuntimeListing {id:string;state:'RUNNING'|'COMPLETED'|'FAILED'|'CANCELLED';createdAt:string;updatedAt:string;errorCode:string|null;message:string}
export interface OfflineIdentity {id:string;name:string;kind:'OFFLINE'}
export interface IdentityListing {identities:OfflineIdentity[];selectedId:string|null;temporary:true;message?:string}
export interface LaunchSettings {javaId:string|null;initialMemoryMiB:number;maximumMemoryMiB:number;width:number;height:number;extraJvmArgs:string[];acceptUnknownJava:boolean}
export interface VersionedLaunchSettings {revision:string;settings:LaunchSettings}
export interface LaunchCheck {id:string;instanceId:string;state:'CHECKING'|'READY'|'FAILED'|'CANCELLED'|'CONSUMED';createdAt:string;expiresAt:string;installationId:string|null;versionId:string|null;javaMajor:number|null;errorCode:string|null;message:string}
export type LaunchState='PREPARING'|'STARTING'|'RUNNING'|'STOPPING'|'EXITED'|'FAILED'|'CANCELLED'|'UNCERTAIN';
export interface LaunchSnapshot {id:string;instanceId:string;installationId:string;state:LaunchState;createdAt:string;startedAt:string|null;endedAt:string|null;exitCode:number|null;errorCode:string|null;message:string}
export interface LaunchLogPage {lines:string[];nextCursor:string;truncated:boolean}
