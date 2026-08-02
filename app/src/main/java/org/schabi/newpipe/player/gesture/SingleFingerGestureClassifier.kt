package org.schabi.newpipe.player.gesture

import kotlin.math.abs

/** Locks a one-finger player gesture to its initial direction until the touch ends. */
internal class SingleFingerGestureClassifier(
    private val movementThreshold: Float
) {
    var state = State.PENDING
        private set

    fun update(
        totalDeltaX: Float,
        totalDeltaY: Float,
        fullscreenSwipeEligible: Boolean
    ): State {
        if (state != State.PENDING) {
            return state
        }

        val horizontalMovement = abs(totalDeltaX)
        val verticalMovement = abs(totalDeltaY)
        if (horizontalMovement <= movementThreshold && verticalMovement <= movementThreshold) {
            return state
        }

        state = when {
            horizontalMovement > verticalMovement -> State.HORIZONTAL_SEEK
            fullscreenSwipeEligible -> State.FULLSCREEN_SWIPE
            else -> State.VERTICAL_ADJUSTMENT
        }
        return state
    }

    fun reset() {
        state = State.PENDING
    }

    enum class State {
        PENDING,
        HORIZONTAL_SEEK,
        VERTICAL_ADJUSTMENT,
        FULLSCREEN_SWIPE
    }
}
