import assert from 'node:assert/strict';

/** 验证真实EXE的官方目录和资源导航；不替换IPC或来源响应。 */
export async function checkNativeVersionNavigation({app,rpc,preset,screenshot,helper,write,checks}){
 const {page}=app;
 const choose=async category=>{
  await page.locator(`[data-version-category="${category}"]`).click();
  const type=category==='legacy'?'old_beta':category;
  if(category!=='all')await page.locator(`[data-official-type="${type}"]`).first().waitFor({timeout:45000});
  await page.waitForFunction(()=>!document.querySelector('[data-refresh-catalog]')?.disabled);
 };
 await page.locator('[data-launcher-page="versions"]').click();
 await page.locator('[data-version-id]').first().waitFor({timeout:45000});
 assert.equal(await page.locator('[data-version-category="all"]').getAttribute('aria-pressed'),'true');
 await choose('release');
 assert.ok(await page.locator('[data-official-type="release"]').count());
 await screenshot(app,'versions-release-light');checks.push('official-release-category-and-local-time');
 await page.locator('[data-version-query]').fill('unsubmitted');
 await page.locator('[data-load-more]').click();
 await page.waitForFunction(()=>document.querySelectorAll('[data-version-id]').length>50,null,{timeout:45000});
 const next=app.sent.findLast(call=>call.path.startsWith('/catalog?')&&new URL('https://local.invalid'+call.path).searchParams.get('offset')==='50');
 assert.ok(next);assert.equal(new URL('https://local.invalid'+next.path).searchParams.get('q'),null);checks.push('pagination-keeps-submitted-query');
 await choose('snapshot');await choose('legacy');
 await page.locator('[data-version-legacy="old_alpha"]').click();
 await page.locator('[data-official-type="old_alpha"]').first().waitFor({timeout:45000});
 await screenshot(app,'versions-legacy-alpha');checks.push('official-snapshot-beta-alpha-categories');
 await choose('release');await page.locator('[data-version-query]').fill('1.20.6');
 await page.locator('form[data-version-search]').evaluate(form=>form.requestSubmit());
 await page.locator('[data-version-id="1.20.6"]').waitFor({timeout:45000});
 await page.waitForFunction(()=>!document.querySelector('[data-refresh-catalog]')?.disabled);
 const refreshing=app.sent.length;await page.locator('[data-refresh-catalog]').click();
 await page.waitForFunction(()=>!document.querySelector('[data-refresh-catalog]')?.disabled,null,{timeout:45000});
 assert.ok(app.sent.slice(refreshing).some(call=>call.path==='/catalog/refresh'));
 assert.ok(app.sent.slice(refreshing).some(call=>call.path.startsWith('/catalog?')&&new URL('https://local.invalid'+call.path).searchParams.get('q')==='1.20.6'&&call.path.includes('type=release')));
 assert.equal(await page.locator('[data-version-category="release"]').getAttribute('aria-pressed'),'true');checks.push('refresh-keeps-official-filter-and-search');
 await page.locator('[data-version-id="1.20.6"]').click();
 await page.locator('[data-instance-name]').fill('版本分类验收实例');
 await page.locator('[data-create-instance]').evaluate(form=>form.requestSubmit());
 await page.locator('[data-instance-id]').waitFor({timeout:30000});
 const instances=(await rpc(app,'/instances')).instances;
 assert.equal(instances.length,1);assert.equal(instances[0].installation,null);checks.push('existing-create-instance-flow-preserved');
 await page.locator('[data-launcher-page="versions"]').click();await page.locator('[data-version-tab="installed"]').click();
 assert.equal(await page.locator('[data-installed-version]').count(),0);
 assert.ok((await page.locator('[aria-label="已安装版本"]').innerText()).includes('1 个实例尚未安装'));
 await screenshot(app,'versions-installed-uninstalled-boundary');checks.push('prepared-instance-is-not-installed');
 await page.locator('[data-launcher-page="resourcepacks"]').click();
 await page.locator('[data-resource-unavailable]').waitFor();
 const before=app.sent.length;await page.locator('[data-resource-toggle]').click();
 assert.equal(await page.locator('[data-resource-toggle]').getAttribute('aria-expanded'),'false');
 assert.ok((await page.locator('[data-resource-toggle]').innerText()).includes('资源包'));
 assert.equal(app.sent.length,before);assert.equal(await page.locator('[data-resource-unavailable]').count(),1);checks.push('collapse-keeps-page-without-remote-action');
 for(const id of ['simple','cards','immersive','workbench']){await preset(app,id);assert.equal(await page.locator('[data-resource-toggle]').getAttribute('aria-expanded'),'false');}
 await page.locator('[data-resource-toggle]').focus();await page.keyboard.press('Enter');
 for(const id of ['shaders','recordings','schematics','bridge','servers']){await page.locator(`[data-launcher-page="${id}"]`).click();assert.equal(await page.locator('[data-resource-unavailable]').count(),1);assert.equal(await page.locator('[data-feature-action]').isDisabled(),true);}
 checks.push('resource-pending-pages-and-four-presets');
 await page.locator('[data-workspace-settings]').click();await page.locator('[data-workspace-theme]').selectOption('forest');await page.locator('[data-close-appearance]').click();
 helper(['--pid',String(app.child.pid),'--started-at',app.ownership.processStartedAt,'--title','三秋叶启动器','--action','resize','--width','1000','--height','700']);
 await screenshot(app,'resource-navigation-forest-narrow');
 assert.equal(await page.locator('body').evaluate(e=>e.scrollWidth<=innerWidth),true);
 await page.locator('[data-resource-toggle]').focus();await page.keyboard.press('Space');
 assert.equal(await page.locator('[data-resource-toggle]').getAttribute('aria-expanded'),'false');
 await page.locator('.feature-status-trigger').click();await page.locator('[aria-label="功能运行情况"]').waitFor();await page.keyboard.press('Escape');checks.push('narrow-keyboard-and-feature-status');
 write('version-navigation-calls.json',app.calls);write('version-navigation-sent.json',app.sent);
}
