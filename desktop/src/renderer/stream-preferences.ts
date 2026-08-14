import type { DesktopSettings, StreamDetails, StreamVariant } from '../shared/contracts';

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
