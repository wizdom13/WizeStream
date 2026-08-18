import { describe, expect, it } from 'vitest';
import type { LibraryStream } from '../shared/contracts';
import {
  adjacentQueueIndex, emptyPlaybackQueue, enqueue, loadPlaybackQueue, moveQueueItem,
  playbackQueueStorageKey, playNext, playNow, removeQueueItem,
} from './playback-queue';

const stream = (id: number): LibraryStream => ({
  serviceId: 0, url: `https://example.com/${id}`, title: `Video ${id}`,
  duration: 60, streamType: 'VIDEO_STREAM',
});

describe('playback queue', () => {
  it('persists only valid bounded queue data', () => {
    const storage = { getItem: (key: string) => key === playbackQueueStorageKey
      ? JSON.stringify({ items: [stream(1), { bad: true }], currentIndex: 8, repeatMode: 'all', shuffle: true }) : null };
    expect(loadPlaybackQueue(storage)).toEqual({ items: [stream(1)], currentIndex: 0, repeatMode: 'all', shuffle: true });
  });

  it('inserts play-now and play-next entries after the current item', () => {
    const queue = enqueue(enqueue(emptyPlaybackQueue, stream(1)), stream(3));
    expect(playNow(queue, stream(2))).toMatchObject({ items: [stream(1), stream(2), stream(3)], currentIndex: 1 });
    expect(playNext(queue, stream(2))).toMatchObject({ items: [stream(1), stream(2), stream(3)], currentIndex: 0 });
  });

  it('keeps the current item stable while moving and removing entries', () => {
    const queue = { items: [stream(1), stream(2), stream(3)], currentIndex: 1, repeatMode: 'off' as const, shuffle: false };
    expect(moveQueueItem(queue, 0, 2).currentIndex).toBe(0);
    expect(removeQueueItem(queue, 0).currentIndex).toBe(0);
    expect(removeQueueItem(queue, 1).currentIndex).toBe(1);
  });

  it('supports repeat-one, repeat-all and deterministic shuffle', () => {
    const base = { items: [stream(1), stream(2), stream(3)], currentIndex: 2, repeatMode: 'off' as const, shuffle: false };
    expect(adjacentQueueIndex(base, 'next')).toBeUndefined();
    expect(adjacentQueueIndex({ ...base, repeatMode: 'one' }, 'next')).toBe(2);
    expect(adjacentQueueIndex({ ...base, repeatMode: 'all' }, 'next')).toBe(0);
    expect(adjacentQueueIndex({ ...base, shuffle: true }, 'next', () => 0)).toBe(0);
  });
});
