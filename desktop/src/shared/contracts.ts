export type BackendMethod =
  | 'health'
  | 'services.list'
  | 'search'
  | 'stream.resolve'
  | 'library.summary'
  | 'library.subscriptions.list'
  | 'library.subscriptions.save'
  | 'library.subscriptions.delete'
  | 'library.playlists.list'
  | 'library.playlists.create'
  | 'library.playlists.rename'
  | 'library.playlists.delete'
  | 'library.playlists.items'
  | 'library.playlists.add-item'
  | 'library.playlists.delete-item'
  | 'library.history.list'
  | 'library.history.record'
  | 'library.history.delete'
  | 'library.history.clear'
  | 'library.search-history.list'
  | 'library.search-history.record'
  | 'library.search-history.delete'
  | 'library.search-history.clear'
  | 'library.learning.list'
  | 'library.learning.save'
  | 'library.learning.delete'
  | 'sync.status'
  | 'sync.invitation'
  | 'sync.pair'
  | 'sync.run';

export interface ServiceSummary {
  id: number;
  name: string;
  capabilities: string[];
}

export interface SearchItem {
  type: string;
  serviceId: number;
  url: string;
  name: string;
  thumbnailUrl?: string;
  uploaderName?: string;
  duration?: number;
}

export interface StreamVariant {
  url: string;
  format?: string;
  resolution?: string;
  bitrate?: number;
  videoOnly?: boolean;
}

export interface StreamDetails {
  serviceId: number;
  url: string;
  name: string;
  uploaderName?: string;
  thumbnailUrl?: string;
  duration: number;
  streamType: string;
  dashMpdUrl?: string;
  hlsUrl?: string;
  videoStreams: StreamVariant[];
  audioStreams: StreamVariant[];
}

export interface LibraryStream {
  serviceId: number;
  url: string;
  title: string;
  duration: number;
  streamType: string;
  uploader?: string;
  uploaderUrl?: string;
  thumbnailUrl?: string;
}

export interface SubscriptionItem {
  serviceId: number;
  url: string;
  name: string;
  avatarUrl?: string;
  subscriberCount?: number;
  description?: string;
  youtubeModeMask?: number;
}

export interface PlaylistSummary {
  id: string;
  name: string;
  thumbnailUrl?: string;
  displayIndex: number;
  itemCount: number;
}

export interface PlaylistItem extends LibraryStream {
  itemId: string;
  position: number;
}

export interface HistoryItem extends LibraryStream {
  id: string;
  watchedAt: number;
  positionSeconds: number;
}

export interface SearchHistoryItem {
  id: string;
  serviceId: number;
  query: string;
  searchedAt: number;
}

export interface LearningNote extends LibraryStream {
  id: string;
  positionSeconds: number;
  note: string;
  createdAt: number;
  updatedAt: number;
}

export interface SyncStatus {
  protocol: string;
  peerId: string;
  listenAddresses: string[];
  trustedPeers: Array<{
    peerId: string;
    deviceName: string;
    lastSyncAtEpochMillis?: number;
    lastSyncError?: string;
  }>;
  dataSyncEnabled: boolean;
  automaticLanDiscovery: boolean;
  categories: string[];
}

export interface SyncRunResult {
  requestedCategories: string[];
  succeeded: number;
  failed: number;
  peers: Array<{
    peerId: string;
    deviceName: string;
    error?: string;
    results: Record<string, { sent?: number; received?: number; changed?: number; rounds?: number; error?: string }>;
  }>;
}

export interface DesktopApi {
  backend: {
    invoke<T>(method: BackendMethod, params?: Record<string, unknown>): Promise<T>;
  };
  player: {
    play(url: string, title?: string): Promise<void>;
    stop(): Promise<void>;
    status(): Promise<{ available: boolean; executable?: string; running: boolean }>;
  };
}

declare global {
  interface Window {
    wizestream: DesktopApi;
  }
}
