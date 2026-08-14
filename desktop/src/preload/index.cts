import { contextBridge, ipcRenderer } from 'electron';
import { exposeMpvApi } from 'electron-mpv-video/preload';
import type { BackendMethod, DesktopApi, DownloadJob, UpdateState } from '../shared/contracts.js';

exposeMpvApi();

const api: DesktopApi = {
  backend: {
    invoke: <T,>(method: BackendMethod, params?: Record<string, unknown>) =>
      ipcRenderer.invoke('backend:invoke', { method, params }) as Promise<T>,
  },
  player: {
    play: (request) => ipcRenderer.invoke('player:play', request) as Promise<void>,
    stop: () => ipcRenderer.invoke('player:stop') as Promise<void>,
    status: () => ipcRenderer.invoke('player:status') as ReturnType<DesktopApi['player']['status']>,
  },
  downloads: {
    list: () => ipcRenderer.invoke('downloads:list') as ReturnType<DesktopApi['downloads']['list']>,
    start: (request) => ipcRenderer.invoke('downloads:start', request) as ReturnType<DesktopApi['downloads']['start']>,
    pause: (id) => ipcRenderer.invoke('downloads:pause', id) as Promise<void>,
    resume: (id) => ipcRenderer.invoke('downloads:resume', id) as Promise<void>,
    cancel: (id) => ipcRenderer.invoke('downloads:cancel', id) as Promise<void>,
    show: (id) => ipcRenderer.invoke('downloads:show', id) as Promise<void>,
    openFolder: () => ipcRenderer.invoke('downloads:open-folder') as Promise<void>,
    onChanged: (listener) => {
      const wrapped = (_event: Electron.IpcRendererEvent, jobs: DownloadJob[]) => listener(jobs);
      ipcRenderer.on('downloads:changed', wrapped);
      return () => ipcRenderer.off('downloads:changed', wrapped);
    },
  },
  updates: {
    state: () => ipcRenderer.invoke('updates:state') as Promise<UpdateState>,
    check: () => ipcRenderer.invoke('updates:check') as Promise<UpdateState>,
    download: () => ipcRenderer.invoke('updates:download') as Promise<UpdateState>,
    install: () => ipcRenderer.invoke('updates:install') as Promise<void>,
    onChanged: (listener) => {
      const wrapped = (_event: Electron.IpcRendererEvent, state: UpdateState) => listener(state);
      ipcRenderer.on('updates:changed', wrapped);
      return () => ipcRenderer.off('updates:changed', wrapped);
    },
  },
};

contextBridge.exposeInMainWorld('wizestream', api);
