import {currentEntryContext} from './entry';

async function windowCommand(command: string): Promise<void> {
  const context=currentEntryContext();
  if (!context.native || context.embedded || context.pathname.startsWith('/player-preview/')) return;
  const {invoke}=await import('@tauri-apps/api/core');
  await invoke(command);
}
export const showPlayerWindow=()=>windowCommand('window_show');
export const minimizePlayerWindow=()=>windowCommand('window_minimize');
export const togglePlayerMaximize=()=>windowCommand('window_toggle_maximize');
export const closePlayerWindow=()=>windowCommand('window_close');
