import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { access } from 'node:fs/promises';
import { app, BrowserWindow, ipcMain, session, shell } from 'electron';
import { createMpvMain, type MpvMain } from 'electron-mpv-video/main';
import { z } from 'zod';
import type { BackendMethod, DownloadKind, DownloadSource, StreamDetails } from '../shared/contracts.js';
import { BackendClient } from './backend-client.js';
import { DownloadManager } from './download-manager.js';
import { embeddedMpvAddonPath, embeddedMpvAvailable } from './embedded-mpv.js';
import { MpvController } from './mpv-controller.js';
import { UpdateManager } from './update-manager.js';

const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
const requireNative = createRequire(import.meta.url);
const backend = new BackendClient();
const player = new MpvController();
let embeddedPlayer: MpvMain | undefined;
let downloads: DownloadManager | undefined;
let updates: UpdateManager | undefined;
let embeddedAddonPath = '';
let shutdownStarted = false;
const backendMethods = new Set<BackendMethod>([
  'health', 'services.list', 'search', 'stream.resolve', 'library.summary',
  'library.subscriptions.list', 'library.subscriptions.save', 'library.subscriptions.delete',
  'library.playlists.list', 'library.playlists.create', 'library.playlists.rename',
  'library.playlists.delete', 'library.playlists.items', 'library.playlists.add-item',
  'library.playlists.delete-item', 'library.history.list', 'library.history.record',
  'library.history.delete', 'library.history.clear', 'library.learning.list',
  'library.search-history.list', 'library.search-history.record',
  'library.search-history.delete', 'library.search-history.clear',
  'library.learning.save', 'library.learning.delete',
  'library.downloads.record',
  'sync.status', 'sync.invitation', 'sync.pair', 'sync.policy.update',
  'sync.runs.list', 'sync.run',
]);
const rpcSchema = z.object({ method: z.string().max(80), params: z.record(z.string(), z.unknown()).optional() });
const syncCategorySchema = z.enum([
  'subscriptions', 'playlists', 'watchHistory', 'searchHistory', 'learningNotes',
  'feedGroups', 'homeTabs', 'channelProfiles', 'filters', 'settings', 'completedDownloads',
]);
const syncPeerIdSchema = z.string().trim().min(1).max(160);
const syncPolicySchema = z.object({
  enabled: z.boolean(),
  intervalMinutes: z.number().int().min(15).max(1440),
  categories: z.array(syncCategorySchema).max(11),
  peerIds: z.array(syncPeerIdSchema).max(32),
});
const syncRunSchema = z.object({
  categories: z.array(syncCategorySchema).max(11).optional(),
  peerIds: z.array(syncPeerIdSchema).max(32).optional(),
});
const syncRunsSchema = z.object({ limit: z.number().int().min(1).max(100).optional() });
const playSchema = z.object({
  url: z.url(),
  title: z.string().max(200).optional(),
  audioUrl: z.url().optional(),
  subtitleUrl: z.url().optional(),
});
const downloadSourceSchema = z.object({
  url: z.url(), kind: z.enum(['video', 'audio', 'caption']).optional(), id: z.string().max(500).optional(),
  format: z.string().max(40).optional(), mimeType: z.string().max(100).optional(),
  deliveryMethod: z.string().max(50).optional(), resolution: z.string().max(50).optional(),
  codec: z.string().max(100).optional(), audioTrackId: z.string().max(500).optional(),
  videoOnly: z.boolean().optional(),
});
const downloadSchema = z.object({
  url: z.url().optional(), sourceUrl: z.url(), title: z.string().trim().min(1).max(200),
  format: z.string().max(40).optional(), mimeType: z.string().max(100).optional(),
  kind: z.enum(['video', 'audio', 'caption']).optional(), video: downloadSourceSchema.optional(),
  audio: downloadSourceSchema.optional(), caption: downloadSourceSchema.optional(),
}).superRefine((value, context) => {
  const legacy = Boolean(value.url && value.kind);
  const composite = Boolean(value.video && value.audio);
  const single = Boolean((value.video && !value.audio) || (!value.video && (value.audio || value.caption)));
  if ([legacy, composite, single].filter(Boolean).length !== 1) {
    context.addIssue({ code: 'custom', message: 'Select exactly one valid download source' });
  }
});
const downloadIdSchema = z.string().uuid();

