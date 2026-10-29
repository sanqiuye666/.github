import {expect,it} from 'vitest';
import {prepareAnimation,AnimationLibrary,type AnimationRepository} from '../intro/media';
const gif=()=>new File([new Uint8Array([71,73,70,56,57,97,1,0,1,0,128,0,0])],'autumn.gif',{type:'image/gif'});
it('按真实头部识别动画并限制格式和容量',async()=>{
 expect((await prepareAnimation(gif())).type).toBe('image/gif');
 await expect(prepareAnimation(new File(['<svg/>'],'bad.gif',{type:'image/gif'}))).rejects.toThrow();
 await expect(prepareAnimation(new File(['GIF89a'],'run.html',{type:'text/html'}))).rejects.toThrow();
 await expect(prepareAnimation(new File([new Uint8Array(30*1024*1024+1)],'large.gif'))).rejects.toThrow();
 expect((await prepareAnimation(new File([new Uint8Array([0,0,0,16,102,116,121,112,105,115,111,109])],'clip.mp4'))).type).toBe('video/mp4');
 expect((await prepareAnimation(new File([new Uint8Array([26,69,223,163,0,0,0,0])],'clip.webm'))).type).toBe('video/webm');
});
it('损坏或保存失败保留旧动画，恢复默认才清除',async()=>{
 let current=await prepareAnimation(gif());let fail=false;const store:AnimationRepository={read:async()=>current,write:async value=>{if(fail)throw Error('磁盘不可写');current=value;},clear:async()=>{current=null as any;}};const lib=new AnimationLibrary(store);
 const old=current;await expect(lib.import(new File(['bad'],'bad.gif'))).rejects.toThrow();expect(await lib.read()).toBe(old);
 fail=true;await expect(lib.import(gif())).rejects.toThrow('磁盘不可写');expect(await lib.read()).toBe(old);
 fail=false;await lib.restoreDefault();expect(await lib.read()).toBeNull();
});
