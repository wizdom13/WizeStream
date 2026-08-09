import { contextBridge, ipcRenderer } from 'electron';
import type { BackendMethod, DesktopApi } from '../shared/contracts.js';

const api: DesktopApi = {
  backend: {
    invoke: <T,>(method: BackendMethod, params?: Record<string, unknown>) =>
      ipcRenderer.invoke('backend:invoke', { method, params }) as Promise<T>,
  },
  player: {
    play: (url, title) => ipcRenderer.invoke('player:play', { url, title }) as Promise<void>,
    stop: () => ipcRenderer.invoke('player:stop') as Promise<void>,
    status: () => ipcRenderer.invoke('player:status') as Promise<{ available: boolean; executable?: string; running: boolean }>,
  },
};

contextBridge.exposeInMainWorld('wizestream', api);
