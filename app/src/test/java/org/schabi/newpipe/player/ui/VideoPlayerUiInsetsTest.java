package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VideoPlayerUiInsetsTest {
    @Test
    public void fullscreenControlsAvoidVisibleStatusBar() {
        assertEquals(28, VideoPlayerUi.calculateTopControlsPadding(true, 4, 24, 0));
    }

    @Test
    public void fullscreenControlsAvoidLargerDisplayCutout() {
        assertEquals(34, VideoPlayerUi.calculateTopControlsPadding(true, 4, 24, 30));
    }

    @Test
    public void embeddedPlayerDoesNotDuplicateActivityInset() {
        assertEquals(4, VideoPlayerUi.calculateTopControlsPadding(false, 4, 24, 30));
    }

    @Test
    public void invalidInsetsAndPaddingAreClamped() {
        assertEquals(0, VideoPlayerUi.calculateTopControlsPadding(true, -4, -24, -30));
    }

    @Test
    public void fullscreenEdgeControlsAvoidSideCutoutAndNavigationBar() {
        assertEquals(36,
                VideoPlayerUi.calculateControlsEdgePadding(true, 6, 24, 30));
    }

    @Test
    public void embeddedEdgeControlsKeepOnlyTheirBasePadding() {
        assertEquals(6,
                VideoPlayerUi.calculateControlsEdgePadding(false, 6, 24, 30));
    }
}
