const BYTES=64*64*4;
export function checkSkinPng(bytes:Uint8Array,fileSize:number){
 if(fileSize>4*1024*1024||fileSize<33||bytes.length<33||[137,80,78,71,13,10,26,10].some((v,i)=>bytes[i]!==v))throw Error('请选择不超过4MB的PNG皮肤');
 const v=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength),width=v.getUint32(16),height=v.getUint32(20);
 if(v.getUint32(8)!==13||String.fromCharCode(...bytes.slice(12,16))!=='IHDR'||width!==64||![32,64].includes(height))throw Error('当前支持64×64或64×32皮肤PNG');
 return {width,height};
}
export class SkinPixels {
 pixels=new Uint8ClampedArray(BYTES);private history:Uint8ClampedArray<ArrayBuffer>[]=[];private future:Uint8ClampedArray<ArrayBuffer>[]=[];private before:Uint8ClampedArray<ArrayBuffer>|null=null;
 get canUndo(){return this.history.length>0;}get canRedo(){return this.future.length>0;}
 beginStroke(){if(!this.before)this.before=this.pixels.slice();}
 endStroke(){if(!this.before)return;const before=this.before;this.before=null;if(before.some((v,i)=>v!==this.pixels[i])){this.history.push(before);if(this.history.length>32)this.history.shift();this.future=[];}}
 load(pixels:Uint8ClampedArray,width:number,height:number){if(width!==64||height!==64||pixels.length!==BYTES)throw Error('编辑区只接受64×64像素；旧皮肤保持原件可预览和导出');this.pixels=new Uint8ClampedArray(pixels);this.history=[];this.future=[];this.before=null;}
 private at(x:number,y:number){if(!Number.isInteger(x)||!Number.isInteger(y)||x<0||y<0||x>=64||y>=64)throw Error('像素坐标超出范围');return (x+y*64)*4;}
 paint(x:number,y:number,color:string|null){const at=this.at(x,y);if(color!==null&&!/^#[\da-f]{6}$/i.test(color))throw Error('像素颜色无效');const rgb=color?[1,3,5].map(i=>parseInt(color.slice(i,i+2),16)):[0,0,0];this.pixels.set([...rgb,color===null?0:255],at);}
 pick(x:number,y:number){const at=this.at(x,y);return '#'+[0,1,2].map(i=>this.pixels[at+i]!.toString(16).padStart(2,'0')).join('');}
 line(x0:number,y0:number,x1:number,y1:number,color:string|null){this.at(x0,y0);this.at(x1,y1);const dx=Math.abs(x1-x0),sx=x0<x1?1:-1,dy=-Math.abs(y1-y0),sy=y0<y1?1:-1;let e=dx+dy;for(;;){this.paint(x0,y0,color);if(x0===x1&&y0===y1)break;const twice=2*e;if(twice>=dy){e+=dy;x0+=sx;}if(twice<=dx){e+=dx;y0+=sy;}}}
 undo(){this.endStroke();const p=this.history.pop();if(p){this.future.push(this.pixels.slice());this.pixels=p;}}
 redo(){this.endStroke();const p=this.future.pop();if(p){this.history.push(this.pixels.slice());this.pixels=p;}}
}