function createWindow(): BrowserWindow {
  const window = new BrowserWindow({
    title: 'WizeStream Desktop',
    width: 1440,
    height: 900,
    minWidth: 980,
    minHeight: 640,
    show: false,
    webPreferences: {
      preload: path.join(currentDirectory, '../preload/index.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
    },
  });
  embeddedPlayer?.attachWindow(window);
  window.once('ready-to-show', () => window.show());
  window.webContents.once('did-finish-load', () => {
    if (updates) window.webContents.send('updates:changed', updates.state());
  });
  window.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://')) void shell.openExternal(url);
    return { action: 'deny' };
  });
  window.webContents.on('will-navigate', (event, url) => {
    const current = window.webContents.getURL();
    if (url !== current) event.preventDefault();
  });
  const devServer = process.env.VITE_DEV_SERVER_URL;
  if (devServer) void window.loadURL(devServer);
  else void window.loadFile(path.join(currentDirectory, '../../dist-renderer/index.html'));
  return window;
}

app.whenReady().then(async () => {
  session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) => callback(false));
  embeddedAddonPath = embeddedMpvAddonPath(process.resourcesPath, app.getAppPath(), app.isPackaged);
  embeddedPlayer = createMpvMain({ addonPath: embeddedAddonPath });
  downloads = new DownloadManager(
    path.join(app.getPath('downloads'), 'WizeStream'),
    path.join(app.getPath('userData'), 'downloads.json'),
    async (job) => {
      const mediaKind: Record<DownloadKind, string> = { video: 'v', audio: 'a', caption: 's' };
      await backend.invoke('library.downloads.record', {
        syncId: job.id,
        sourceUrl: job.sourceUrl,
        displayName: job.fileName,
        mimeType: job.mimeType,
        sizeBytes: job.bytesDownloaded,
        completedAt: job.completedAt ?? Date.now(),
        mediaKind: mediaKind[job.kind],
      });
    },
    {
      ffmpegPath: mediaToolPath('ffmpeg'),
      ffprobePath: mediaToolPath('ffprobe'),
      refreshSources: async (sourceUrl) => downloadSources(
        await backend.invoke<StreamDetails>('stream.resolve', { url: sourceUrl }),
      ),
    },
  );
  await downloads.initialize();
  downloads.setListener((jobs) => {
    for (const window of BrowserWindow.getAllWindows()) window.webContents.send('downloads:changed', jobs);
  });
  ipcMain.handle('backend:invoke', async (_event, input: unknown) => {
    const { method, params } = rpcSchema.parse(input);
    if (!backendMethods.has(method as BackendMethod)) throw new Error('Backend method is not allowed');
    const validatedParams = method === 'sync.policy.update' ? syncPolicySchema.parse(params)
      : method === 'sync.run' ? syncRunSchema.parse(params ?? {})
        : method === 'sync.runs.list' ? syncRunsSchema.parse(params ?? {})
          : params;
    return backend.invoke(method as BackendMethod, validatedParams);
  });
  ipcMain.handle('player:play', async (_event, input: unknown) => {
    const value = playSchema.parse(input);
    await player.play(value);
  });
  ipcMain.handle('player:stop', () => player.stop());
  ipcMain.handle('player:status', async () => {
    const external = await player.status();
    return {
      embeddedAvailable: await embeddedMpvAvailable(embeddedAddonPath),
      externalAvailable: external.available,
      executable: external.executable,
      running: external.running,
    };
  });
  ipcMain.handle('downloads:list', () => downloads?.list() ?? []);
  ipcMain.handle('downloads:start', (_event, input: unknown) => downloads?.start(downloadSchema.parse(input)));
  ipcMain.handle('downloads:pause', (_event, input: unknown) => downloads?.pause(downloadIdSchema.parse(input)));
  ipcMain.handle('downloads:resume', (_event, input: unknown) => downloads?.resume(downloadIdSchema.parse(input)));
  ipcMain.handle('downloads:cancel', (_event, input: unknown) => downloads?.cancel(downloadIdSchema.parse(input)));
  ipcMain.handle('downloads:show', (_event, input: unknown) => {
    const filePath = downloads?.completedPath(downloadIdSchema.parse(input));
    if (filePath) shell.showItemInFolder(filePath);
  });
  ipcMain.handle('downloads:open-folder', () => shell.openPath(path.join(app.getPath('downloads'), 'WizeStream')));
  updates = await createUpdateManager();
  updates.initialize();
  ipcMain.handle('updates:state', () => updates?.state());
  ipcMain.handle('updates:check', () => updates?.check());
  ipcMain.handle('updates:download', () => updates?.download());
  ipcMain.handle('updates:install', async () => {
    if (updates?.state().status !== 'downloaded') return;
    await cleanupApplication();
    updates.install();
  });
  await backend.start();
  if (process.env.WIZESTREAM_PACKAGE_SMOKE === '1') {
    if (!await embeddedMpvAvailable(embeddedAddonPath)) throw new Error('Packaged embedded libmpv is unavailable');
    process.env.MPV_AO ??= 'null';
    const native = requireNative(embeddedAddonPath) as {
      MpvPlayer: new (options: { mode: 'software' }) => { destroy(): void };
    };
    const smokePlayer = new native.MpvPlayer({ mode: 'software' });
    smokePlayer.destroy();
    await access(mediaToolPath('ffmpeg'));
    await access(mediaToolPath('ffprobe'));
    console.log(`WIZESTREAM_PACKAGE_SMOKE_OK ${process.platform}-${process.arch}`);
    await shutdownApplication(0);
    return;
  }
  createWindow();
  setTimeout(() => { void updates?.check(); }, 10_000);
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
}).catch((error: unknown) => {
  console.error(error);
  void shutdownApplication(1);
});

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
app.on('before-quit', (event) => {
  if (shutdownStarted) return;
  event.preventDefault();
  void shutdownApplication(0);
});

