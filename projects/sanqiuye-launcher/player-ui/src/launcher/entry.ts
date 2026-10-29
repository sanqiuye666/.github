export type EntryMode = 'launcher' | 'updater' | 'preview';
export interface EntryContext { native: boolean; embedded: boolean; pathname: string; search: string }

export function currentEntryContext(): EntryContext {
  return {native:'__TAURI_INTERNALS__' in window,embedded:window.parent!==window,pathname:window.location.pathname,search:window.location.search};
}

export async function readPlayerEntryMode(): Promise<EntryMode> {
  const {invoke}=await import('@tauri-apps/api/core');
  return invoke<EntryMode>('player_entry_mode');
}

export async function resolveEntryMode(context: EntryContext, readNativeMode: () => Promise<EntryMode>): Promise<EntryMode> {
  if (context.embedded || context.pathname.startsWith('/player-preview/')) return 'preview';
  if (context.native) {
    const mode=await readNativeMode();
    if (!['launcher','updater','preview'].includes(mode)) throw new Error('启动程序返回了无法识别的入口状态');
    return mode;
  }
  const query=new URLSearchParams(context.search);
  return query.get('adminPreview')==='1' || query.get('updaterPreview')==='1' ? 'preview' : 'launcher';
}
