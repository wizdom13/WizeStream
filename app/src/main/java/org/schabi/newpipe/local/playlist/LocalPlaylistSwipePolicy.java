package org.schabi.newpipe.local.playlist;

/** Resolves gesture ownership for local playlists shown inside or outside the main pager. */
public final class LocalPlaylistSwipePolicy {
    private LocalPlaylistSwipePolicy() {
    }

    /**
     * @param useAsFrontPage whether the playlist is hosted inside the main tab pager
     * @param editing whether Edit mode is active
     * @param swipeEnabled whether the user enabled swipe removal
     * @return whether horizontal item-removal swipes may consume the pager gesture
     */
    public static boolean isItemRemovalSwipeEnabled(final boolean useAsFrontPage,
                                                    final boolean editing,
                                                    final boolean swipeEnabled) {
        return !useAsFrontPage && editing && swipeEnabled;
    }
}
