import { describe, expect, it } from 'vitest';
import { defaultDesktopSettings, type StreamDetails } from '../shared/contracts';
import { preferredAudioIndex, preferredVideoIndex } from './stream-preferences';

const details: StreamDetails = {
  serviceId: 0, url: 'https://example.test/watch', name: 'Example', duration: 10, streamType: 'VIDEO_STREAM',
  videoStreams: [
    { id: 'low', url: 'https://example.test/low', resolution: '480p', format: 'MPEG-4' },
    { id: 'webm', url: 'https://example.test/high', resolution: '720p60', format: 'WebM' },
    { id: 'mp4', url: 'https://example.test/preferred', resolution: '720p60', format: 'MPEG-4' },
  ],
  audioStreams: [
    { id: 'dubbed', url: 'https://example.test/dubbed', format: 'M4A', audioTrackType: 'DUBBED' },
    { id: 'original', url: 'https://example.test/original', format: 'WebM', audioTrackType: 'ORIGINAL' },
    { id: 'descriptive', url: 'https://example.test/descriptive', format: 'M4A', audioTrackType: 'DESCRIPTIVE' },
  ],
  subtitles: [],
  relatedItems: [],
  sponsorBlockSegments: [],
};

describe('desktop stream preferences', () => {
  it('prefers the configured resolution and format', () => {
    expect(preferredVideoIndex(details, defaultDesktopSettings)).toBe(2);
  });

  it('prefers descriptive audio when requested', () => {
    expect(preferredAudioIndex(details, { ...defaultDesktopSettings, preferDescriptiveAudio: true })).toBe(2);
  });

  it('uses the original audio preference before the format preference', () => {
    expect(preferredAudioIndex(details, defaultDesktopSettings)).toBe(1);
  });
});