async function shutdownApplication(exitCode: number): Promise<void> {
  const cleanupFailed = await cleanupApplication();
  if (cleanupFailed) exitCode = 1;
  if (process.platform === 'linux') {
    try {
      const native = requireNative(embeddedAddonPath) as { exitProcess(code: number): never };
      native.exitProcess(exitCode);
    } catch (error) {
      console.error('Immediate Linux process exit is unavailable', error);
    }
  }
  app.exit(exitCode);
}

async function cleanupApplication(): Promise<boolean> {
  if (shutdownStarted) return false;
  shutdownStarted = true;
  const results = await Promise.allSettled([embeddedPlayer?.dispose(), player.stop(), backend.stop()]);
  const failed = results.find((result) => result.status === 'rejected');
  if (failed?.status === 'rejected') {
    console.error('Desktop shutdown cleanup failed', failed.reason);
    return true;
  }
  return false;
}

function mediaToolPath(tool: 'ffmpeg' | 'ffprobe'): string {
  const configured = process.env[`WIZESTREAM_${tool.toUpperCase()}_PATH`];
  if (configured) return configured;
  const root = app.isPackaged ? process.resourcesPath : app.getAppPath();
  return path.join(root, 'native', 'media-tools', `${tool}${process.platform === 'win32' ? '.exe' : ''}`);
}

async function createUpdateManager(): Promise<UpdateManager> {
  const enabled = app.isPackaged && process.env.WIZESTREAM_PACKAGE_SMOKE !== '1';
  const updater = enabled ? (await import('electron-updater')).autoUpdater : undefined;
  return new UpdateManager(updater, app.getVersion(), (state) => {
    for (const window of BrowserWindow.getAllWindows()) window.webContents.send('updates:changed', state);
  });
}

function downloadSources(details: StreamDetails): DownloadSource[] {
  return [
    ...details.videoStreams.map((stream) => ({ ...stream, kind: 'video' as const })),
    ...details.audioStreams.map((stream) => ({ ...stream, kind: 'audio' as const })),
    ...details.subtitles.map((stream) => ({ ...stream, kind: 'caption' as const })),
  ];
}
