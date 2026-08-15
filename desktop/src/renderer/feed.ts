import type { PlaybackState, SearchItem } from '../shared/contracts';

export type FeedFilter = 'none' | 'unwatched' | 'live' | 'shorts' | 'partially-watched';

const compactNumber = new Intl.NumberFormat('en', {
  notation: 'compact',
  maximumFractionDigits: 1,
});
const relativeTime = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

export function viewCountLabel(value?: number | null): string | undefined {
  if (value == null || !Number.isSafeInteger(value) || value < 0) return undefined;
  const count = compactNumber.format(value).replace('K', 'k').replace('M', 'm').replace('B', 'b');
  return `${count} ${value === 1 ? 'view' : 'views'}`;
}

export function publishedAgeLabel(item: SearchItem, now = Date.now()): string | undefined {
  if (item.publishedAt == null || !Number.isFinite(item.publishedAt)) return item.textualUploadDate;
  const deltaSeconds = (item.publishedAt - now) / 1_000;
  const units: Array<[Intl.RelativeTimeFormatUnit, number]> = [
    ['year', 365 * 24 * 60 * 60],
    ['month', 30 * 24 * 60 * 60],
    ['week', 7 * 24 * 60 * 60],
    ['day', 24 * 60 * 60],
    ['hour', 60 * 60],
    ['minute', 60],
  ];
  const absoluteSeconds = Math.abs(deltaSeconds);
  const [unit, seconds] = units.find(([, size]) => absoluteSeconds >= size) ?? ['second', 1];
  return relativeTime.format(Math.round(deltaSeconds / seconds), unit);
}

export function matchesFeedFilter(
  item: SearchItem,
  filter: FeedFilter,
  playback?: PlaybackState,
): boolean {
  if (filter === 'none') return true;
  if (filter === 'live') return item.streamType === 'LIVE_STREAM' || item.streamType === 'AUDIO_LIVE_STREAM';
  if (filter === 'shorts') return !isLive(item)
    && (item.shortForm === true || item.url.includes('/shorts/') || (duration(item) >= 1 && duration(item) <= 180));
  const valid = playback != null && isPlaybackValid(playback.positionMillis, duration(item));
  if (filter === 'unwatched') return !valid;
  return valid && !isPlaybackFinished(playback.positionMillis, duration(item));
}

export function playbackKey(serviceId: number, url: string): string {
  return `${serviceId}:${url}`;
}

function duration(item: SearchItem): number {
  return item.duration != null && item.duration > 0 ? item.duration : 0;
}

function isLive(item: SearchItem): boolean {
  return item.streamType === 'LIVE_STREAM' || item.streamType === 'AUDIO_LIVE_STREAM';
}

function isPlaybackValid(positionMillis: number, durationSeconds: number): boolean {
  return positionMillis > 5_000 || positionMillis > durationSeconds * 1_000 / 4;
}

function isPlaybackFinished(positionMillis: number, durationSeconds: number): boolean {
  return durationSeconds > 0
    && positionMillis >= durationSeconds * 1_000 - 60_000
    && positionMillis >= durationSeconds * 1_000 * 3 / 4;
}
