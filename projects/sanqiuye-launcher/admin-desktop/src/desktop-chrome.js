// Shared by the bundled chooser and the desktop-owned management webview.
(() => {
  if (window.parent !== window) return;
  const meta = window.__DFS_DESKTOP_META__ || {};
  const diagnostic = (message) => {
    if (!meta.check) return;
    const report = () => { document.title = `DFS_FRAME_ERROR:${message}`; };
    if (document.head) report(); else document.addEventListener('DOMContentLoaded', report, { once:true });
  };
  if (meta.check) window.addEventListener('error', e => diagnostic(e.message));
  const install = () => {
    const root = document.documentElement;
    root.classList.add('dfs-desktop');
    if (meta.workspace) root.classList.add('dfs-workspace');
    if (document.getElementById('dfs-desktop-frame')) return;
    if (meta.css) {
      const sheet = new CSSStyleSheet();
      sheet.replaceSync(meta.css);
      document.adoptedStyleSheets = [...document.adoptedStyleSheets, sheet];
    }
    const frame = document.createElement('header');
    frame.id = 'dfs-desktop-frame';
    frame.setAttribute('aria-label', '桌面窗口');
    const mark = document.createElement('span');
    // The app icon, as in src/brand-logo.ts (this file also runs as a plain initialization script, so it keeps a copy).
    mark.className = 'dfs-frame-mark'; mark.innerHTML = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"512\" height=\"512\" viewBox=\"0 0 512 512\" fill=\"none\" role=\"img\" aria-labelledby=\"dfs-frame-title dfs-frame-desc\">\n<title id=\"dfs-frame-title\">三秋叶</title><desc id=\"dfs-frame-desc\">三片青绿与暖金秋叶，沿同一枝脉生长。</desc>\n<defs><linearGradient id=\"dfs-frame-main\" x1=\"208\" y1=\"120\" x2=\"330\" y2=\"382\" gradientUnits=\"userSpaceOnUse\"><stop stop-color=\"#9AF5DD\"/><stop offset=\"1\" stop-color=\"#21B6A8\"/></linearGradient>\n<linearGradient id=\"dfs-frame-gold\" x1=\"108\" y1=\"229\" x2=\"267\" y2=\"362\" gradientUnits=\"userSpaceOnUse\"><stop stop-color=\"#FFE5A6\"/><stop offset=\"1\" stop-color=\"#DBA35B\"/></linearGradient>\n<linearGradient id=\"dfs-frame-amber\" x1=\"293\" y1=\"271\" x2=\"400\" y2=\"350\" gradientUnits=\"userSpaceOnUse\"><stop stop-color=\"#FFDB9A\"/><stop offset=\"1\" stop-color=\"#E5A568\"/></linearGradient><linearGradient id=\"dfs-frame-bg\" x1=\"64\" y1=\"0\" x2=\"448\" y2=\"512\" gradientUnits=\"userSpaceOnUse\"><stop stop-color=\"#255B50\"/><stop offset=\"1\" stop-color=\"#091E1D\"/></linearGradient></defs><rect x=\"1\" y=\"1\" width=\"510\" height=\"510\" rx=\"112\" fill=\"url(#dfs-frame-bg)\" stroke=\"#7CC9AC\" stroke-opacity=\".25\" stroke-width=\"2\"/><path d=\"M246 368C153 326 150 242 203 184C241 143 300 131 331 94C373 195 374 282 318 335C299 353 271 364 246 368Z\" fill=\"url(#dfs-frame-main)\"/><path d=\"M225 341C150 351 92 307 101 229C166 219 222 262 225 341Z\" fill=\"url(#dfs-frame-gold)\"/><path d=\"M282 370C283 300 343 252 415 259C409 331 356 377 282 370Z\" fill=\"url(#dfs-frame-amber)\"/>\n<path d=\"M205 422C228 382 245 339 264 292L310 161M242 348L145 263M255 383L371 296\" stroke=\"#FFF3D3\" stroke-width=\"13\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n<path d=\"M268 281L315 259M284 237L263 205\" stroke=\"#0C554C\" stroke-opacity=\".3\" stroke-width=\"8\" stroke-linecap=\"round\"/></svg>\n";
    const title = document.createElement('span');
    title.className = 'dfs-frame-title'; title.textContent = meta.name || '三秋叶更新管理';
    title.title = title.textContent;
    const badge = document.createElement('span');
    badge.className = 'dfs-frame-badge'; badge.textContent = meta.workspace ? '工作区' : '0.2.7';
    frame.append(mark, title, badge);
    const controls = document.createElement('div');
    controls.className = 'dfs-frame-controls';
    let maximize;
    let statusAttempts = 0;
    const statusRetryLimit = 50;
    const native = async (action) => {
      try {
        const state = await window.__TAURI_INTERNALS__.invoke('desktop_window_action', { action });
        if (maximize) {
          maximize.title = state.maximized ? '还原窗口' : '最大化';
          maximize.setAttribute('aria-label', maximize.title);
          maximize.classList.toggle('is-maximized', state.maximized);
          maximize.setAttribute('aria-pressed', String(state.maximized));
        }
        frame.removeAttribute('data-error');
        badge.textContent = meta.workspace ? '工作区' : '0.2.7'; badge.removeAttribute('title');
      } catch (error) {
        if (action === 'status' && statusAttempts++ < statusRetryLimit) { setTimeout(() => void native('status'), 200); return; }
        diagnostic(String(error));
        frame.dataset.error = 'true';
        badge.title = String(error);
        badge.textContent = '窗口操作暂不可用';
      }
    };
    const button = (action, label, path) => {
      const node = document.createElement('button');
      node.type = 'button'; node.className = `dfs-frame-button dfs-frame-${action}`;
      node.title = label; node.setAttribute('aria-label', label);
      const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.setAttribute('viewBox', '0 0 16 16'); svg.setAttribute('aria-hidden', 'true');
      const shape = document.createElementNS(svg.namespaceURI, 'path');
      shape.setAttribute('d', path); svg.append(shape); node.append(svg);
      node.addEventListener('click', () => void native(action));
      return node;
    };
    if (meta.workspace) {
      const chooser = button('connections', '切换连接', 'M2 3.5h10M9 1l3 2.5L9 6M14 12.5H4M7 10l-3 2.5L7 15');
      const label = document.createElement('span'); label.textContent = '连接'; chooser.append(label);
      frame.append(chooser);
    }
    const space = document.createElement('div'); space.className = 'dfs-frame-space'; frame.append(space);
    maximize = button('maximize', '最大化', 'M3 3h10v10H3z');
    controls.append(button('minimize', '最小化', 'M3 8h10'), maximize, button('close', '收到托盘', 'M3 3l10 10M13 3 3 13'));
    frame.append(controls);
    frame.addEventListener('mousedown', (event) => {
      if (event.button !== 0 || event.target.closest('button')) return;
      event.preventDefault();
      void native(event.detail === 2 ? 'maximize' : 'drag');
    });
    document.body.prepend(frame);
    // Keep window controls accessible while an HTML modal makes its siblings inert.
    const updateModal = () => {
      const dialogs = [...document.querySelectorAll('dialog[open]')].filter(d => d.matches(':modal'));
      const parent = dialogs.at(-1) || document.body;
      if (frame.parentNode !== parent) parent.prepend(frame);
    };
    new MutationObserver(updateModal).observe(document.body, { subtree: true, attributes: true, attributeFilter: ['open'] });
    let resizeTimer;
    window.addEventListener('resize', () => {
      clearTimeout(resizeTimer); resizeTimer = setTimeout(() => void native('status'), 100);
    });
    // WebView2 can deliver DOMContentLoaded before the IPC request origin is
    // committed. Status is read-only: wait for load when possible and keep a
    // bounded retry window, without changing any native origin permission.
    if (document.readyState === 'complete') void native('status');
    else window.addEventListener('load', () => void native('status'), { once:true });
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install, { once: true });
  else install();
})();
