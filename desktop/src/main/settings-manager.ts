import path from 'node:path';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { z } from 'zod';
import { defaultDesktopSettings, type DesktopSettings } from '../shared/contracts.js';

const settingsSchema = z.object({
  theme: z.enum(['system', 'light', 'dark']),
  defaultServiceId: z.number().int().nonnegative().nullable(),
  defaultResolution: z.enum([
    'best_resolution', '1080p60', '1080p', '720p60', '720p', '480p', '360p', '240p', '144p',
  ]),
  defaultVideoFormat: z.enum(['video_mp4', 'video_webm', 'video_3gp']),
  defaultAudioFormat: z.enum(['audio_m4a', 'audio_webm']),
  preferOriginalAudio: z.boolean(),
  preferDescriptiveAudio: z.boolean(),
  enableWatchHistory: z.boolean(),
  enableSearchHistory: z.boolean(),
  learningMode: z.boolean(),
  learningNotes: z.boolean(),
}).strict();

const settingsPatchSchema = settingsSchema.partial();

export class SettingsManager {
  private value: DesktopSettings = { ...defaultDesktopSettings };

  constructor(private readonly filePath: string) {}

  async initialize(): Promise<DesktopSettings> {
    try {
      const parsed = JSON.parse(await readFile(this.filePath, 'utf8')) as unknown;
      const migrated = settingsPatchSchema.parse(parsed);
      this.value = settingsSchema.parse({ ...defaultDesktopSettings, ...migrated });
    } catch (error) {
      const code = error && typeof error === 'object' && 'code' in error ? error.code : undefined;
      if (code !== 'ENOENT') console.warn('Desktop settings could not be read; defaults will be used', error);
      this.value = { ...defaultDesktopSettings };
    }
    return this.get();
  }

  get(): DesktopSettings {
    return { ...this.value };
  }

  async update(input: unknown): Promise<DesktopSettings> {
    const patch = settingsPatchSchema.parse(input);
    this.value = settingsSchema.parse({ ...this.value, ...patch });
    await this.save();
    return this.get();
  }

  async reset(): Promise<DesktopSettings> {
    this.value = { ...defaultDesktopSettings };
    await this.save();
    return this.get();
  }

  private async save(): Promise<void> {
    await mkdir(path.dirname(this.filePath), { recursive: true });
    const temporaryPath = `${this.filePath}.tmp`;
    await writeFile(temporaryPath, `${JSON.stringify(this.value, null, 2)}\n`, { mode: 0o600 });
    await rename(temporaryPath, this.filePath);
  }
}
