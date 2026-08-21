package org.schabi.newpipe.local.playlist;

import androidx.annotation.NonNull;

import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.download.BulkDownloadItem;

import java.util.List;
import java.util.stream.Collectors;

/** Converts downloadable entries from a local playlist into batch download items. */
final class LocalPlaylistBulkDownloadMapper {
    private LocalPlaylistBulkDownloadMapper() {
    }

    @NonNull
    static List<BulkDownloadItem> from(@NonNull final List<PlaylistStreamEntry> entries) {
        return entries.stream()
                .filter(entry -> !entry.getStreamEntity().isLocalMedia())
                .map(PlaylistStreamEntry::toStreamInfoItem)
                .map(BulkDownloadItem::from)
                .collect(Collectors.toList());
    }
}
