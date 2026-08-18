import type { PreviewFrameset } from '../shared/contracts';

export interface PreviewFrame {
  url: string;
  left: number;
  top: number;
  width: number;
  height: number;
  pageWidth: number;
  pageHeight: number;
}

export function previewFrameAt(framesets: PreviewFrameset[], positionSeconds: number): PreviewFrame | undefined {
  const positionMillis = Math.max(0, positionSeconds * 1_000);
  const frameset = framesets.find((item) => item.urls.length > 0 && item.durationPerFrame > 0
    && item.frameWidth > 0 && item.frameHeight > 0 && item.framesPerPageX > 0 && item.framesPerPageY > 0);
  if (!frameset) return undefined;
  const frameNumber = Math.min(Math.max(0, frameset.totalCount - 1), Math.floor(positionMillis / frameset.durationPerFrame));
  const perPage = frameset.framesPerPageX * frameset.framesPerPageY;
  const page = Math.min(frameset.urls.length - 1, Math.floor(frameNumber / perPage));
  const relative = frameNumber % perPage;
  const column = relative % frameset.framesPerPageX;
  const row = Math.floor(relative / frameset.framesPerPageX);
  return {
    url: frameset.urls[page], left: column * frameset.frameWidth, top: row * frameset.frameHeight,
    width: frameset.frameWidth, height: frameset.frameHeight,
    pageWidth: frameset.framesPerPageX * frameset.frameWidth,
    pageHeight: frameset.framesPerPageY * frameset.frameHeight,
  };
}
