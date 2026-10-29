import {describe,it,expect} from 'vitest';
import {gradientText,formatGradient} from '../tools/color-text';
import {SkinPixels,checkSkinPng} from '../tools/skin-pixels';
describe('彩字输出的兼容和注入边界',()=>{
 it('按Unicode码点渐变，空格和端点保持',()=>{const a=gradientText('秋 🍁','#000000','#ffffff');expect(a.map(x=>x.text)).toEqual(['秋',' ','🍁']);expect(a.map(x=>x.color)).toEqual(['#000000','#808080','#ffffff']);expect(gradientText('叶','#123456','#ffffff')[0]?.color).toBe('#123456');});
 it('原版tellraw JSON转义，不把输入当命令',()=>{const text='"\\<red>三秋叶';const cmd=formatGradient(text,'#112233','#445566','tellraw');const components=JSON.parse(cmd.slice('/tellraw @s '.length));expect(components.map((x:{text:string})=>x.text).join('')).toBe(text);expect(components[0].color).toBe('#112233');});
 it('MiniMessage文本转义，传统模式明确16色',()=>{expect(formatGradient('<red>\\x','#ffffff','#ffffff','minimessage')).toContain('\\<');expect(formatGradient('秋叶','#ff5555','#ff5555','legacy')).toBe('&c秋&c叶&r');});
 it('拒绝控制符、超限和无效颜色',()=>{for(const text of ['a\nb','\u0000','\u0085','\u009f','x'.repeat(513)])expect(()=>gradientText(text,'#ffffff','#000000')).toThrow();expect(()=>gradientText('a','red','#000000')).toThrow();expect(gradientText('','#ffffff','#000000')).toEqual([]);});
});
describe('皮肤像素编辑不覆盖原件',()=>{
 it('笔画插值连续，同一笔只撤销一次；新笔清除重做',()=>{const skin=new SkinPixels();skin.beginStroke();skin.line(0,0,3,0,'#ff0000');skin.endStroke();expect([...skin.pixels.slice(0,16)]).toEqual(Array(4).fill([255,0,0,255]).flat());skin.undo();expect(skin.pixels.some(Boolean)).toBe(false);skin.redo();expect(skin.pixels[0]).toBe(255);skin.beginStroke();skin.paint(4,0,'#00ff00');skin.endStroke();expect(skin.canRedo).toBe(false);});
 it('擦除与取色、导入尺寸/字节校验，坏导入保留旧像素',()=>{const skin=new SkinPixels();skin.beginStroke();skin.paint(1,1,'#123456');skin.endStroke();expect(skin.pick(1,1)).toBe('#123456');const before=skin.pixels.slice();expect(()=>skin.load(new Uint8ClampedArray(3),64,64)).toThrow();expect(skin.pixels).toEqual(before);skin.beginStroke();skin.paint(1,1,null);skin.endStroke();expect(skin.pixels[(1+64)*4+3]).toBe(0);});
 it('PNG头和IHDR限定尺寸及大小',()=>{const b=new Uint8Array(33);b.set([137,80,78,71,13,10,26,10]);new DataView(b.buffer).setUint32(8,13);b.set([73,72,68,82],12);new DataView(b.buffer).setUint32(16,64);new DataView(b.buffer).setUint32(20,64);expect(checkSkinPng(b,33)).toEqual({width:64,height:64});new DataView(b.buffer).setUint32(20,32);expect(checkSkinPng(b,33).height).toBe(32);new DataView(b.buffer).setUint32(16,512);expect(()=>checkSkinPng(b,33)).toThrow();expect(()=>checkSkinPng(b,5*1024*1024)).toThrow();});
});
