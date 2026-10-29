import {describe,expect,it} from 'vitest';
import {cottageFrame,mapleLeaf} from '../cottage/scene';
describe('独立小屋演示时序',()=>{
 it('任意加载时长持续发叶，不擅自完成或显示账号小人',()=>{const f=cottageFrame(90,null,false);expect(f.phase).toBe('loading');expect(f.emitLeaves).toBe(true);expect(f.characterOpacity).toBe(0);});
 it('模拟完成立即停止叶片时间，之后行走并进入门内',()=>{const f=cottageFrame(7,6,false),later=cottageFrame(12,6,false);expect(f.emitLeaves).toBe(false);expect(f.leafTime).toBe(6);expect(f.phase).toBe('walking');expect(f.characterX).toBeGreaterThan(190);expect(later.phase).toBe('entered');expect(later.characterOpacity).toBe(0);expect(later.leafTime).toBe(6);});
 it('减少动态时加载保持静止，完成直接结束，不强迫观看动画',()=>{const a=cottageFrame(10,null,true),b=cottageFrame(10,10,true);expect(a.leafTime).toBe(0);expect(a.emitLeaves).toBe(false);expect(b.phase).toBe('entered');expect(b.characterOpacity).toBe(0);});
 it('叶片姿态可确定重播，沿右上到左下轨迹且不产生无效坐标',()=>{expect(mapleLeaf(4,1)).toEqual(mapleLeaf(4,1));const a=mapleLeaf(0,0),b=mapleLeaf(0,2);expect(b.x).toBeLessThan(a.x);expect(b.y).toBeGreaterThan(a.y);expect(Object.values(b).every(Number.isFinite)).toBe(true);});
});
