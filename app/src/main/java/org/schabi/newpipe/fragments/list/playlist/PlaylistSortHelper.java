package org.schabi.newpipe.fragments.list.playlist;

import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PlaylistSortHelper {
    private PlaylistSortHelper() {
    }

    static List<StreamInfoItem> sortedCopy(final List<StreamInfoItem> items,
                                           final PlaylistSortOrder order) {
        final List<StreamInfoItem> sortedItems = new ArrayList<>(items);
        final Comparator<StreamInfoItem> comparator = comparatorFor(order);
        if (comparator != null) {
            sortedItems.sort(comparator);
        }
        return sortedItems;
    }

    private static Comparator<StreamInfoItem> comparatorFor(final PlaylistSortOrder order) {
        switch (order) {
            case LATEST:
                return Comparator.comparing(
                        PlaylistSortHelper::uploadDate,
                        Comparator.nullsLast(Comparator.reverseOrder()));
            case POPULAR:
                return Comparator.comparing(
                        PlaylistSortHelper::knownViewCount,
                        Comparator.nullsLast(Comparator.reverseOrder()));
            case OLDEST:
                return Comparator.comparing(
                        PlaylistSortHelper::uploadDate,
                        Comparator.nullsLast(Comparator.naturalOrder()));
            case PLAYLIST_ORDER:
            default:
                return null;
        }
    }

    private static OffsetDateTime uploadDate(final StreamInfoItem item) {
        final DateWrapper uploadDate = item.getUploadDate();
        return uploadDate == null ? null : uploadDate.offsetDateTime();
    }

    private static Long knownViewCount(final StreamInfoItem item) {
        return item.getViewCount() < 0 ? null : item.getViewCount();
    }
}
