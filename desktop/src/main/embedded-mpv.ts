import { access } from 'node:fs/promises';
import path from 'node:path';

export function embeddedMpvAddonPath(resourcesPath: string, applicationPath: string, packaged: boolean): string {
  return packaged
    ? path.join(resourcesPath, 'native', 'mpv', 'mpv_addon.node')
    : path.join(applicationPath, 'node_modules', 'electron-mpv-video', 'native', 'mpv-addon', 'build', 'Release', 'mpv_addon.node');
}

export async function embeddedMpvAvailable(addonPath: string): Promise<boolean> {
  if (!((process.platform === 'win32' && process.arch === 'x64')
    || (process.platform === 'darwin' && process.arch === 'arm64'))) return false;
  try {
    await access(addonPath);
    return true;
  } catch {
    return false;
  }
}
