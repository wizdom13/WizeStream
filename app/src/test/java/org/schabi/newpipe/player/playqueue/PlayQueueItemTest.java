package org.schabi.newpipe.player.playqueue;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayQueueItemTest {

    public static final String URL = "MY_URL";

    @Test
    public void equalsMustNotBeOverloaded() {
        final PlayQueueItem a = PlayQueueTest.makeItemWithUrl(URL);
        final PlayQueueItem b = PlayQueueTest.makeItemWithUrl(URL);
        assertEquals(a, a);
        assertNotEquals(a, b); // they should compare different even if they have the same data
    }

    @Test
    public void localMediaIsExplicitAndNeverUsesExtractor() {
        final PlayQueueItem item = PlayQueueItem.localMedia(
                "Local song", "content://media/audio/1", 120,
                "Artist", "Album", "Music", "audio/mpeg", 1, false, null);

        assertTrue(item.isLocalMedia());
        assertEquals(PlayQueueItem.LOCAL_SERVICE_ID, item.getServiceId());
        item.getStream().test().assertError(IllegalStateException.class);
    }

    @Test
    public void localAndRemoteSourcesAreNeverTreatedAsTheSameItem() {
        final PlayQueueItem local = PlayQueueItem.localMedia(
                "Local", URL, 1, null, null, null, null, 1, false, null);
        assertFalse(local.isSameItem(PlayQueueTest.makeItemWithUrl(URL)));
    }
}
