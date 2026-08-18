import * as electron from 'electron';
import { randomUUID } from 'node:crypto';
import path from 'node:path';
import { createRequire } from 'node:module';
const CHANNEL_PREFIX = 'electron-mpv-video:v1';
const channel = (name) => `${CHANNEL_PREFIX}:${name}`;
const IPC_CHANNELS = [
    'player:create',
    'player:open',
    'player:open-media',
    'player:set-audio-track',
    'player:set-subtitle-track',
    'player:play',
    'player:pause',
    'player:stop',
    'player:seek',
    'player:set-volume',
    'player:set-equalizer',
    'player:set-playback-parameters',
    'player:set-render-size',
    'player:set-render-pipeline',
    'player:destroy',
];
const require = createRequire(import.meta.url);
let activeService = null;
function supportsSharedTexturePipeline() {
    return (process.platform === 'darwin' || process.platform === 'win32') && Boolean(electron.sharedTexture);
}
function defaultAddonPath() {
    const packageRoot = path.dirname(require.resolve('electron-mpv-video/package.json'));
    return path.join(packageRoot, 'native/mpv-addon/build/Release/mpv_addon.node');
}
function finiteNumber(value, name) {
    if (typeof value !== 'number' || !Number.isFinite(value)) {
        throw new TypeError(`${name} must be a finite number`);
    }
    return value;
}
function normalizeRenderSize(value) {
    if (!value || typeof value !== 'object') {
        throw new TypeError('renderSize must be an object');
    }
    const size = value;
    return {
        width: Math.max(2, Math.min(3840, Math.floor(finiteNumber(size.width, 'renderSize.width')))),
        height: Math.max(2, Math.min(2160, Math.floor(finiteNumber(size.height, 'renderSize.height')))),
    };
}
function normalizePipeline(value) {
    if (value !== 'software' && value !== 'shared-texture') {
        throw new TypeError(`Unsupported render pipeline: ${String(value)}`);
    }
    return value;
}
function normalizePlayerId(value) {
    if (typeof value !== 'string' || value.length === 0) {
        throw new TypeError('playerId must be a non-empty string');
    }
    return value;
}
function normalizeEqualizerGains(value) {
    if (value === undefined || value === null)
        return undefined;
    if (!Array.isArray(value) || value.length !== 10)
        throw new TypeError('equalizer gains must contain exactly ten bands');
    return value.map((gain, index) => {
        const normalized = finiteNumber(gain, `equalizer gains[${index}]`);
        if (!Number.isInteger(normalized) || normalized < -24 || normalized > 24)
            throw new RangeError(`equalizer gains[${index}] must be an integer from -24 to 24`);
        return normalized;
    });
}
function normalizePlaybackParameter(value, name) {
    const normalized = finiteNumber(value, name);
    if (normalized < 0.1 || normalized > 3)
        throw new RangeError(`${name} must be between 0.1 and 3`);
    return Math.round(normalized * 100) / 100;
}
function normalizeSource(value) {
    if (typeof value !== 'string' || value.trim().length === 0) {
        throw new TypeError('source must be a non-empty string');
    }
    return value;
}
function normalizeHttpSource(value, name = 'source') {
    const source = normalizeSource(value);
    if (source.length > 16384)
        throw new TypeError(`${name} is too long`);
    const parsed = new URL(source);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:')
        throw new TypeError(`${name} must use HTTP or HTTPS`);
    return parsed.toString();
}
function normalizeTrack(value, name) {
    if (value === undefined || value === null)
        return undefined;
    if (typeof value !== 'object')
        throw new TypeError(`${name} must be an object`);
    const track = value;
    const title = typeof track.title === 'string' ? track.title.replace(/[\0\r\n]/g, ' ').slice(0, 200) : '';
    const language = typeof track.language === 'string' ? track.language.replace(/[^A-Za-z0-9_-]/g, '').slice(0, 35) : '';
    return { url: normalizeHttpSource(track.url, `${name}.url`), title, language };
}
function normalizeOptionalText(value, name, maxLength) {
    if (value === undefined || value === null)
        return undefined;
    if (typeof value !== 'string' || value.length === 0 || value.length > maxLength || /[\0\r\n]/.test(value))
        throw new TypeError(`${name} is invalid`);
    return value;
}
function normalizeHttpHeaders(value) {
    if (value === undefined || value === null)
        return undefined;
    if (!Array.isArray(value) || value.length > 16)
        throw new TypeError('httpHeaders must be an array with at most 16 entries');
    return value.map((entry, index) => {
        if (typeof entry !== 'string' || entry.length > 512
            || !/^[!#$%&'*+\-.^_`|~0-9A-Za-z]+:\s*[^\0\r\n]*$/.test(entry))
            throw new TypeError(`httpHeaders[${index}] is invalid`);
        return entry;
    });
}
function normalizeMediaRequest(value) {
    if (!value || typeof value !== 'object')
        throw new TypeError('media request must be an object');
    const request = value;
    return {
        source: normalizeHttpSource(request.source),
        audio: normalizeTrack(request.audio, 'audio'),
        subtitle: normalizeTrack(request.subtitle, 'subtitle'),
        userAgent: normalizeOptionalText(request.userAgent, 'userAgent', 512),
        referrer: request.referrer === undefined ? undefined : normalizeHttpSource(request.referrer, 'referrer'),
        httpHeaders: normalizeHttpHeaders(request.httpHeaders),
    };
}
class PlayerSession {
    window;
    ownerWebContentsId;
    nativeModule;
    onDestroyed;
    id = randomUUID();
    player;
    renderSize;
    pipeline;
    sharedTextureFrameInFlight = false;
    framePumpRunning = false;
    framePumpPending = false;
    framePumpPromise = null;
    eventPumpRunning = false;
    eventPumpPending = false;
    destroyed = false;
    source = null;
    mediaRequest = null;
    currentTime = 0;
    volume = 100;
    equalizerGains = null;
    playbackSpeed = 1;
    playbackPitch = 1;
    skipSilence = false;
    paused = true;
    stopped = true;
    constructor(window, ownerWebContentsId, nativeModule, onDestroyed, options) {
        this.window = window;
        this.ownerWebContentsId = ownerWebContentsId;
        this.nativeModule = nativeModule;
        this.onDestroyed = onDestroyed;
        this.pipeline = options.pipeline;
        if (this.pipeline === 'shared-texture' && !supportsSharedTexturePipeline()) {
            this.pipeline = 'software';
        }
        this.renderSize = options.renderSize;
        this.player = new this.nativeModule.MpvPlayer({ mode: this.pipeline });
        this.startCallbacks();
    }
    belongsTo(sender) {
        return this.ownerWebContentsId === (typeof sender === 'number' ? sender : sender.id);
    }
    open(source) {
        this.assertAlive();
        this.player.open(source);
        this.source = source;
        this.mediaRequest = { source };
        this.currentTime = 0;
        this.stopped = false;
        this.queueFrame();
        this.queueEvents();
    }
    openMedia(request) {
        this.assertAlive();
        this.player.openMedia(request);
        this.source = request.source;
        this.mediaRequest = request;
        this.currentTime = 0;
        this.stopped = false;
        this.queueFrame();
        this.queueEvents();
    }
    setAudioTrack(track) {
        this.assertAlive();
        this.player.setAudioFile(track ?? null);
        if (this.mediaRequest)
            this.mediaRequest = { ...this.mediaRequest, audio: track };
        this.queueEvents();
    }
    setSubtitleTrack(track) {
        this.assertAlive();
        this.player.setSubtitleFile(track ?? null);
        if (this.mediaRequest)
            this.mediaRequest = { ...this.mediaRequest, subtitle: track };
        this.queueEvents();
    }
    play() {
        this.assertAlive();
        this.player.play();
        this.paused = false;
    }
    pause() {
        this.assertAlive();
        this.player.pause();
        this.paused = true;
    }
    stop() {
        this.assertAlive();
        this.player.stop();
        this.currentTime = 0;
        this.paused = true;
        this.stopped = true;
    }
    seek(seconds, exact = true) {
        this.assertAlive();
        const nextTime = Math.max(0, finiteNumber(seconds, 'seconds'));
        this.player.seek(nextTime, exact);
        this.currentTime = nextTime;
    }
    setVolume(value) {
        this.assertAlive();
        const nextVolume = Math.max(0, Math.min(100, finiteNumber(value, 'volume')));
        this.player.setVolume(nextVolume);
        this.volume = nextVolume;
    }
    setEqualizer(gains) {
        this.assertAlive();
        this.player.setEqualizer(gains ?? null);
        this.equalizerGains = gains ? [...gains] : null;
    }
    setPlaybackParameters(speed, pitch, skipSilence) {
        this.assertAlive();
        this.player.setPlaybackParameters(speed, pitch, skipSilence);
        this.playbackSpeed = speed;
        this.playbackPitch = pitch;
        this.skipSilence = skipSilence;
    }
    setRenderSize(size) {
        this.assertAlive();
        this.renderSize = size;
        this.queueFrame();
    }
    async setRenderPipeline(nextPipeline) {
        this.assertAlive();
        if (nextPipeline === 'shared-texture' && !supportsSharedTexturePipeline()) {
            throw new Error(`Shared texture pipeline is unavailable on ${process.platform}`);
        }
        if (this.pipeline === nextPipeline)
            return;
        const previousPlayer = this.player;
        const previousPipeline = this.pipeline;
        this.stopCallbacks();
        await this.waitForFramePump();
        let replacement = null;
        let restoredEvents = [];
        try {
            replacement = new this.nativeModule.MpvPlayer({ mode: nextPipeline });
            replacement.setVolume(this.volume);
            replacement.setEqualizer(this.equalizerGains);
            replacement.setPlaybackParameters(this.playbackSpeed, this.playbackPitch, this.skipSilence);
            if (this.source && !this.stopped) {
                if (this.mediaRequest)
                    replacement.openMedia(this.mediaRequest);
                else
                    replacement.open(this.source);
                restoredEvents = await this.waitForFileLoaded(replacement);
                if (this.currentTime > 0)
                    replacement.seek(this.currentTime);
                if (this.paused)
                    replacement.pause();
                else
                    replacement.play();
            }
            this.player = replacement;
            this.pipeline = nextPipeline;
            for (const event of restoredEvents) {
                const transientProperty = event.type === 'property-change' &&
                    (event.name === 'time-pos' || event.name === 'pause' || event.name === 'eof-reached');
                if (!transientProperty)
                    this.sendEvent(event);
            }
            this.startCallbacks();
        }
        catch (error) {
            replacement?.destroy();
            this.player = previousPlayer;
            this.pipeline = previousPipeline;
            this.startCallbacks();
            throw error;
        }
        previousPlayer.destroy();
    }
    async destroy() {
        if (this.destroyed)
            return;
        this.destroyed = true;
        this.onDestroyed(this.id);
        this.stopCallbacks();
        await this.waitForFramePump();
        this.player.destroy();
    }
    async waitForFileLoaded(player) {
        const events = [];
        const deadline = Date.now() + 30_000;
        while (Date.now() < deadline) {
            const batch = player.pollEvents();
            events.push(...batch);
            const failure = batch.find((event) => event.error);
            if (failure) {
                throw new Error(`Failed to restore source: ${failure.error}`);
            }
            if (batch.some((event) => event.type === 'file-loaded'))
                return events;
            await new Promise((resolve) => setTimeout(resolve, 10));
        }
        throw new Error('Timed out while restoring the media source');
    }
    assertAlive() {
        if (this.destroyed || this.window.isDestroyed()) {
            throw new Error(`Player session is destroyed: ${this.id}`);
        }
    }
    trackEvent(event) {
        if (event.type !== 'property-change')
            return;
        if (event.name === 'time-pos' && typeof event.data === 'number') {
            this.currentTime = event.data;
        }
        else if (event.name === 'pause' && typeof event.data === 'boolean') {
            this.paused = event.data;
        }
        else if (event.name === 'eof-reached' && event.data === true) {
            this.paused = true;
        }
    }
    sendEvent(event) {
        const eventData = event.type === 'log-message' && typeof event.data === 'string'
            ? event.data.replace(/https?:\/\/\S+/gi, '[media URL]').trim().slice(0, 1000)
            : event.data;
        if (event.type === 'log-message' && eventData) {
            console.warn(`[mpv:${event.name ?? event.level ?? 'warn'}] ${eventData}`);
        }
        this.trackEvent(event);
        if (this.window.isDestroyed())
            return;
        this.window.webContents.send(channel('player:event'), {
            playerId: this.id,
            type: event.type,
            name: event.name,
            data: eventData,
            reason: event.reason,
            error: event.error,
            level: event.level,
        });
    }
    sendError(type, error) {
        if (this.window.isDestroyed())
            return;
        this.window.webContents.send(channel('player:event'), {
            playerId: this.id,
            type,
            data: error instanceof Error ? error.message : String(error),
        });
    }
    async renderOneFrame() {
        if (this.destroyed || this.window.isDestroyed())
            return;
        if (this.pipeline === 'shared-texture') {
            if (!electron.sharedTexture) {
                throw new Error('Electron sharedTexture API is not available in this runtime');
            }
            if (this.sharedTextureFrameInFlight)
                return;
            this.sharedTextureFrameInFlight = true;
            let imported;
            try {
                const textureInfo = this.player.renderSharedTexture(this.renderSize.width, this.renderSize.height);
                const referencesReleased = new Promise((resolve) => {
                    imported = electron.sharedTexture.importSharedTexture({
                        textureInfo,
                        allReferencesReleased: () => {
                            this.sharedTextureFrameInFlight = false;
                            resolve();
                        },
                    });
                });
                await electron.sharedTexture.sendSharedTexture({
                    frame: this.window.webContents.mainFrame,
                    importedSharedTexture: imported,
                }, this.id);
                imported.release();
                await referencesReleased;
            }
            catch (error) {
                this.sharedTextureFrameInFlight = false;
                imported?.release();
                throw error;
            }
        }
        else {
            const frame = this.player.renderFrame(this.renderSize.width, this.renderSize.height);
            const rgba = frame.rgba.buffer.slice(frame.rgba.byteOffset, frame.rgba.byteOffset + frame.rgba.byteLength);
            this.window.webContents.send(channel('player:frame'), {
                playerId: this.id,
                width: frame.width,
                height: frame.height,
                rgba,
            }, [rgba]);
        }
    }
    queueFrame() {
        this.framePumpPending = true;
        if (this.framePumpRunning || this.destroyed)
            return;
        this.framePumpRunning = true;
        const pump = (async () => {
            try {
                while (this.framePumpPending && !this.destroyed && !this.window.isDestroyed()) {
                    this.framePumpPending = false;
                    try {
                        await this.renderOneFrame();
                    }
                    catch (error) {
                        this.sharedTextureFrameInFlight = false;
                        this.sendError('render-error', error);
                    }
                }
            }
            finally {
                this.framePumpRunning = false;
                this.framePumpPromise = null;
                if (this.framePumpPending && !this.destroyed && !this.window.isDestroyed()) {
                    this.queueFrame();
                }
            }
        })();
        this.framePumpPromise = pump;
        void pump;
    }
    queueEvents() {
        this.eventPumpPending = true;
        if (this.eventPumpRunning || this.destroyed)
            return;
        this.eventPumpRunning = true;
        queueMicrotask(() => {
            try {
                while (this.eventPumpPending && !this.destroyed && !this.window.isDestroyed()) {
                    this.eventPumpPending = false;
                    try {
                        for (const event of this.player.pollEvents())
                            this.sendEvent(event);
                    }
                    catch (error) {
                        this.sendError('event-error', error);
                    }
                }
            }
            finally {
                this.eventPumpRunning = false;
                if (this.eventPumpPending && !this.destroyed && !this.window.isDestroyed()) {
                    this.queueEvents();
                }
            }
        });
    }
    startCallbacks() {
        this.player.setUpdateCallback(() => this.queueFrame());
        this.player.setEventCallback(() => this.queueEvents());
        this.queueFrame();
        this.queueEvents();
    }
    stopCallbacks() {
        this.framePumpPending = false;
        this.eventPumpPending = false;
        this.player.setUpdateCallback();
        this.player.setEventCallback();
    }
    async waitForFramePump() {
        while (this.framePumpPromise)
            await this.framePumpPromise;
    }
}
class MpvMainService {
    options;
    sessions = new Map();
    windows = new Map();
    nativeModule = null;
    disposed = false;
    constructor(options) {
        this.options = options;
        this.registerIpc();
    }
    attachWindow(window) {
        this.assertActive();
        const id = window.webContents.id;
        if (this.windows.has(id))
            return;
        const onClosed = () => {
            this.windows.delete(id);
            void this.destroyWindowSessions(id);
        };
        this.windows.set(id, { window, onClosed });
        window.once('closed', onClosed);
    }
    async detachWindow(window) {
        const attached = this.windows.get(window.webContents.id);
        if (attached) {
            attached.window.off('closed', attached.onClosed);
            this.windows.delete(window.webContents.id);
        }
        await this.destroyWindowSessions(window);
    }
    async dispose() {
        if (this.disposed)
            return;
        this.disposed = true;
        for (const attached of this.windows.values()) {
            attached.window.off('closed', attached.onClosed);
        }
        this.windows.clear();
        for (const name of IPC_CHANNELS)
            electron.ipcMain.removeHandler(channel(name));
        await Promise.all(Array.from(this.sessions.values(), (session) => session.destroy()));
        if (activeService === this)
            activeService = null;
    }
    assertActive() {
        if (this.disposed)
            throw new Error('MpvMain service has been disposed');
    }
    getNativeModule() {
        if (!this.nativeModule) {
            this.nativeModule = require(this.options.addonPath ?? defaultAddonPath());
        }
        return this.nativeModule;
    }
    resolveOwner(event) {
        this.assertActive();
        const attached = this.windows.get(event.sender.id);
        if (!attached || attached.window.isDestroyed()) {
            throw new Error('The sender BrowserWindow is not attached to electron-mpv-video');
        }
        return attached.window;
    }
    getOwnedSession(event, playerIdValue) {
        this.resolveOwner(event);
        const playerId = normalizePlayerId(playerIdValue);
        const session = this.sessions.get(playerId);
        if (!session)
            throw new Error(`Unknown player session: ${playerId}`);
        if (!session.belongsTo(event.sender)) {
            throw new Error(`Player session does not belong to the sender: ${playerId}`);
        }
        return session;
    }
    async destroyWindowSessions(window) {
        const ownerWebContentsId = typeof window === 'number' ? window : window.webContents.id;
        const owned = Array.from(this.sessions.values()).filter((session) => session.belongsTo(ownerWebContentsId));
        await Promise.all(owned.map((session) => session.destroy()));
    }
    registerIpc() {
        electron.ipcMain.handle(channel('player:create'), async (event, value) => {
            const owner = this.resolveOwner(event);
            const options = value && typeof value === 'object'
                ? value
                : {};
            const session = new PlayerSession(owner, owner.webContents.id, this.getNativeModule(), (id) => this.sessions.delete(id), {
                pipeline: normalizePipeline(options.pipeline ?? 'software'),
                renderSize: normalizeRenderSize(options.renderSize ?? { width: 960, height: 540 }),
            });
            this.sessions.set(session.id, session);
            return session.id;
        });
        electron.ipcMain.handle(channel('player:open'), async (event, id, source) => this.getOwnedSession(event, id).open(normalizeHttpSource(source)));
        electron.ipcMain.handle(channel('player:open-media'), async (event, id, request) => this.getOwnedSession(event, id).openMedia(normalizeMediaRequest(request)));
        electron.ipcMain.handle(channel('player:set-audio-track'), async (event, id, track) => this.getOwnedSession(event, id).setAudioTrack(normalizeTrack(track, 'audio')));
        electron.ipcMain.handle(channel('player:set-subtitle-track'), async (event, id, track) => this.getOwnedSession(event, id).setSubtitleTrack(normalizeTrack(track, 'subtitle')));
        electron.ipcMain.handle(channel('player:play'), async (event, id) => this.getOwnedSession(event, id).play());
        electron.ipcMain.handle(channel('player:pause'), async (event, id) => this.getOwnedSession(event, id).pause());
        electron.ipcMain.handle(channel('player:stop'), async (event, id) => this.getOwnedSession(event, id).stop());
        electron.ipcMain.handle(channel('player:seek'), async (event, id, seconds, exact = true) => {
            if (typeof exact !== 'boolean')
                throw new TypeError('exact must be a boolean');
            this.getOwnedSession(event, id).seek(finiteNumber(seconds, 'seconds'), exact);
        });
        electron.ipcMain.handle(channel('player:set-volume'), async (event, id, value) => this.getOwnedSession(event, id).setVolume(finiteNumber(value, 'volume')));
        electron.ipcMain.handle(channel('player:set-equalizer'), async (event, id, gains) => this.getOwnedSession(event, id).setEqualizer(normalizeEqualizerGains(gains)));
        electron.ipcMain.handle(channel('player:set-playback-parameters'), async (event, id, speed, pitch, skipSilence) => {
            if (typeof skipSilence !== 'boolean')
                throw new TypeError('skipSilence must be a boolean');
            this.getOwnedSession(event, id).setPlaybackParameters(normalizePlaybackParameter(speed, 'speed'), normalizePlaybackParameter(pitch, 'pitch'), skipSilence);
        });
        electron.ipcMain.handle(channel('player:set-render-size'), async (event, id, size) => this.getOwnedSession(event, id).setRenderSize(normalizeRenderSize(size)));
        electron.ipcMain.handle(channel('player:set-render-pipeline'), async (event, id, pipeline) => this.getOwnedSession(event, id).setRenderPipeline(normalizePipeline(pipeline)));
        electron.ipcMain.handle(channel('player:destroy'), async (event, id) => this.getOwnedSession(event, id).destroy());
    }
}
export function createMpvMain(options = {}) {
    if (activeService) {
        throw new Error('Only one electron-mpv-video main service can be active at a time');
    }
    activeService = new MpvMainService(options);
    return activeService;
}
//# sourceMappingURL=mpv-service.js.map
