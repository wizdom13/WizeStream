package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoPlayerUiTvFocusTest {
    @Test
    public void embeddedTvPlayerDoesNotStealFocus() {
        assertFalse(VideoPlayerUi.shouldRequestPlaybackButtonFocus(
                true, false, false, false));
    }

    @Test
    public void fullscreenAndPopupTvPlayersMayOwnFocus() {
        assertTrue(VideoPlayerUi.shouldRequestPlaybackButtonFocus(
                true, true, false, false));
        assertTrue(VideoPlayerUi.shouldRequestPlaybackButtonFocus(
                true, false, true, false));
    }

    @Test
    public void phonePlayerKeepsExistingFocusBehavior() {
        assertTrue(VideoPlayerUi.shouldRequestPlaybackButtonFocus(
                false, false, false, false));
    }

    @Test
    public void openPlayerListsKeepTheirOwnFocus() {
        assertFalse(VideoPlayerUi.shouldRequestPlaybackButtonFocus(
                false, true, false, true));
        assertFalse(VideoPlayerUi.shouldRequestPlaybackButtonFocus(
                true, true, false, true));
    }
}
