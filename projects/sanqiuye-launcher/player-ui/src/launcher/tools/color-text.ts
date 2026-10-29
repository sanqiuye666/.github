export type TextFormat='tellraw'|'minimessage'|'legacy';
const color=(v:string)=>{if(!/^#[\da-f]{6}$/i.test(v))throw Error('需要六位十六进制颜色');return [1,3,5].map(i=>parseInt(v.slice(i,i+2),16));};
export function gradientText(text:string,start:string,end:string){
 const a=color(start),b=color(end),chars=Array.from(text);if(chars.length>512||/[\u0000-\u001f\u007f-\u009f]/u.test(text)||chars.some(c=>c.length===1&&c.charCodeAt(0)>=0xd800&&c.charCodeAt(0)<=0xdfff))throw Error('最多512个字符，不能包含换行、控制字符或无效文字');
 return chars.map((text,i)=>({text,color:'#'+a.map((v,k)=>Math.round(v+(b[k]!-v)*(chars.length<2?0:i/(chars.length-1))).toString(16).padStart(2,'0')).join('')}));
}
const LEGACY=['000000','0000aa','00aa00','00aaaa','aa0000','aa00aa','ffaa00','aaaaaa','555555','5555ff','55ff55','55ffff','ff5555','ff55ff','ffff55','ffffff'];
export function formatGradient(text:string,start:string,end:string,format:TextFormat){
 const spans=gradientText(text,start,end);
 if(format==='tellraw')return '/tellraw @s '+JSON.stringify(spans.length?spans:[{text:''}]);
 if(format==='minimessage')return spans.map(s=>`<color:${s.color}>${s.text.replace(/\\/g,'\\\\').replace(/</g,'\\<')}</color>`).join('');
 if(format!=='legacy')throw Error('不支持的输出格式');
 return spans.map(s=>{const rgb=color(s.color);let best=0,d=Infinity;LEGACY.forEach((hex,i)=>{const n=color('#'+hex).reduce((sum,v,k)=>sum+(v-rgb[k]!)**2,0);if(n<d){d=n;best=i;}});return '&'+best.toString(16)+s.text;}).join('')+(spans.length?'&r':'');
}
