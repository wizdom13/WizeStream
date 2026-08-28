package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoDetailLoadingStateTest {
    @Test
    public void uncachedStreamHidesPreviousDetailsAndTabs() {
        assertTrue(VideoDetailFragment.shouldHidePreviousStreamContent(false));
    }

    @Test
    public void cachedStreamAvoidsUnnecessaryContentFlicker() {
        assertFalse(VideoDetailFragment.shouldHidePreviousStreamContent(true));
    }

    @Test
    public void remoteQueueItemProvidesLoadingPreview() {
        assertTrue(VideoDetailFragment.shouldShowQueueItemLoadingPreview(true, false));
    }

    @Test
    public void missingOrLocalQueueItemDoesNotProvideRemotePreview() {
        assertFalse(VideoDetailFragment.shouldShowQueueItemLoadingPreview(false, false));
        assertFalse(VideoDetailFragment.shouldShowQueueItemLoadingPreview(true, true));
    }
}
