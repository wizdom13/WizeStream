package org.schabi.newpipe.local.playlist;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.download.BulkDownloadItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.List;

public class LocalPlaylistBulkDownloadMapperTest {
    @Test
    public void localMediaIsSkippedAndRemoteOrderIsPreserved() {
        final PlaylistStreamEntry first = remoteEntry(0, "https://example.test/first", "First");
        final PlaylistStreamEntry local = mock(PlaylistStreamEntry.class);
        final StreamEntity localEntity = mock(StreamEntity.class);
        when(local.getStreamEntity()).thenReturn(localEntity);
        when(localEntity.isLocalMedia()).thenReturn(true);
        final PlaylistStreamEntry second = remoteEntry(1, "https://example.test/second", "Second");

        final List<BulkDownloadItem> result =
                LocalPlaylistBulkDownloadMapper.from(List.of(first, local, second));

        assertEquals(2, result.size());
        assertEquals("First", result.get(0).getTitle());
        assertEquals("https://example.test/first", result.get(0).getUrl());
        assertEquals("Second", result.get(1).getTitle());
    }

    private static PlaylistStreamEntry remoteEntry(final int serviceId,
                                                   final String url,
                                                   final String title) {
        final PlaylistStreamEntry entry = mock(PlaylistStreamEntry.class);
        final StreamEntity entity = mock(StreamEntity.class);
        when(entry.getStreamEntity()).thenReturn(entity);
        when(entity.isLocalMedia()).thenReturn(false);
        when(entry.toStreamInfoItem()).thenReturn(
                new StreamInfoItem(serviceId, url, title, StreamType.VIDEO_STREAM));
        return entry;
    }
}
