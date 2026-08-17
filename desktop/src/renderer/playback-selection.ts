import type { StreamDetails, StreamVariant, SubtitleVariant } from '../shared/contracts';

export interface PlaybackSelection {
  video?: StreamVariant;
  audio?: StreamVariant;
  subtitle?: SubtitleVariant;
  source?: string;
}

export function resolvePlaybackSelection(
  details: StreamDetails,
  videoChoice: string,
  audioChoice: string,
  subtitleChoice: string,
): PlaybackSelection {
  const selectedVideo = videoChoice === 'auto' ? undefined : details.videoStreams[Number(videoChoice)];
  const selectedAudio = audioChoice === 'auto' ? undefined : details.audioStreams[Number(audioChoice)];
  const subtitle = subtitleChoice === 'none' ? undefined : details.subtitles[Number(subtitleChoice)];
  const automaticVideo = !details.hlsUrl && !details.dashMpdUrl
    ? details.videoStreams.find((stream) => !stream.videoOnly) ?? details.videoStreams[0]
    : undefined;
  const video = selectedVideo ?? automaticVideo;
  const source = selectedVideo?.url ?? details.hlsUrl ?? details.dashMpdUrl
    ?? automaticVideo?.url ?? details.audioStreams[0]?.url;
  const audio = video?.videoOnly
    ? selectedAudio ?? details.audioStreams.find((stream) => !video.audioTrackId
      || stream.audioTrackId === video.audioTrackId) ?? details.audioStreams[0]
    : selectedAudio;
  return { video, audio, subtitle, source };
}
