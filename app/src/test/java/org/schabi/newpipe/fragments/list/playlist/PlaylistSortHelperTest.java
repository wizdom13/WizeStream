package org.schabi.newpipe.fragments.list.playlist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

public class PlaylistSortHelperTest {
    private final StreamInfoItem newest = item("newest", 2026, 10);
    private final StreamInfoItem oldest = item("oldest", 2020, 100);
    private final StreamInfoItem unknown = item("unknown", null, -1);

    @Test
    public void preservesPlaylistOrderWithoutMutatingTheSource() {
        final List<StreamInfoItem> source = List.of(oldest, unknown, newest);

        final List<StreamInfoItem> sorted =
                PlaylistSortHelper.sortedCopy(source, PlaylistSortOrder.PLAYLIST_ORDER);

        assertNotSame(source, sorted);
        assertEquals(List.of("oldest", "unknown", "newest"), names(sorted));
    }

    @Test
    public void sortsLatestFirstAndKeepsUnknownDatesAtTheEnd() {
        assertEquals(
                List.of("newest", "oldest", "unknown"),
                names(PlaylistSortHelper.sortedCopy(
                        List.of(oldest, unknown, newest), PlaylistSortOrder.LATEST)));
    }

    @Test
    public void sortsOldestFirstAndKeepsUnknownDatesAtTheEnd() {
        assertEquals(
                List.of("oldest", "newest", "unknown"),
                names(PlaylistSortHelper.sortedCopy(
                        List.of(newest, unknown, oldest), PlaylistSortOrder.OLDEST)));
    }

    @Test
    public void sortsPopularFirstAndKeepsUnknownCountsAtTheEnd() {
        assertEquals(
                List.of("oldest", "newest", "unknown"),
                names(PlaylistSortHelper.sortedCopy(
                        List.of(newest, unknown, oldest), PlaylistSortOrder.POPULAR)));
    }

    @Test
    public void keepsEqualValuesInPlaylistOrder() {
        final StreamInfoItem first = item("first", 2026, 10);
        final StreamInfoItem second = item("second", 2026, 10);

        assertEquals(
                List.of("first", "second"),
                names(PlaylistSortHelper.sortedCopy(
                        List.of(first, second), PlaylistSortOrder.POPULAR)));
    }

    private static StreamInfoItem item(final String name,
                                       final Integer year,
                                       final long viewCount) {
        final StreamInfoItem item = new StreamInfoItem(
                0, "https://example.com/" + name, name, StreamType.VIDEO_STREAM);
        if (year != null) {
            item.setUploadDate(new DateWrapper(
                    OffsetDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)));
        }
        item.setViewCount(viewCount);
        return item;
    }

    private static List<String> names(final List<StreamInfoItem> items) {
        return items.stream().map(StreamInfoItem::getName).collect(Collectors.toList());
    }
}
