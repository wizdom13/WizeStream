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

  it('adds disabled SponsorBlock defaults to settings saved by older Desktop versions', async () => {
    const filePath = await fixture();
    const legacySettings: Record<string, unknown> = { ...defaultDesktopSettings };
    delete legacySettings.sponsorBlock;
    await writeFile(filePath, JSON.stringify(legacySettings));

    const manager = new SettingsManager(filePath);
    expect(await manager.initialize()).toMatchObject({
      sponsorBlock: { enabled: false, gracedRewind: true, notifications: true },
    });
  });

  it('adds the Android-compatible flat equalizer to older Desktop settings', async () => {
    const filePath = await fixture();
    const legacySettings: Record<string, unknown> = { ...defaultDesktopSettings };
    delete legacySettings.equalizer;
    await writeFile(filePath, JSON.stringify(legacySettings));

    const manager = new SettingsManager(filePath);
    expect(await manager.initialize()).toMatchObject({
      equalizer: { enabled: false, preset: 'flat', gains: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0] },
    });
  });

  it('adds Android-compatible playback parameters to older Desktop settings', async () => {
    const filePath = await fixture();
    const legacySettings: Record<string, unknown> = { ...defaultDesktopSettings };
    delete legacySettings.playbackParameters;
    await writeFile(filePath, JSON.stringify(legacySettings));

    const manager = new SettingsManager(filePath);
    expect(await manager.initialize()).toMatchObject({
      playbackParameters: {
        speed: 1, pitch: 1, skipSilence: false, unhook: true,
        adjustmentStep: 0.25, pitchMode: 'percent',
      },
    });
  });

  it('adds Milestone 1 playback defaults to older Desktop settings', async () => {
    const filePath = await fixture();
    const legacySettings: Record<string, unknown> = { ...defaultDesktopSettings };
    delete legacySettings.autoplayNext;
    delete legacySettings.autoQueueRelated;
    delete legacySettings.clearQueueConfirmation;
    delete legacySettings.seekDurationSeconds;
    delete legacySettings.useInexactSeek;
    delete legacySettings.startPlayerFullscreen;
    delete legacySettings.preferredOpenAction;
    delete legacySettings.perChannelPlaybackProfiles;
    delete legacySettings.rememberLiveStreamSpeed;
    delete legacySettings.channelPlaybackProfiles;
    await writeFile(filePath, JSON.stringify(legacySettings));

    const manager = new SettingsManager(filePath);
    expect(await manager.initialize()).toMatchObject({
      autoplayNext: true,
      autoQueueRelated: true,
      clearQueueConfirmation: true,
      seekDurationSeconds: 10,
      useInexactSeek: false,
      startPlayerFullscreen: false,
      preferredOpenAction: 'details',
      perChannelPlaybackProfiles: true,
      rememberLiveStreamSpeed: true,
      channelPlaybackProfiles: {},
    });
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

  it('validates and atomically replaces settings restored from a backup', async () => {
    const filePath = await fixture();
    const manager = new SettingsManager(filePath);
    await manager.initialize();
    const restored = { ...defaultDesktopSettings, theme: 'dark' as const, learningMode: true };
    expect(manager.validate(restored)).toEqual(restored);
    expect(await manager.replace(restored)).toEqual(restored);
    await expect(manager.replace({ theme: 'dark' })).rejects.toThrow();
  });

  it('restores a complete legacy backup that predates SponsorBlock settings', async () => {
    const manager = new SettingsManager(await fixture());
    await manager.initialize();
    const legacySettings: Record<string, unknown> = { ...defaultDesktopSettings };
    delete legacySettings.sponsorBlock;
    delete legacySettings.equalizer;
    delete legacySettings.playbackParameters;

    expect(await manager.replace(legacySettings)).toMatchObject({
      sponsorBlock: { enabled: false, gracedRewind: true, notifications: true },
      equalizer: { enabled: false, preset: 'flat' },
      playbackParameters: { speed: 1, pitch: 1, skipSilence: false, unhook: true },
    });
  });
});
