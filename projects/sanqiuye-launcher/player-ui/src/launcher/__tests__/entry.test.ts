import { describe, expect, it, vi } from 'vitest';
import { resolveEntryMode, type EntryContext } from '../entry';

const browser: EntryContext = {native:false,embedded:false,pathname:'/',search:''};
describe('player entry boundary', () => {
  it('opens the standalone launcher without calling native IPC', async () => {
    const read = vi.fn();
    expect(await resolveEntryMode(browser, read)).toBe('launcher');
    expect(read).not.toHaveBeenCalled();
  });
  it('keeps embedded and player-preview pages out of native commands', async () => {
    const read = vi.fn();
    for (const context of [{...browser,native:true,embedded:true}, {...browser,native:true,pathname:'/player-preview/index.html'}]) {
      expect(await resolveEntryMode(context, read)).toBe('preview');
    }
    expect(read).not.toHaveBeenCalled();
  });
  it('preserves the management preview and explicit browser updater preview', async () => {
    for (const search of ['?adminPreview=1','?updaterPreview=1']) {
      expect(await resolveEntryMode({...browser,search},vi.fn())).toBe('preview');
    }
  });
  it('reads the native startup mode instead of accepting a query override', async () => {
    const read = vi.fn(async () => 'updater' as const);
    expect(await resolveEntryMode({...browser,native:true,search:'?adminPreview=1'},read)).toBe('updater');
    expect(read).toHaveBeenCalledTimes(1);
  });
  it('fails closed on an unavailable or invalid native response', async () => {
    await expect(resolveEntryMode({...browser,native:true},async()=>{throw new Error('读取失败');})).rejects.toThrow('读取失败');
    await expect(resolveEntryMode({...browser,native:true},async()=> 'unknown' as never)).rejects.toThrow();
  });
});
