package org.schabi.newpipe.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FullscreenCommentsSidebarTest {
    @Test
    fun sidebarUsesStableFragmentTag() {
        assertEquals("fullscreen_comments_sidebar", FullscreenCommentsSidebar.TAG)
    }
}
