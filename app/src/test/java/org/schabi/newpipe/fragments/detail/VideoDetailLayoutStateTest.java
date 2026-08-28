package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertEquals;

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
}
