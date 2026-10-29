import type {CapabilityState} from './model';
export type {JavaRuntime,RuntimeListing,RuntimeDiscovery,OfflineIdentity,IdentityListing,LaunchSettings,VersionedLaunchSettings,LaunchCheck,LaunchState,LaunchSnapshot,LaunchLogPage} from './launch-types';

export interface LocalSession {schemaVersion:1;mode:'LOCAL';sessionToken:string;capabilities:Record<string,CapabilityState>}
export type OfficialType='release'|'snapshot'|'old_alpha'|'old_beta';
export interface OfficialVersion {id:string;type:OfficialType;releaseTime:string}
export interface CatalogQuery {q?:string;type?:OfficialType|'';offset?:number;limit?:number;snapshotId?:string}
export interface CatalogPage {snapshotId:string;total:number;filteredTotal:number;typeCounts:Record<OfficialType,number>;offset:number;limit:number;items:OfficialVersion[];source:'NETWORK'|'CACHE'|'STALE_CACHE'|'MOCK';fetchedAt:string;warning:string|null}
export interface GameInstance {id:string;name:string;gameVersion:string;loader:string;createdAt:string;state:'PREPARED';installation:InstallationSummary|null;installationProblems?:{installationId:string;reason:string}[]}
export type LoaderKind='VANILLA'|'FABRIC'|'QUILT'|'FORGE'|'NEOFORGE';
export interface InstallationSummary {fabricVersion?:string|null;loader?:LoaderKind;loaderVersion?:string|null;installationId:string;versionId:string;installedAt:string;fileCount:number;requiredJavaMajor:number|null;platform:GamePlatform}
export interface GamePlatform {osName:string;osVersion?:string;arch:string;bits:number}
export interface InstallationPlan {loader?:LoaderKind;loaderVersion?:string|null;fabricVersion?:string|null;planId:string;instanceId:string;versionId:string;platform:GamePlatform;requiredJavaMajor:number|null;fileCount:number;totalBytes:number;expiresAt:string}
export type TaskState='QUEUED'|'RESOLVING'|'DOWNLOADING'|'VERIFYING'|'COMPLETED'|'FAILED'|'CANCELLED'|'INTERRUPTED';
export interface TaskSnapshot {id:string;kind:'MOD_INSTALL'|'MOD_REMOVE'|'LOADER_INSTALL'|'FABRIC_INSTALL'|'VANILLA_INSTALL'|'MOD_DOWNLOAD'|'INSTALLATION_VERIFY'|'MOD_VERIFY';state:TaskState;inputs:Record<string,string>;createdAt:string;updatedAt:string;downloadedBytes:number;totalBytes:number|null;completedFiles:number;totalFiles:number|null;errorCode:string|null;message:string|null;retryOf:string|null}
export interface TaskPage {tasks:TaskSnapshot[];problems:{recordId:string;reason:string}[];total:number;offset:number;limit:number}
export interface InstanceListing {instances:GameInstance[];problems:{directoryId:string;reason:string}[]}
export interface CreateInstance {name:string;versionId:string;snapshotId:string}
export type ModSource='MODRINTH'|'CURSEFORGE';
export interface SourceStatus {source:ModSource;state:'AVAILABLE'|'UNCONFIGURED'|'UNAVAILABLE';reason:string|null;loaders:string[];warning:string|null}
export interface ModProject {source:ModSource;projectId:string;title:string|null;author:string|null;summary:string|null;downloads:number|null;presentation?:import('./content-types').ProjectPresentation}
export interface ModPage {items:ModProject[];offset:number;limit:number;total:number;accessibleTotal:number;platformLimited:boolean}
export interface ModSearchParams {source:ModSource;category?:string;q?:string;gameVersion?:string;loader?:string;sort?:'RELEVANCE'|'DOWNLOADS'|'UPDATED';offset?:number;limit?:number}
export interface ModFileRef {source:ModSource;projectId:string;versionId:string;fileKey:string}
export interface FileDigest {algorithm:'SHA1'|'SHA256'|'SHA512';hex:string}
export interface ModDependency {kind:'REQUIRED'|'OPTIONAL'|'INCOMPATIBLE'|'EMBEDDED'|'TOOL'|'INCLUDE'|'UNKNOWN';projectId:string|null;versionId:string|null;fileName:string|null}
export interface ModFile {ref:ModFileRef;displayName:string|null;fileName:string;gameVersions:string[];loaders:string[];releaseChannel:string|null;publishedAt:string|null;size:number|null;sourceDigest:FileDigest|null;dependencies:ModDependency[];downloadable:boolean;blockedReason:string|null}
export interface ModFileQuery {gameVersion?:string;loader?:string;offset?:number;limit?:number}
export interface ModFilesPage {items:ModFile[];offset:number;limit:number;total:number;accessibleTotal:number;platformLimited:boolean}
export interface RecordProblem {recordId:string;message:string}
export interface ModLibraryEntry {id:string;taskId:string;source:ModSource;projectId:string;versionId:string;fileKey:string;title:string|null;fileName:string;gameVersions:string[];loaders:string[];sourceDigest:FileDigest;size:number;sha256:string;createdAt:string;dependencies:ModDependency[];filePath:string}

export interface InstanceModEntry {source:ModSource;projectId:string;versionId:string;fileKey:string;displayName:string|null;fileName:string;size:number;sha256:string;dependencies:ModDependency[]}
export interface InstanceModListing {instanceId:string;gameVersion:string;loader:string;managedLoader:string;entries:InstanceModEntry[];manualFiles:number;directory:string;fingerprint:string;compatible:boolean}
export interface InstanceModPlan {planId:string;instanceId:string;gameVersion:string;loader:string;removal:boolean;files:{source:string;projectId:string;versionId:string;fileName:string;size:number;digest:string}[];totalBytes:number;warnings:string[];expiresAt:string}
