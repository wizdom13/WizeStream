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
  autoplayNext: z.boolean(),
  autoQueueRelated: z.boolean(),
  clearQueueConfirmation: z.boolean(),
  seekDurationSeconds: z.union([
    z.literal(5), z.literal(10), z.literal(15), z.literal(30), z.literal(60),
  ]),
  useInexactSeek: z.boolean(),
  startPlayerFullscreen: z.boolean(),
  preferredOpenAction: z.enum(['details', 'play', 'enqueue']),
  perChannelPlaybackProfiles: z.boolean(),
  rememberLiveStreamSpeed: z.boolean(),
  channelPlaybackProfiles: z.record(z.string().max(2_000), z.object({
    videoResolution: z.string().max(80).optional(),
    videoFormat: z.string().max(80).optional(),
    audioTrackId: z.string().max(500).optional(),
    audioLocale: z.string().max(80).optional(),
    subtitleLanguageTag: z.string().max(80).nullable().optional(),
    speed: z.number().min(0.1).max(3).optional(),
    updatedAt: z.number().int().nonnegative(),
  }).strict()).refine((profiles) => Object.keys(profiles).length <= 500,
    'No more than 500 channel playback profiles may be stored'),
  equalizer: z.object({
    enabled: z.boolean(),
    preset: z.enum(['flat', 'bass_boost', 'vocal', 'acoustic', 'rock', 'custom']),
    gains: z.array(z.number().int().min(-24).max(24)).length(10),
  }).strict(),
  playbackParameters: z.object({
    speed: z.number().min(0.1).max(3),
    pitch: z.number().min(0.1).max(3),
    skipSilence: z.boolean(),
    unhook: z.boolean(),
    adjustmentStep: z.union([
      z.literal(0.01), z.literal(0.05), z.literal(0.1), z.literal(0.25), z.literal(1),
    ]),
    pitchMode: z.enum(['percent', 'semitone']),
  }).strict(),
  sponsorBlock: z.object({
    enabled: z.boolean(),
    gracedRewind: z.boolean(),
    notifications: z.boolean(),
    categories: z.object({
      sponsor: z.object({ enabled: z.boolean(), behavior: z.enum(['skip', 'manual', 'dont_skip']) }).strict(),
      intro: z.object({ enabled: z.boolean(), behavior: z.enum(['skip', 'manual', 'dont_skip']) }).strict(),
      outro: z.object({ enabled: z.boolean(), behavior: z.enum(['skip', 'manual', 'dont_skip']) }).strict(),
      interaction: z.object({ enabled: z.boolean(), behavior: z.enum(['skip', 'manual', 'dont_skip']) }).strict(),
      self_promo: z.object({ enabled: z.boolean(), behavior: z.enum(['skip', 'manual', 'dont_skip']) }).strict(),
      non_music: z.object({ enabled: z.boolean(), behavior: z.enum(['skip', 'manual', 'dont_skip']) }).strict(),
      preview: z.object({ enabled: z.boolean(), behavior: z.enum(['skip', 'manual', 'dont_skip']) }).strict(),
      filler: z.object({ enabled: z.boolean(), behavior: z.enum(['skip', 'manual', 'dont_skip']) }).strict(),
      highlight: z.object({ enabled: z.boolean(), behavior: z.literal('dont_skip') }).strict(),
    }).strict(),
  }).strict(),
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

  validate(input: unknown): DesktopSettings {
    if (!input || typeof input !== 'object' || Array.isArray(input)) {
      return settingsSchema.parse(input);
    }
    return settingsSchema.parse({
      autoplayNext: defaultDesktopSettings.autoplayNext,
      autoQueueRelated: defaultDesktopSettings.autoQueueRelated,
      clearQueueConfirmation: defaultDesktopSettings.clearQueueConfirmation,
      seekDurationSeconds: defaultDesktopSettings.seekDurationSeconds,
      useInexactSeek: defaultDesktopSettings.useInexactSeek,
      startPlayerFullscreen: defaultDesktopSettings.startPlayerFullscreen,
      preferredOpenAction: defaultDesktopSettings.preferredOpenAction,
      perChannelPlaybackProfiles: defaultDesktopSettings.perChannelPlaybackProfiles,
      rememberLiveStreamSpeed: defaultDesktopSettings.rememberLiveStreamSpeed,
      channelPlaybackProfiles: defaultDesktopSettings.channelPlaybackProfiles,
      equalizer: defaultDesktopSettings.equalizer,
      playbackParameters: defaultDesktopSettings.playbackParameters,
      sponsorBlock: defaultDesktopSettings.sponsorBlock,
      ...(input as Record<string, unknown>),
    });
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

  async replace(input: unknown): Promise<DesktopSettings> {
    this.value = this.validate(input);
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
