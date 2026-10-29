import assert from 'node:assert/strict';

/** 通过真实原生IPC和官方来源验证分类/图片；不替换任何服务响应。 */
export async function checkNativeContent({app,rpc,screenshot,helper,write,checks}){
 const page=app.page;
 helper(['--pid',String(app.child.pid),'--started-at',app.ownership.processStartedAt,'--title','三秋叶启动器','--action','resize','--width','1600','--height','1000']);
 await page.locator('[data-launcher-page="mods"]').click();
 const categories=page.locator('[data-content-category="utility"]');await categories.waitFor({timeout:45000});
 const modTags=await rpc(app,'/mods/categories?source=MODRINTH');assert.ok(modTags.categories.length>=19);assert.ok(!modTags.categories.some(c=>c.id==='kitchen-sink'));write('mod-categories.json',modTags);
 await page.locator('.content-category-search input').fill('实用');await categories.waitFor();await categories.click();await page.locator('[data-mod-query]').fill('Mod Menu');await page.locator('[data-search-mods]:not([disabled])').click();
 const card=page.locator('[data-mod-project="mOgUt4GM"]');await card.waitFor({timeout:45000});
 await page.waitForFunction(()=>{const i=document.querySelector('[data-mod-project="mOgUt4GM"] img');return i?.complete&&i.naturalWidth>0;},null,{timeout:30000});
 assert.ok((await card.innerText()).includes('实用工具'));await page.locator('.launcher-content').focus();await page.keyboard.press('Control+Home');await screenshot(app,'mod-list-light');
 const sent=app.sent.findLast(c=>c.path.startsWith('/mods/search?'));assert.equal(new URL(sent.path,'http://localhost').searchParams.get('category'),'utility');checks.push('native-real-mod-category-filter-icon-summary');
 await card.click();const intro=page.locator('[data-project-introduction]');await intro.waitFor();
 await page.waitForFunction(()=>document.querySelector('[data-project-introduction] .content-markdown')?.textContent?.length>100,null,{timeout:30000});
 assert.ok((await intro.innerText()).includes('兼容性'));await page.locator('[data-mod-files]').waitFor();await screenshot(app,'mod-detail-light');checks.push('native-real-mod-introduction-and-files-preserved');
 await page.locator('[data-close-project]').click();await page.locator('[data-mod-source="CURSEFORGE"]').click();assert.ok((await page.locator('body').innerText()).includes('官方应用密钥'));assert.equal(await page.locator('[data-search-mods]').isDisabled(),true);checks.push('native-curseforge-unconfigured-explicit');
 await page.locator('[data-launcher-page="packs"]').click();await page.locator('[data-content-category="optimization"]').waitFor({timeout:45000});
 const packTags=await rpc(app,'/packs/categories?source=MODRINTH');assert.ok(packTags.categories.length>=10);write('pack-categories.json',packTags);
 await page.locator('[data-content-category="optimization"]').click();await page.locator('[data-pack-query]').fill('Fabulously Optimized');await page.locator('[data-pack-search]:not([disabled])').click();
 const pack=page.locator('[data-pack-project="1KVo5zza"]');await pack.waitFor({timeout:45000});await page.waitForFunction(()=>{const i=document.querySelector('[data-pack-project="1KVo5zza"] img');return i?.complete&&i.naturalWidth>0;},null,{timeout:30000});
 const packSent=app.sent.findLast(c=>c.path.startsWith('/packs/search?'));assert.equal(new URL(packSent.path,'http://localhost').searchParams.get('category'),'optimization');await page.locator('.launcher-content').focus();await page.keyboard.press('Control+Home');await screenshot(app,'pack-list-light');checks.push('native-real-pack-category-filter-icon-summary');
 await pack.click();await page.locator('[data-project-introduction]').waitFor({timeout:45000});
 const gallery=page.locator('[data-project-gallery] img');assert.ok(await gallery.count()>0);await gallery.first().scrollIntoViewIfNeeded();await gallery.first().evaluate(i=>i.loading='eager');
 await page.waitForFunction(()=>{const i=document.querySelector('[data-project-gallery] img');return i?.complete&&i.naturalWidth>0;},null,{timeout:30000});
 assert.ok((await page.locator('[data-project-introduction]').innerText()).includes('Performance'));assert.ok((await page.locator('[aria-label="整合包版本"]').innerText()).includes('安装待接入'));await screenshot(app,'pack-detail-light');checks.push('native-real-pack-gallery-introduction-version-boundary');
 await page.locator('[data-close-project]').click();await page.locator('[data-pack-query]').fill('');await page.locator('[data-pack-search]:not([disabled])').click();
 await page.locator('[data-pack-next]:not([disabled])').waitFor({timeout:45000});await page.locator('[data-pack-next]').click();await page.getByRole('button',{name:'上一页',exact:true}).waitFor();
 await page.waitForFunction(()=>!document.querySelector('[data-pack-search]').disabled,null,{timeout:45000});
 await page.locator('[data-pack-query]').fill('unsubmitted draft');await page.locator('[data-content-category="magic"]').click();await page.getByRole('button',{name:'上一页',exact:true}).click();
 await page.waitForFunction(()=>!document.querySelector('[data-pack-search]').disabled,null,{timeout:45000});
 const back=new URL(app.sent.findLast(c=>c.path.startsWith('/packs/search?')).path,'http://localhost');assert.equal(back.searchParams.get('offset'),'0');assert.equal(back.searchParams.get('category'),'optimization');assert.equal(back.searchParams.get('q'),null);checks.push('native-pagination-to-first-page-retains-submitted-filters');
 helper(['--pid',String(app.child.pid),'--started-at',app.ownership.processStartedAt,'--title','三秋叶启动器','--action','resize','--width','1000','--height','700']);

 await page.locator('[data-content-category="optimization"]').focus();await page.keyboard.press('Space');await screenshot(app,'pack-list-narrow');
 assert.equal(await page.locator('body').evaluate(e=>e.scrollWidth<=innerWidth),true);checks.push('native-narrow-keyboard-category-and-back');
 await page.locator('[data-pack-source]').selectOption('FTB');assert.ok((await page.locator('body').innerText()).includes('不支持完整分类筛选'));assert.equal(await page.locator('[data-content-category="optimization"]').count(),0);checks.push('native-unsupported-facets-clear-on-source-change');
 // 不让旧分类随新来源发送；真正读取一次FTB项目的图片和简介。
 const ftb=await rpc(app,'/packs/FTB/125');assert.ok(ftb.project.presentation.iconUrl.startsWith('https://cdn.feed-the-beast.com/'));assert.ok(ftb.project.presentation.description.length>20);write('ftb-presentation.json',ftb);checks.push('native-real-ftb-art-and-description');
 await page.locator('[data-launcher-page="home"]').click();await page.locator('[data-workspace-settings]').click();await page.locator('[data-workspace-theme]').selectOption('forest');await page.locator('[data-close-appearance]').click();
 await page.locator('[data-launcher-page="mods"]').click();await page.locator('[data-content-category="utility"]').waitFor({timeout:45000});await page.locator('[data-mod-query]').fill('Mod Menu');await page.locator('[data-search-mods]:not([disabled])').click();await page.locator('[data-mod-project="mOgUt4GM"]').waitFor({timeout:45000});await screenshot(app,'mod-list-forest-narrow');checks.push('native-forest-theme-content-layout');
 write('content-calls.json',app.calls);write('content-sent.json',app.sent);
}
