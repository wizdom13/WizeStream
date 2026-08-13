import type { MediaRequest, MediaTrack, RenderMode } from '../shared/types.js';
export declare class MpvVideoElement extends HTMLElement {
    static observedAttributes: string[];
    private readonly surfaceRoot;
    private player;
    private canvas2d;
    private webglCanvas;
    private sharedCanvas;
    private canvasRenderer;
    private webglRenderer;
    private sharedRenderer;
    private activeRenderer;
    private activeSharedRenderer;
    private resizeObserver;
    private disposers;
    private ready;
    private destroying;
    private renderMode;
    private status;
    private openedSource;
    private state;
    get currentTime(): number;
    get duration(): number;
    get videoWidth(): number;
    get videoHeight(): number;
    get rendererName(): string;
    get playerId(): string;
    get src(): string;
    set src(value: string);
    get loop(): boolean;
    set loop(value: boolean);
    get volume(): number;
    set volume(value: number);
    get mode(): RenderMode;
    connectedCallback(): void;
    disconnectedCallback(): void;
    attributeChangedCallback(name: string, oldValue: string | null, newValue: string | null): void;
    open(filePath: string): Promise<void>;
    openMedia(request: MediaRequest): Promise<void>;
    setAudioTrack(track?: MediaTrack): Promise<void>;
    setSubtitleTrack(track?: MediaTrack): Promise<void>;
    play(): Promise<void>;
    pause(): Promise<void>;
    stop(): Promise<void>;
    seek(seconds: number): Promise<void>;
    setVolume(value: number): Promise<void>;
    setRenderMode(mode: RenderMode): Promise<void>;
    destroy(): Promise<void>;
    private destroyInternal;
    private startInitialize;
    private initialize;
    private attachSurfaces;
    private ensureReady;
    private resolveRenderMode;
    private updateVisibleSurface;
    private updateRenderSize;
    private handlePlayerEvent;
    private dispatchState;
    private emitError;
}
declare global {
    interface HTMLElementTagNameMap {
        'mpv-video': MpvVideoElement;
    }
}
//# sourceMappingURL=mpv-video.d.ts.map
