package org.schabi.newpipe.player.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class SingleFingerGestureClassifierTest {
    private val classifier = SingleFingerGestureClassifier(MOVEMENT_THRESHOLD)

    @Test
    fun movementInsideThresholdRemainsPending() {
        assertEquals(
            SingleFingerGestureClassifier.State.PENDING,
            classifier.update(40f, -40f, fullscreenSwipeEligible = false)
        )
    }

    @Test
    fun verticalGestureRemainsLockedAfterHorizontalTransition() {
        assertEquals(
            SingleFingerGestureClassifier.State.VERTICAL_ADJUSTMENT,
            classifier.update(5f, 60f, fullscreenSwipeEligible = false)
        )
        assertEquals(
            SingleFingerGestureClassifier.State.VERTICAL_ADJUSTMENT,
            classifier.update(150f, 70f, fullscreenSwipeEligible = false)
        )
    }

    @Test
    fun horizontalGestureRemainsLockedAfterVerticalTransition() {
        assertEquals(
            SingleFingerGestureClassifier.State.HORIZONTAL_SEEK,
            classifier.update(60f, 5f, fullscreenSwipeEligible = false)
        )
        assertEquals(
            SingleFingerGestureClassifier.State.HORIZONTAL_SEEK,
            classifier.update(70f, 150f, fullscreenSwipeEligible = true)
        )
    }

    @Test
    fun diagonalGestureUsesVerticalDirectionWhenAxesAreEqual() {
        assertEquals(
            SingleFingerGestureClassifier.State.VERTICAL_ADJUSTMENT,
            classifier.update(60f, -60f, fullscreenSwipeEligible = false)
        )
    }

    @Test
    fun eligibleVerticalGestureLocksFullscreenSwipe() {
        assertEquals(
            SingleFingerGestureClassifier.State.FULLSCREEN_SWIPE,
            classifier.update(5f, -60f, fullscreenSwipeEligible = true)
        )
        assertEquals(
            SingleFingerGestureClassifier.State.FULLSCREEN_SWIPE,
            classifier.update(100f, -60f, fullscreenSwipeEligible = false)
        )
    }

    @Test
    fun resetAllowsNewTouchToChooseAnotherDirection() {
        classifier.update(5f, 60f, fullscreenSwipeEligible = false)

        classifier.reset()

        assertEquals(
            SingleFingerGestureClassifier.State.HORIZONTAL_SEEK,
            classifier.update(60f, 5f, fullscreenSwipeEligible = false)
        )
    }

    private companion object {
        const val MOVEMENT_THRESHOLD = 40f
    }
}
