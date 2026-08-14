import type { DetailedHTMLProps, HTMLAttributes, Ref } from 'react';
import type { MpvVideoElement } from 'electron-mpv-video/renderer';

declare module 'react' {
  namespace JSX {
    interface IntrinsicElements {
      'mpv-video': DetailedHTMLProps<HTMLAttributes<MpvVideoElement>, MpvVideoElement> & {
        ref?: Ref<MpvVideoElement>;
        'render-mode'?: 'shared-texture' | 'webgl' | 'canvas2d';
        volume?: string;
      };
    }
  }
}
