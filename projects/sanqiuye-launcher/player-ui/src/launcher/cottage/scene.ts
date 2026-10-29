export type CottagePhase='loading'|'walking'|'entered';
const clamp=(n:number)=>Math.max(0,Math.min(1,n));
export function cottageFrame(seconds:number,readyAt:number|null,reduced:boolean){
 const elapsed=readyAt===null?0:Math.max(0,seconds-readyAt),progress=reduced&&readyAt!==null?1:clamp((elapsed-.45)/3.6);
 return {phase:(readyAt===null?'loading':progress>=1?'entered':'walking') as CottagePhase,
  emitLeaves:readyAt===null&&!reduced,leafTime:reduced?0:Math.min(seconds,readyAt??seconds),
  leafOpacity:reduced?.45:readyAt===null?1:1-clamp(elapsed/1.2),
  characterX:190+progress*273,characterY:420-progress*3,characterScale:1-progress*.17,
  characterOpacity:readyAt===null||reduced?0:clamp(elapsed/.35)*(1-clamp((progress-.85)/.15)),
  walk:readyAt!==null&&progress<1&&!reduced,cameraScale:reduced?1:1+Math.min(seconds,12)*.002};
}
export function mapleLeaf(index:number,seconds:number){
 const seed=((index*73+29)%997)/997,duration=8+seed*5,t=(seconds+(index%7)*1.17)%duration,progress=t/duration;
 return {x:990-progress*1170+(seed-.5)*180,y:-80+progress*660+Math.sin(t*.9+index)*32,
  angle:index*47+t*(18+seed*30),scale:.26+seed*.45,flip:.35+.65*Math.abs(Math.cos(t*.8+index))};
}
