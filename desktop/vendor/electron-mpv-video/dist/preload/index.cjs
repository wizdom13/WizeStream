"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.exposeMpvApi = exposeMpvApi;
const electron = __importStar(require("electron"));
const CHANNEL_PREFIX = 'electron-mpv-video:v1';
const channel = (name) => `${CHANNEL_PREFIX}:${name}`;
const sharedTextureCallbacks = new Map();
const supportsSharedTexture = (process.platform === 'darwin' || process.platform === 'win32') && Boolean(electron.sharedTexture);
let sharedTextureReceiverRegistered = false;
let apiExposed = false;
function pipelineForRenderMode(mode) {
    if (!mode)
        return supportsSharedTexture ? 'shared-texture' : 'software';
    return mode === 'shared-texture' ? 'shared-texture' : 'software';
}
function registerSharedTextureReceiver() {
    if (sharedTextureReceiverRegistered || !electron.sharedTexture)
        return;
    electron.sharedTexture.setSharedTextureReceiver(async ({ importedSharedTexture }, playerId) => {
        const frame = importedSharedTexture.getVideoFrame();
        try {
            if (!playerId)
                return;
            const callbacks = sharedTextureCallbacks.get(playerId);
            if (!callbacks)
                return;
            for (const callback of callbacks) {
                await callback(frame);
            }
        }
        finally {
            frame.close();
            importedSharedTexture.release();
        }
    });
    sharedTextureReceiverRegistered = true;
}
function createPlayerSession(id) {
    const disposers = new Set();
    let destroyed = false;
    const disposeAll = () => {
        for (const dispose of disposers)
            dispose();
        disposers.clear();
    };
    const session = {
        id,
        open: (source) => electron.ipcRenderer.invoke(channel('player:open'), id, source),
        openMedia: (request) => electron.ipcRenderer.invoke(channel('player:open-media'), id, request),
        setAudioTrack: (track) => electron.ipcRenderer.invoke(channel('player:set-audio-track'), id, track),
        setSubtitleTrack: (track) => electron.ipcRenderer.invoke(channel('player:set-subtitle-track'), id, track),
        play: () => electron.ipcRenderer.invoke(channel('player:play'), id),
        pause: () => electron.ipcRenderer.invoke(channel('player:pause'), id),
        stop: () => electron.ipcRenderer.invoke(channel('player:stop'), id),
        seek: (seconds, exact = true) => electron.ipcRenderer.invoke(channel('player:seek'), id, seconds, exact),
        setVolume: (value) => electron.ipcRenderer.invoke(channel('player:set-volume'), id, value),
        setEqualizer: (gains) => electron.ipcRenderer.invoke(channel('player:set-equalizer'), id, gains),
        setPlaybackParameters: (speed, pitch, skipSilence) => electron.ipcRenderer.invoke(channel('player:set-playback-parameters'), id, speed, pitch, skipSilence),
        setRenderSize: (width, height) => electron.ipcRenderer.invoke(channel('player:set-render-size'), id, { width, height }),
        setRenderMode: (mode) => electron.ipcRenderer.invoke(channel('player:set-render-pipeline'), id, pipelineForRenderMode(mode)),
        destroy: async () => {
            if (destroyed)
                return;
            destroyed = true;
            disposeAll();
            await electron.ipcRenderer.invoke(channel('player:destroy'), id);
        },
        onFrame: (callback) => {
            const listener = (_event, frame) => {
                if (frame.playerId === id)
                    callback(frame);
            };
            electron.ipcRenderer.on(channel('player:frame'), listener);
            const dispose = () => electron.ipcRenderer.off(channel('player:frame'), listener);
            disposers.add(dispose);
            return () => {
                disposers.delete(dispose);
                dispose();
            };
        },
        onSharedTextureFrame: (callback) => {
            if (!electron.sharedTexture) {
                throw new Error('Electron sharedTexture API is not available');
            }
            let callbacks = sharedTextureCallbacks.get(id);
            if (!callbacks) {
                callbacks = new Set();
                sharedTextureCallbacks.set(id, callbacks);
            }
            callbacks.add(callback);
            const dispose = () => {
                callbacks?.delete(callback);
                if (callbacks?.size === 0)
                    sharedTextureCallbacks.delete(id);
            };
            disposers.add(dispose);
            return () => {
                disposers.delete(dispose);
                dispose();
            };
        },
        onEvent: (callback) => {
            const listener = (_event, event) => {
                if (event.playerId === id)
                    callback(event);
            };
            electron.ipcRenderer.on(channel('player:event'), listener);
            const dispose = () => electron.ipcRenderer.off(channel('player:event'), listener);
            disposers.add(dispose);
            return () => {
                disposers.delete(dispose);
                dispose();
            };
        },
    };
    return session;
}
const mpvApi = {
    platform: process.platform,
    supportsSharedTexture,
    create: async (options) => {
        const id = await electron.ipcRenderer.invoke(channel('player:create'), {
            pipeline: pipelineForRenderMode(options?.renderMode),
            renderSize: {
                width: options?.width ?? 960,
                height: options?.height ?? 540,
            },
        });
        return createPlayerSession(id);
    },
};
function exposeMpvApi() {
    if (apiExposed)
        return;
    registerSharedTextureReceiver();
    electron.contextBridge.exposeInMainWorld('_electronMpvVideo', mpvApi);
    apiExposed = true;
}
//# sourceMappingURL=index.cjs.map
