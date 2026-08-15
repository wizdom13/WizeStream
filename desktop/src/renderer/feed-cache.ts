import type { SearchItem, SubscriptionFeed } from '../shared/contracts';

const FEED_CACHE_KEY = 'wizestream.desktop.subscription-feed.v1';
const MAX_CACHED_ITEMS = 600;

type CacheStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

export function loadSubscriptionFeedCache(storage: CacheStorage): SubscriptionFeed | undefined {
  try {
    const raw = storage.getItem(FEED_CACHE_KEY);
    if (!raw) return undefined;
    const parsed = JSON.parse(raw) as unknown;
    if (!isSubscriptionFeed(parsed)) throw new Error('Invalid feed cache');
    return { ...parsed, items: parsed.items.slice(0, MAX_CACHED_ITEMS) };
  } catch {
    storage.removeItem(FEED_CACHE_KEY);
    return undefined;
  }
}

export function saveSubscriptionFeedCache(storage: CacheStorage, feed: SubscriptionFeed): void {
  try {
    storage.setItem(FEED_CACHE_KEY, JSON.stringify({
      ...feed,
      items: feed.items.slice(0, MAX_CACHED_ITEMS),
    }));
  } catch {
    // A full or unavailable browser cache must not stop the feed from loading.
  }
}

function isSubscriptionFeed(value: unknown): value is SubscriptionFeed {
  if (!value || typeof value !== 'object') return false;
  const feed = value as Partial<SubscriptionFeed>;
  return Array.isArray(feed.items)
    && feed.items.every(isSearchItem)
    && isNonNegativeInteger(feed.totalChannels)
    && isNonNegativeInteger(feed.failedChannels)
    && typeof feed.refreshedAt === 'number'
    && Number.isFinite(feed.refreshedAt)
    && feed.refreshedAt >= 0;
}

function isSearchItem(value: unknown): value is SearchItem {
  if (!value || typeof value !== 'object') return false;
  const item = value as Partial<SearchItem>;
  return typeof item.type === 'string'
    && Number.isInteger(item.serviceId)
    && typeof item.url === 'string'
    && item.url.length > 0
    && typeof item.name === 'string';
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}
