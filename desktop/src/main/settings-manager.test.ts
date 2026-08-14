import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { defaultDesktopSettings } from '../shared/contracts.js';
import { SettingsManager } from './settings-manager.js';

const temporaryDirectories: string[] = [];

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => rm(directory, { recursive: true, force: true })));
});

async function fixture() {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'wizestream-settings-'));
  temporaryDirectories.push(directory);
  return path.join(directory, 'settings.json');
}

describe('SettingsManager', () => {
  it('uses the Android-aligned desktop defaults when no file exists', async () => {
    const manager = new SettingsManager(await fixture());
    expect(await manager.initialize()).toEqual(defaultDesktopSettings);
  });

  it('persists a validated partial update', async () => {
    const filePath = await fixture();
    const manager = new SettingsManager(filePath);
    await manager.initialize();
    await manager.update({ theme: 'dark', enableSearchHistory: false });

    const restored = new SettingsManager(filePath);
    expect(await restored.initialize()).toMatchObject({ theme: 'dark', enableSearchHistory: false });
    expect(JSON.parse(await readFile(filePath, 'utf8'))).toMatchObject({ theme: 'dark' });
  });

  it('rejects unknown or invalid settings', async () => {
    const manager = new SettingsManager(await fixture());
    await manager.initialize();
    await expect(manager.update({ theme: 'neon' })).rejects.toThrow();
    await expect(manager.update({ unexpected: true })).rejects.toThrow();
  });

  it('recovers from a corrupt file without exposing malformed values', async () => {
    const filePath = await fixture();
    await writeFile(filePath, '{broken');
    const manager = new SettingsManager(filePath);
    expect(await manager.initialize()).toEqual(defaultDesktopSettings);
  });
});
