import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { app, BrowserWindow, ipcMain, session, shell } from 'electron';
import { z } from 'zod';
import type { BackendMethod } from '../shared/contracts.js';
import { BackendClient } from './backend-client.js';
import { MpvController } from './mpv-controller.js';

const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
const backend = new BackendClient();
const player = new MpvController();
const backendMethods = new Set<BackendMethod>([
  'health', 'services.list', 'search', 'stream.resolve', 'library.summary',
  'sync.status', 'sync.invitation', 'sync.pair',
]);
const rpcSchema = z.object({ method: z.string().max(80), params: z.record(z.string(), z.unknown()).optional() });
const playSchema = z.object({ url: z.url(), title: z.string().max(200).optional() });

function createWindow(): BrowserWindow {
  const window = new BrowserWindow({
    title: 'WizeStream Desktop',
    width: 1440,
    height: 900,
    minWidth: 980,
    minHeight: 640,
    show: false,
    webPreferences: {
      preload: path.join(currentDirectory, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
    },
  });
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
  ipcMain.handle('backend:invoke', async (_event, input: unknown) => {
    const { method, params } = rpcSchema.parse(input);
    if (!backendMethods.has(method as BackendMethod)) throw new Error('Backend method is not allowed');
    return backend.invoke(method as BackendMethod, params);
  });
  ipcMain.handle('player:play', async (_event, input: unknown) => {
    const value = playSchema.parse(input);
    await player.play(value.url, value.title);
  });
  ipcMain.handle('player:stop', () => player.stop());
  ipcMain.handle('player:status', () => player.status());
  await backend.start();
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
}).catch((error: unknown) => {
  console.error(error);
  app.quit();
});

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
app.on('before-quit', () => { void player.stop(); void backend.stop(); });
