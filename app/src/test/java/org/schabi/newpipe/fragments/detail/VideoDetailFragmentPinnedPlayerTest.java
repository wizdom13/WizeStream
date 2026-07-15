package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoDetailFragmentPinnedPlayerTest {
    @Test
    public void disabledPreferenceKeepsLegacyLayout() {
        assertFalse(VideoDetailFragment.shouldUsePinnedPlayerLayout(
                false, true, true, false, true, false, false));
    }

    @Test
    public void enabledWithoutSelectedOrAttachedMainPlayerKeepsLegacyLayout() {
        assertFalse(VideoDetailFragment.shouldUsePinnedPlayerLayout(
                true, false, true, false, true, false, false));
        assertFalse(VideoDetailFragment.shouldUsePinnedPlayerLayout(
                true, true, false, false, true, false, false));
    }

    @Test
    public void enabledActiveAttachedMainPlayerOnPhoneUsesPinnedLayout() {
        assertTrue(VideoDetailFragment.shouldUsePinnedPlayerLayout(
                true, true, true, false, true, false, false));
    }

    @Test
    public void fullscreenTabletAndTvKeepLegacyLayout() {
        assertFalse(VideoDetailFragment.shouldUsePinnedPlayerLayout(
                true, true, true, true, true, false, false));
        assertFalse(VideoDetailFragment.shouldUsePinnedPlayerLayout(
                true, true, true, false, true, true, false));
        assertFalse(VideoDetailFragment.shouldUsePinnedPlayerLayout(
                true, true, true, false, true, false, true));
    }

    @Test
    public void absentRelatedItemsLayoutMapsToPhoneLayout() {
        assertTrue(VideoDetailFragment.isPhoneDetailLayout(false));
    }

    @Test
    public void presentRelatedItemsLayoutMapsToNonPhoneLayout() {
        assertFalse(VideoDetailFragment.isPhoneDetailLayout(true));
    }

    @Test
    public void phoneContentMarginEqualsThumbnailHeight() {
        assertEquals(720, VideoDetailFragment.getContentTopMargin(true, 720));
    }

    @Test
    public void tabletContentMarginRemainsZero() {
        assertEquals(0, VideoDetailFragment.getContentTopMargin(false, 720));
    }

    @Test
    public void requestedHeightTakesPrecedenceOverStaleMeasuredHeight() {
        assertEquals(400, VideoDetailFragment.resolveThumbnailHeight(400, 300, 200));
    }

    @Test
    public void layoutParamHeightUsedWhenNoRequestedHeightExists() {
        assertEquals(300, VideoDetailFragment.resolveThumbnailHeight(0, 300, 200));
    }

    @Test
    public void measuredHeightIsFinalFallback() {
        assertEquals(200, VideoDetailFragment.resolveThumbnailHeight(0, 0, 200));
    }

    @Test
    public void negativeFallbackHeightIsClampedToZero() {
        assertEquals(0, VideoDetailFragment.resolveThumbnailHeight(0, 0, -1));
    }
}
