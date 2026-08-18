export type RenderMode = 'shared-texture' | 'webgl' | 'canvas2d';
export type RenderPipeline = 'shared-texture' | 'software';
export type PlayerFrame = {
    playerId: string;
    width: number;
    height: number;
    rgba: ArrayBuffer;
};
export type PlayerEvent = {
    playerId: string;
    type: string;
    name?: string;
    data?: unknown;
    reason?: string;
    error?: string;
    level?: string;
};
export type PlayerCreateOptions = {
    renderMode?: RenderMode;
    width?: number;
    height?: number;
};
export type MediaTrack = {
    url: string;
    title?: string;
    language?: string;
    userAgent?: string;
    referrer?: string;
    httpHeaders?: string[];
};
export type MediaRequest = {
    source: string;
    audio?: MediaTrack;
    subtitle?: MediaTrack;
    userAgent?: string;
    referrer?: string;
    httpHeaders?: string[];
};
export type Dispose = () => void;
export type MpvPlayerSession = {
    readonly id: string;
    open(source: string): Promise<void>;
    openMedia(request: MediaRequest): Promise<void>;
    setAudioTrack(track?: MediaTrack): Promise<void>;
    setSubtitleTrack(track?: MediaTrack): Promise<void>;
    play(): Promise<void>;
    pause(): Promise<void>;
    stop(): Promise<void>;
    seek(seconds: number, exact?: boolean): Promise<void>;
    setVolume(value: number): Promise<void>;
    setEqualizer(gains?: number[]): Promise<void>;
    setPlaybackParameters(speed: number, pitch: number, skipSilence: boolean): Promise<void>;
    setRenderSize(width: number, height: number): Promise<void>;
    setRenderMode(mode: RenderMode): Promise<void>;
    destroy(): Promise<void>;
    onFrame(callback: (frame: PlayerFrame) => void): Dispose;
    onSharedTextureFrame(callback: (frame: VideoFrame) => Promise<void> | void): Dispose;
    onEvent(callback: (event: PlayerEvent) => void): Dispose;
};
export type ElectronMpvVideoApi = {
    readonly platform: string;
    readonly supportsSharedTexture: boolean;
    create(options?: PlayerCreateOptions): Promise<MpvPlayerSession>;
};
export type MpvVideoState = {
    playerId: string;
    status: string;
    renderMode: RenderMode;
    rendererName: string;
    time: number;
    duration: number;
    width: number;
    height: number;
    codec: string;
    fps: number;
    audioTrack: string;
    subtitleTrack: string;
};
declare global {
    interface Window {
        _electronMpvVideo: ElectronMpvVideoApi;
    }
}
//# sourceMappingURL=types.d.ts.map
