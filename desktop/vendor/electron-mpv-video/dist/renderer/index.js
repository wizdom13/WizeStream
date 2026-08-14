import { MpvVideoElement } from './mpv-video.js';
export { MpvVideoElement } from './mpv-video.js';
export function defineMpvVideoElement() {
    if (!customElements.get('mpv-video')) {
        customElements.define('mpv-video', MpvVideoElement);
    }
    return MpvVideoElement;
}
//# sourceMappingURL=index.js.map