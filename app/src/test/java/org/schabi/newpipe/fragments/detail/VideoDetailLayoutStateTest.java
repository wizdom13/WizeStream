package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;

import org.junit.Test;

public class VideoDetailLayoutStateTest {
    @Test
    public void normalDetailContentStartsBelowTheStatusBar() {
        assertEquals(30, VideoDetailFragment.getDetailContentTopMargin(false, 30));
    }

    @Test
    public void fullscreenDetailContentStartsAtTheTopOfTheWindow() {
        assertEquals(0, VideoDetailFragment.getDetailContentTopMargin(true, 30));
    }

    @Test
    public void unmeasuredToolbarDoesNotCreateANegativeOffset() {
        assertEquals(0, VideoDetailFragment.getDetailContentTopMargin(false, -1));
    }

    @Test
    public void fullscreenRemovesTheDetailNavigationRailStartMargin() {
        assertEquals(0, VideoDetailFragment.getDetailContentStartMargin(true, 80));
        assertEquals(80, VideoDetailFragment.getDetailContentStartMargin(false, 80));
    }

    @Test
    public void twoPaneDetailsRequireExpandedWidthAndLandscape() {
        assertTrue(VideoDetailFragment.shouldUseWideLandscapeDetailLayout(
                Configuration.ORIENTATION_LANDSCAPE, 840));
        assertFalse(VideoDetailFragment.shouldUseWideLandscapeDetailLayout(
                Configuration.ORIENTATION_PORTRAIT, 840));
        assertFalse(VideoDetailFragment.shouldUseWideLandscapeDetailLayout(
                Configuration.ORIENTATION_LANDSCAPE, 839));
    }

    @Test
    public void phoneDetailNavigationAvoidsBottomSystemBar() {
        assertEquals(48,
                VideoDetailFragment.getDetailNavigationBottomInset(
                        false, false, 48, 0));
    }

    @Test
    public void phoneDetailNavigationAvoidsLargerBottomCutout() {
        assertEquals(52,
                VideoDetailFragment.getDetailNavigationBottomInset(
                        false, false, 48, 52));
    }

    @Test
    public void fullscreenAndNavigationRailDoNotMoveDetailNavigation() {
        assertEquals(0,
                VideoDetailFragment.getDetailNavigationBottomInset(
                        true, false, 48, 52));
        assertEquals(0,
                VideoDetailFragment.getDetailNavigationBottomInset(
                        false, true, 48, 52));
    }
}
