package org.schabi.newpipe.fragments.list.playlist;

import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.StreamListFilter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class PlaylistSortHelper {
    private PlaylistSortHelper() {
    }

    static List<StreamInfoItem> sortedCopy(final List<StreamInfoItem> items,
                                           final PlaylistSortOrder order) {
        final List<StreamInfoItem> sortedItems = new ArrayList<>(items);
        sortInPlace(sortedItems, order);
        return sortedItems;
    }

    static List<StreamInfoItem> itemsForDisplay(
            final List<StreamInfoItem> items,
            final StreamListFilter filter,
            final Map<String, StreamStateEntity> streamStates,
            final PlaylistSortOrder order) {
        if (filter == StreamListFilter.NONE && order == PlaylistSortOrder.PLAYLIST_ORDER) {
            return items;
        }

        final List<StreamInfoItem> displayedItems = new ArrayList<>(items.size());
        for (final StreamInfoItem item : items) {
            if (StreamListFilter.matches(filter, item, streamStates.get(item.getUrl()))) {
                displayedItems.add(item);
            }
        }
        sortInPlace(displayedItems, order);
        return displayedItems;
    }

    private static void sortInPlace(final List<StreamInfoItem> items,
                                    final PlaylistSortOrder order) {
        final Comparator<StreamInfoItem> comparator = comparatorFor(order);
        if (comparator != null) {
            items.sort(comparator);
        }
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
