export type MaidState='welcome'|'guide'|'waiting'|'success'|'attention';
export const MAID_STATES:Record<MaidState,{index:number;label:string}>={welcome:{index:0,label:'欢迎回来'},guide:{index:1,label:'功能指引'},waiting:{index:2,label:'请稍候'},success:{index:3,label:'这一步完成了'},attention:{index:4,label:'需要留意'}};
export const MAID_AVATAR='/images/maid/program-avatar-transparent.png';
export const MAID_SPRITES='/images/maid/states.png';
