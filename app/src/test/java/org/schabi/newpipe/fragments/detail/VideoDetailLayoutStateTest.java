package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;
import android.graphics.Color;

import org.junit.Test;
import org.schabi.newpipe.player.ui.FullscreenOrientationPolicy;

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
    public void fullscreenUsesBlackPlayerBackdropForLetterboxing() {
        assertEquals(Color.BLACK,
                VideoDetailFragment.getPlayerPlaceholderBackgroundColor(true));
        assertEquals(Color.TRANSPARENT,
                VideoDetailFragment.getPlayerPlaceholderBackgroundColor(false));
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
        assertTrue(FullscreenOrientationPolicy.shouldUseWideLandscapeDetailLayout(
                Configuration.ORIENTATION_LANDSCAPE, 840));
        assertFalse(FullscreenOrientationPolicy.shouldUseWideLandscapeDetailLayout(
                Configuration.ORIENTATION_PORTRAIT, 840));
        assertFalse(FullscreenOrientationPolicy.shouldUseWideLandscapeDetailLayout(
                Configuration.ORIENTATION_LANDSCAPE, 839));
    }

    @Test
    public void layoutRecreationTracksTheCurrentlyInflatedLayout() {
        assertTrue(FullscreenOrientationPolicy.shouldRecreateDetailLayout(
                false, Configuration.ORIENTATION_LANDSCAPE, 840));
        assertFalse(FullscreenOrientationPolicy.shouldRecreateDetailLayout(
                true, Configuration.ORIENTATION_LANDSCAPE, 840));
        assertTrue(FullscreenOrientationPolicy.shouldRecreateDetailLayout(
                true, Configuration.ORIENTATION_PORTRAIT, 1200));
        assertFalse(FullscreenOrientationPolicy.shouldRecreateDetailLayout(
                false, Configuration.ORIENTATION_PORTRAIT, 1200));
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
    public void fullscreenAndWideDetailNavigationDoNotMoveForBottomInsets() {
        assertEquals(0,
                VideoDetailFragment.getDetailNavigationBottomInset(
                        true, false, 48, 52));
        assertEquals(0,
                VideoDetailFragment.getDetailNavigationBottomInset(
                        false, true, 48, 52));
    }
}
