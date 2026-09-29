package org.schabi.newpipe.player.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class CompletedPlaybackFullscreenGestureTest {
    @Test
    fun completedPlaybackStillClassifiesDownwardFullscreenSwipe() {
        val classifier = SingleFingerGestureClassifier(40f)
        val state = classifier.update(
            deltaX = 0f,
            deltaY = 120f,
            fullscreenSwipeEligible = true
        )

        assertEquals(SingleFingerGestureClassifier.State.FULLSCREEN_SWIPE, state)
    }
}
