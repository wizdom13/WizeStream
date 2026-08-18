import type { BrowserWindow } from 'electron';
export type MpvMainOptions = {
    addonPath?: string;
    tlsCaFile?: string;
};
export type MpvMain = {
    attachWindow(window: BrowserWindow): void;
    detachWindow(window: BrowserWindow): Promise<void>;
    dispose(): Promise<void>;
};
export declare function createMpvMain(options?: MpvMainOptions): MpvMain;
//# sourceMappingURL=mpv-service.d.ts.map
