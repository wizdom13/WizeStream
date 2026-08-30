package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.util.Collections;
import java.util.List;

public class PlayerHelperAutoQueueTest {
    @Test
    public void shortFormQueueItemPreservesContentType() {
        assertTrue(new PlayQueueItem(stream("short", true)).isShortFormContent());
    }

    @Test
    public void prefersFirstNonRepeatedShortForShortFormPlayback() {
        final StreamInfoItem longVideo = stream("long", false);
        final StreamInfoItem repeatedShort = stream("short-1", true);
        final StreamInfoItem nextShort = stream("short-2", true);
        final StreamInfo info = infoWithRelated(longVideo, repeatedShort, nextShort);

        final PlayQueue queue = PlayerHelper.autoQueueOf(info,
                List.of(new PlayQueueItem(repeatedShort)), true);

        assertNotNull(queue);
        assertEquals(nextShort.getUrl(), queue.getItem().getUrl());
    }

    @Test
    public void fallsBackToRegularRelatedVideoWhenNoShortIsAvailable() {
        final StreamInfoItem longVideo = stream("long", false);
        final StreamInfo info = infoWithRelated(longVideo);

        final PlayQueue queue = PlayerHelper.autoQueueOf(
                info, Collections.emptyList(), true);

        assertNotNull(queue);
        assertEquals(longVideo.getUrl(), queue.getItem().getUrl());
    }

    @Test
    public void regularPlaybackKeepsExistingFirstRelatedBehavior() {
        final StreamInfoItem longVideo = stream("long", false);
        final StreamInfoItem shortVideo = stream("short", true);
        final StreamInfo info = infoWithRelated(longVideo, shortVideo);

        final PlayQueue queue = PlayerHelper.autoQueueOf(
                info, Collections.emptyList(), false);

        assertNotNull(queue);
        assertEquals(longVideo.getUrl(), queue.getItem().getUrl());
    }

    private static StreamInfo infoWithRelated(final InfoItem... items) {
        final StreamInfo info = new StreamInfo();
        info.setRelatedItems(List.of(items));
        return info;
    }

    private static StreamInfoItem stream(final String url, final boolean shortFormContent) {
        final StreamInfoItem item = new StreamInfoItem(
                0, url, url, StreamType.VIDEO_STREAM);
        item.setShortFormContent(shortFormContent);
        return item;
    }
}
