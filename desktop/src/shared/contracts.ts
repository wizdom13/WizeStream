export type BackendMethod =
  | 'health'
  | 'services.list'
  | 'search'
  | 'stream.resolve'
  | 'library.summary'
  | 'sync.status'
  | 'sync.invitation'
  | 'sync.pair';

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
