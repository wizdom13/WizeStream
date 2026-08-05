package org.schabi.newpipe.player;

import androidx.annotation.Nullable;

/** Remembers the main-player fullscreen state while the popup UI is active. */
final class PopupPlayerReturnState {
    @Nullable
    private Boolean fullscreen;

    void remember(final boolean wasFullscreen) {
        fullscreen = wasFullscreen;
    }

    boolean isRemembered() {
        return fullscreen != null;
    }

    boolean consume(final boolean fallback) {
        final boolean result = fullscreen != null ? fullscreen : fallback;
        fullscreen = null;
        return result;
    }
}
