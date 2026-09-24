package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.schabi.newpipe.extractor.stream.StreamExtractor.NO_AGE_LIMIT;

import org.schabi.newpipe.extractor.ServiceList;

import org.junit.Test;

public class VideoDetailLoadingStateTest {
    @Test
    public void ageRestrictionPreferenceDoesNotBlockBitChutePlayback() {
        assertFalse(VideoDetailFragment.shouldHideAgeRestrictedContent(
                ServiceList.BitChute.getServiceId(), 18, false));
        assertTrue(VideoDetailFragment.shouldHideAgeRestrictedContent(
                ServiceList.YouTube.getServiceId(), 18, false));
        assertFalse(VideoDetailFragment.shouldHideAgeRestrictedContent(
                ServiceList.YouTube.getServiceId(), NO_AGE_LIMIT, false));
        assertFalse(VideoDetailFragment.shouldHideAgeRestrictedContent(
                ServiceList.YouTube.getServiceId(), 18, true));
    }

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
