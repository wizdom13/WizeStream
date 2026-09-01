package org.schabi.newpipe.local.playlist;

/** Resolves gesture ownership for local playlists shown inside or outside the main pager. */
public final class LocalPlaylistSwipePolicy {
    private LocalPlaylistSwipePolicy() {
    }

    /**
     * @param useAsFrontPage whether the playlist is hosted inside the main tab pager
     * @return whether horizontal item-removal swipes may consume the pager gesture
     */
    public static boolean isItemRemovalSwipeEnabled(final boolean useAsFrontPage) {
        return !useAsFrontPage;
    }
}
