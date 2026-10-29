import {afterEach,expect,it} from 'vitest';
import {createApp,nextTick} from 'vue';
import ProjectImage from '../ProjectImage.vue';
import {projectImage,projectDescription,categoryLabel} from '../content-labels';
let app:ReturnType<typeof createApp>|undefined,root:HTMLDivElement;
afterEach(()=>{app?.unmount();root?.remove();});
it('外部图片只允许精确来源HTTPS主机，未知分类仍保留',()=>{
 for(const value of ['http://cdn.modrinth.com/x.png','https://cdn.modrinth.com.evil.invalid/x.png','https://user:secret@cdn.modrinth.com/x.png','file:///secret','https://127.0.0.1/a.png'])expect(projectImage(value)).toBe('');
 expect(categoryLabel('future-category')).toBe('future-category');expect(projectImage('https://cdn.modrinth.com/x.png')).toBe('https://cdn.modrinth.com/x.png');
});
it('图片实际失败显示回退，不继续挂坏图片',async()=>{
 root=document.createElement('div');app=createApp(ProjectImage,{url:'https://cdn.modrinth.com/x.png',title:'项目'});app.mount(root);root.querySelector('img')!.dispatchEvent(new Event('error'));await nextTick();expect(root.querySelector('img')).toBeNull();expect(root.textContent).toContain('图片暂不可用');
});
it('公开图片只作显示，不请求CORS像素读取权限，仍关闭Referer',()=>{
 root=document.createElement('div');app=createApp(ProjectImage,{url:'https://cdn.modrinth.com/x.png',title:'项目'});app.mount(root);const img=root.querySelector('img')!;expect(img.hasAttribute('crossorigin')).toBe(false);expect(img.getAttribute('referrerpolicy')).toBe('no-referrer');
});
it('简介不会执行HTML脚本、外部图片、表单或危险链接',()=>{
 const raw='# 简介\n\n**真实说明**\n\n<script>alert(1)</script>\n\n![秘密](http://127.0.0.1/private)\n\n[跳转](javascript:alert(1))\n\n<iframe src="https://evil.invalid"></iframe>';
 const html=projectDescription(raw);expect(html).toContain('真实说明');for(const bad of ['<script','<iframe','<img','href=','onerror='])expect(html).not.toContain(bad);
});
