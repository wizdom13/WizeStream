import { EventEmitter } from 'node:events';
import { describe, expect, test, vi } from 'vitest';
import type { AppUpdater } from 'electron-updater';
import { UpdateManager } from './update-manager.js';

class FakeUpdater extends EventEmitter {
  autoDownload = true;
  autoInstallOnAppQuit = true;
  allowPrerelease = false;
  allowDowngrade = true;
  disableWebInstaller = false;
  channel: string | null = null;
  checkForUpdates = vi.fn(async () => null);
  downloadUpdate = vi.fn(async () => []);
  quitAndInstall = vi.fn();
}

describe('UpdateManager', () => {
  test('checks without downloading and waits for explicit confirmation', async () => {
    const updater = new FakeUpdater();
    const states: string[] = [];
    const manager = new UpdateManager(updater as unknown as AppUpdater, '0.6.0-beta.1',
      (state) => states.push(state.status));
    manager.initialize();

    await manager.check();
    updater.emit('update-available', { version: '0.6.0-beta.2', releaseName: 'Beta 2' });

    expect(manager.state().status).toBe('available');
    expect(updater.downloadUpdate).not.toHaveBeenCalled();
    await manager.download();
    expect(updater.downloadUpdate).toHaveBeenCalledOnce();
    expect(states).toContain('downloading');
  });

  test('installs only after the downloaded event and a separate user action', () => {
    const updater = new FakeUpdater();
    const manager = new UpdateManager(updater as unknown as AppUpdater, '0.6.0-beta.1', () => undefined);
    manager.initialize();

    manager.install();
    expect(updater.quitAndInstall).not.toHaveBeenCalled();
    updater.emit('update-downloaded', { version: '0.6.0-beta.2' });
    manager.install();

    expect(updater.quitAndInstall).toHaveBeenCalledWith(false, true);
    expect(updater.autoDownload).toBe(false);
    expect(updater.autoInstallOnAppQuit).toBe(false);
    expect(updater.allowDowngrade).toBe(false);
  });

  test('is unavailable in development and packaged smoke tests', async () => {
    const manager = new UpdateManager(undefined, '0.6.0-beta.1', () => undefined);
    manager.initialize();
    expect((await manager.check()).status).toBe('unavailable');
  });
});
