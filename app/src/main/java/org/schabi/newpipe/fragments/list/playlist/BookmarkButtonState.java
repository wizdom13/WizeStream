package org.schabi.newpipe.fragments.list.playlist;

final class BookmarkButtonState {
    private BookmarkButtonState() {
    }

    static boolean isEnabled(final boolean lookupReady,
                             final boolean hasStoredBookmark,
                             final boolean hasLoadedPlaylistInfo,
                             final boolean actionRunning) {
        return lookupReady
                && (hasStoredBookmark || hasLoadedPlaylistInfo)
                && !actionRunning;
    }
}
