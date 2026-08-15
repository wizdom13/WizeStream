import { describe, expect, it } from 'vitest';
import type { SearchItem } from '../shared/contracts';
import { matchesFeedFilter, publishedAgeLabel, viewCountLabel } from './feed';

const video: SearchItem = {
  type: 'STREAM', serviceId: 0, url: 'https://video.example/watch/1', name: 'Video',
  duration: 600, streamType: 'VIDEO_STREAM',
};

describe("What's New feed presentation", () => {
  it('formats compact views and relative publication time', () => {
    expect(viewCountLabel(405_000)).toBe('405k views');
    expect(viewCountLabel(1)).toBe('1 view');
    expect(publishedAgeLabel({ ...video, publishedAt: Date.UTC(2026, 7, 14, 12) }, Date.UTC(2026, 7, 14, 22)))
      .toBe('10 hours ago');
  });

  it('matches Android live and shorts filters', () => {
    expect(matchesFeedFilter({ ...video, streamType: 'LIVE_STREAM' }, 'live')).toBe(true);
    expect(matchesFeedFilter({ ...video, duration: 180 }, 'shorts')).toBe(true);
    expect(matchesFeedFilter({ ...video, duration: 181 }, 'shorts')).toBe(false);
    expect(matchesFeedFilter({ ...video, url: 'https://video.example/shorts/1' }, 'shorts')).toBe(true);
  });

  it('matches Android playback progress thresholds', () => {
    expect(matchesFeedFilter(video, 'unwatched')).toBe(true);
    expect(matchesFeedFilter(video, 'unwatched', { serviceId: 0, url: video.url, positionMillis: 6_000 }))
      .toBe(false);
    expect(matchesFeedFilter(video, 'partially-watched', { serviceId: 0, url: video.url, positionMillis: 300_000 }))
      .toBe(true);
    expect(matchesFeedFilter(video, 'partially-watched', { serviceId: 0, url: video.url, positionMillis: 570_000 }))
      .toBe(false);
  });
});
