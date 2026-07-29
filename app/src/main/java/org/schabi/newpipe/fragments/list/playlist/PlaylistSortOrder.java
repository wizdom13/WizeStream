package org.schabi.newpipe.fragments.list.playlist;

import androidx.annotation.StringRes;

import org.schabi.newpipe.R;

enum PlaylistSortOrder {
    PLAYLIST_ORDER(R.string.playlist_sort_order),
    LATEST(R.string.channel_video_sort_latest),
    POPULAR(R.string.channel_video_sort_popular),
    OLDEST(R.string.channel_video_sort_oldest);

    @StringRes
    private final int label;

    PlaylistSortOrder(@StringRes final int label) {
        this.label = label;
    }

    @StringRes
    int getLabel() {
        return label;
    }
}
