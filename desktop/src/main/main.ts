import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { app, BrowserWindow, ipcMain, session, shell } from 'electron';
import { createMpvMain, type MpvMain } from 'electron-mpv-video/main';
import { z } from 'zod';
import type { BackendMethod, DownloadKind } from '../shared/contracts.js';
import { BackendClient } from './backend-client.js';
import { DownloadManager } from './download-manager.js';
import { embeddedMpvAddonPath, embeddedMpvAvailable } from './embedded-mpv.js';
import { MpvController } from './mpv-controller.js';

const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
const backend = new BackendClient();
const player = new MpvController();
let embeddedPlayer: MpvMain | undefined;
let downloads: DownloadManager | undefined;
let embeddedAddonPath = '';
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
  'sync.status', 'sync.invitation', 'sync.pair', 'sync.run',
]);
const rpcSchema = z.object({ method: z.string().max(80), params: z.record(z.string(), z.unknown()).optional() });
const playSchema = z.object({
  url: z.url(),
  title: z.string().max(200).optional(),
  audioUrl: z.url().optional(),
  subtitleUrl: z.url().optional(),
});
const downloadSchema = z.object({
  url: z.url(), sourceUrl: z.url(), title: z.string().trim().min(1).max(200), format: z.string().max(40).optional(),
  mimeType: z.string().max(100).optional(), kind: z.enum(['video', 'audio', 'caption']),
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
        sourceUrl: job.sourceUrl,
        displayName: job.fileName,
        mimeType: job.mimeType,
        sizeBytes: job.bytesDownloaded,
        completedAt: job.completedAt ?? Date.now(),
        mediaKind: mediaKind[job.kind],
      });
    },
  );
  await downloads.initialize();
  downloads.setListener((jobs) => {
    for (const window of BrowserWindow.getAllWindows()) window.webContents.send('downloads:changed', jobs);
  });
  ipcMain.handle('backend:invoke', async (_event, input: unknown) => {
    const { method, params } = rpcSchema.parse(input);
    if (!backendMethods.has(method as BackendMethod)) throw new Error('Backend method is not allowed');
    return backend.invoke(method as BackendMethod, params);
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
  await backend.start();
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
}).catch((error: unknown) => {
  console.error(error);
  app.quit();
});

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
app.on('before-quit', () => { void embeddedPlayer?.dispose(); void player.stop(); void backend.stop(); });
