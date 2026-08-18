import type {
  ChannelPlaybackProfile, DesktopSettings, StreamDetails, StreamVariant,
} from '../shared/contracts';

const videoFormats: Record<DesktopSettings['defaultVideoFormat'], string[]> = {
  video_mp4: ['mp4', 'mpeg-4', 'mpeg4'],
  video_webm: ['webm'],
  video_3gp: ['3gp'],
};

const audioFormats: Record<DesktopSettings['defaultAudioFormat'], string[]> = {
  audio_m4a: ['m4a', 'mp4', 'mpeg-4', 'mpeg4'],
  audio_webm: ['webm'],
};

function formatMatches(stream: StreamVariant, formats: string[]): boolean {
  const value = stream.format?.toLowerCase() ?? '';
  return formats.some((format) => value.includes(format));
}

export function preferredVideoIndex(details: StreamDetails, settings: DesktopSettings): number | undefined {
  const profile = channelPlaybackProfile(details, settings);
  if (profile?.videoResolution || profile?.videoFormat) {
    const index = details.videoStreams.findIndex((stream) =>
      (!profile.videoResolution || stream.resolution === profile.videoResolution)
      && (!profile.videoFormat || stream.format === profile.videoFormat));
    if (index >= 0) return index;
  }
  const formats = videoFormats[settings.defaultVideoFormat];
  const preferredResolution = settings.defaultResolution.toLowerCase();
  const preferredHeight = preferredResolution.match(/\d+p/)?.[0];
  let bestScore = 0;
  let bestIndex: number | undefined;
  details.videoStreams.forEach((stream, index) => {
    const resolution = stream.resolution?.toLowerCase() ?? '';
    const exactResolution = resolution.includes(preferredResolution);
    const sameHeight = preferredHeight !== undefined && resolution.includes(preferredHeight);
    const height = Number(resolution.match(/\d+(?=p)/)?.[0] ?? 0);
    const resolutionScore = preferredResolution === 'best_resolution' ? height / 100
      : exactResolution ? 8 : sameHeight ? 6 : 0;
    const score = resolutionScore
      + (formatMatches(stream, formats) ? 3 : 0) + (!stream.videoOnly ? 1 : 0);
    if (score > bestScore) { bestScore = score; bestIndex = index; }
  });
  return bestIndex;
}

export function preferredAudioIndex(details: StreamDetails, settings: DesktopSettings): number | undefined {
  const profile = channelPlaybackProfile(details, settings);
  if (profile?.audioTrackId || profile?.audioLocale) {
    const index = details.audioStreams.findIndex((stream) =>
      (!profile.audioTrackId || stream.audioTrackId === profile.audioTrackId)
      && (!profile.audioLocale || stream.audioLocale === profile.audioLocale));
    if (index >= 0) return index;
  }
  const formats = audioFormats[settings.defaultAudioFormat];
  let bestScore = 0;
  let bestIndex: number | undefined;
  details.audioStreams.forEach((stream, index) => {
    const score = (settings.preferDescriptiveAudio && stream.audioTrackType === 'DESCRIPTIVE' ? 8 : 0)
      + (settings.preferOriginalAudio && stream.audioTrackType === 'ORIGINAL' ? 5 : 0)
      + (formatMatches(stream, formats) ? 2 : 0);
    if (score > bestScore) { bestScore = score; bestIndex = index; }
  });
  return bestIndex;
}

export function preferredSubtitleIndex(details: StreamDetails, settings: DesktopSettings): number | undefined {
  const profile = channelPlaybackProfile(details, settings);
  if (!profile || profile.subtitleLanguageTag == null) return undefined;
  const index = details.subtitles.findIndex((subtitle) => subtitle.languageTag === profile.subtitleLanguageTag);
  return index >= 0 ? index : undefined;
}

export function channelProfileKey(details: Pick<StreamDetails, 'serviceId' | 'uploaderUrl' | 'uploaderName'>) {
  const identity = details.uploaderUrl?.trim() || details.uploaderName?.trim();
  return identity ? `${details.serviceId}:${identity}` : undefined;
}

export function channelPlaybackProfile(details: StreamDetails, settings: DesktopSettings) {
  if (!settings.perChannelPlaybackProfiles) return undefined;
  const key = channelProfileKey(details);
  return key ? settings.channelPlaybackProfiles[key] : undefined;
}

export function updatedChannelProfile(
  details: StreamDetails,
  settings: DesktopSettings,
  patch: Partial<ChannelPlaybackProfile>,
): Record<string, ChannelPlaybackProfile> | undefined {
  if (!settings.perChannelPlaybackProfiles) return undefined;
  const key = channelProfileKey(details);
  if (!key) return undefined;
  return {
    ...settings.channelPlaybackProfiles,
    [key]: { ...settings.channelPlaybackProfiles[key], ...patch, updatedAt: Date.now() },
  };
}
