package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoPlayerUiThumbnailTest {

    @Test
    public void avoidsAllocatingAnotherBitmapWhenTheThumbnailAlreadyFits() {
        assertFalse(VideoPlayerUi.needsThumbnailScaling(720, 720));
        assertFalse(VideoPlayerUi.needsThumbnailScaling(720, 1080));
    }

    @Test
    public void scalesOnlyOversizedThumbnails() {
        assertTrue(VideoPlayerUi.needsThumbnailScaling(2160, 1080));
        assertFalse(VideoPlayerUi.needsThumbnailScaling(2160, 0));
    }
}
