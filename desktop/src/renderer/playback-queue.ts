import type { LibraryStream, SearchItem } from '../shared/contracts';

export type RepeatMode = 'off' | 'one' | 'all';

export interface PlaybackQueueState {
  items: LibraryStream[];
  currentIndex: number;
  repeatMode: RepeatMode;
  shuffle: boolean;
}

export const playbackQueueStorageKey = 'wizestream.desktop.playback-queue.v1';
export const emptyPlaybackQueue: PlaybackQueueState = {
  items: [], currentIndex: -1, repeatMode: 'off', shuffle: false,
};

export function loadPlaybackQueue(storage: Pick<Storage, 'getItem'>): PlaybackQueueState {
  try {
    const parsed = JSON.parse(storage.getItem(playbackQueueStorageKey) ?? 'null') as unknown;
    if (!parsed || typeof parsed !== 'object') return emptyPlaybackQueue;
    const candidate = parsed as Partial<PlaybackQueueState>;
    const items = Array.isArray(candidate.items) ? candidate.items.filter(isLibraryStream).slice(0, 500) : [];
    const currentIndex = Number.isInteger(candidate.currentIndex)
      ? Math.max(-1, Math.min(items.length - 1, Number(candidate.currentIndex))) : -1;
    const repeatMode = ['off', 'one', 'all'].includes(String(candidate.repeatMode))
      ? candidate.repeatMode as RepeatMode : 'off';
    return { items, currentIndex, repeatMode, shuffle: candidate.shuffle === true };
  } catch {
    return emptyPlaybackQueue;
  }
}

export function savePlaybackQueue(storage: Pick<Storage, 'setItem'>, queue: PlaybackQueueState) {
  storage.setItem(playbackQueueStorageKey, JSON.stringify(queue));
}

export function queueStreamKey(stream: Pick<LibraryStream, 'serviceId' | 'url'>) {
  return `${stream.serviceId}:${stream.url}`;
}

export function searchItemToLibraryStream(item: SearchItem): LibraryStream {
  return {
    serviceId: item.serviceId,
    url: item.url,
    title: item.name,
    duration: item.duration ?? 0,
    streamType: item.streamType ?? 'VIDEO_STREAM',
    uploader: item.uploaderName,
    uploaderUrl: item.uploaderUrl,
    thumbnailUrl: item.thumbnailUrl,
  };
}

export function playNow(queue: PlaybackQueueState, stream: LibraryStream): PlaybackQueueState {
  const existing = queue.items.findIndex((item) => queueStreamKey(item) === queueStreamKey(stream));
  if (existing >= 0) return { ...queue, currentIndex: existing };
  if (queue.items.length === 0) return { ...queue, items: [stream], currentIndex: 0 };
  const insertion = Math.max(0, Math.min(queue.items.length, queue.currentIndex + 1));
  const items = [...queue.items];
  items.splice(insertion, 0, stream);
  return { ...queue, items, currentIndex: insertion };
}

export function enqueue(queue: PlaybackQueueState, stream: LibraryStream): PlaybackQueueState {
  return { ...queue, items: [...queue.items, stream], currentIndex: queue.currentIndex < 0 ? 0 : queue.currentIndex };
}

export function playNext(queue: PlaybackQueueState, stream: LibraryStream): PlaybackQueueState {
  const insertion = Math.max(0, Math.min(queue.items.length, queue.currentIndex + 1));
  const items = [...queue.items];
  items.splice(insertion, 0, stream);
  return { ...queue, items, currentIndex: queue.currentIndex < 0 ? 0 : queue.currentIndex };
}

export function removeQueueItem(queue: PlaybackQueueState, index: number): PlaybackQueueState {
  if (index < 0 || index >= queue.items.length) return queue;
  const items = queue.items.filter((_item, itemIndex) => itemIndex !== index);
  let currentIndex = queue.currentIndex;
  if (items.length === 0) currentIndex = -1;
  else if (index < currentIndex) currentIndex -= 1;
  else if (index === currentIndex) currentIndex = Math.min(currentIndex, items.length - 1);
  return { ...queue, items, currentIndex };
}

export function moveQueueItem(queue: PlaybackQueueState, from: number, to: number): PlaybackQueueState {
  if (from < 0 || from >= queue.items.length || to < 0 || to >= queue.items.length || from === to) return queue;
  const items = [...queue.items];
  const [moved] = items.splice(from, 1);
  items.splice(to, 0, moved);
  let currentIndex = queue.currentIndex;
  if (currentIndex === from) currentIndex = to;
  else if (from < currentIndex && to >= currentIndex) currentIndex -= 1;
  else if (from > currentIndex && to <= currentIndex) currentIndex += 1;
  return { ...queue, items, currentIndex };
}

export function adjacentQueueIndex(
  queue: PlaybackQueueState,
  direction: 'next' | 'previous',
  random: () => number = Math.random,
): number | undefined {
  if (queue.items.length === 0 || queue.currentIndex < 0) return undefined;
  if (direction === 'next' && queue.repeatMode === 'one') return queue.currentIndex;
  if (queue.shuffle && queue.items.length > 1) {
    const offset = 1 + Math.floor(Math.max(0, Math.min(0.999999, random())) * (queue.items.length - 1));
    return (queue.currentIndex + offset) % queue.items.length;
  }
  const candidate = queue.currentIndex + (direction === 'next' ? 1 : -1);
  if (candidate >= 0 && candidate < queue.items.length) return candidate;
  if (queue.repeatMode === 'all') return direction === 'next' ? 0 : queue.items.length - 1;
  return undefined;
}

function isLibraryStream(value: unknown): value is LibraryStream {
  if (!value || typeof value !== 'object') return false;
  const item = value as Partial<LibraryStream>;
  return Number.isInteger(item.serviceId) && typeof item.url === 'string' && item.url.length > 0
    && typeof item.title === 'string' && typeof item.duration === 'number'
    && typeof item.streamType === 'string';
}
