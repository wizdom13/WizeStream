import { describe, expect, it } from 'vitest';
import type { SubscriptionFeed } from '../shared/contracts';
import { loadSubscriptionFeedCache, saveSubscriptionFeedCache } from './feed-cache';

class MemoryStorage {
  private readonly values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  removeItem(key: string) { this.values.delete(key); }
}

const feed: SubscriptionFeed = {
  items: [{ type: 'STREAM', serviceId: 0, url: 'https://video.example/1', name: 'Cached video' }],
  totalChannels: 1,
  failedChannels: 0,
  refreshedAt: Date.UTC(2026, 7, 16),
};

describe("What's New persistent cache", () => {
  it('restores cached cards before a network refresh', () => {
    const storage = new MemoryStorage();
    saveSubscriptionFeedCache(storage, feed);
    expect(loadSubscriptionFeedCache(storage)).toEqual(feed);
  });

  it('discards malformed cache data safely', () => {
    const storage = new MemoryStorage();
    storage.setItem('wizestream.desktop.subscription-feed.v1', '{"items":"broken"}');
    expect(loadSubscriptionFeedCache(storage)).toBeUndefined();
    expect(storage.getItem('wizestream.desktop.subscription-feed.v1')).toBeNull();
  });
});
