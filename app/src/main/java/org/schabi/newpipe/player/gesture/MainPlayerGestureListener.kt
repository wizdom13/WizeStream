package org.schabi.newpipe.player.gesture

import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sign
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ThemeHelper.getAndroidDimenPx

/**
 * GestureListener for the player
 *
 * While [BasePlayerGestureListener] contains the logic behind the single gestures
 * this class focuses on the visual aspect like hiding and showing the controls or changing
 * volume/brightness during scrolling for specific events.
 */
class MainPlayerGestureListener(
    private val playerUi: MainPlayerUi
) : BasePlayerGestureListener(playerUi), OnTouchListener {
    private var isMoving = false

    private var isSwipeSeeking = false
    private var accumulatedSeek = 0f
    private var swipeSeekStartPosition = 0L
    private var swipeSeekTargetPosition = 0L

    private var isPendingFullscreenSwipe = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDelegatingToBottomSheet = false
    private val singleFingerGestureClassifier =
        SingleFingerGestureClassifier(MOVEMENT_THRESHOLD.toFloat())

    private var twoFingerGestureState = TwoFingerGestureState.IDLE
    private var suppressSingleTouchUntilUp = false
    private var twoFingerInitialCenterX = 0f
    private var twoFingerInitialCenterY = 0f
    private var twoFingerInitialSpan = 0f
    private var twoFingerStartSpeed = 1f
    private var lastGestureSpeed = 1f

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (handleTwoFingerSpeedGesture(v, event)) {
            return true
        }

        if (isDelegatingToBottomSheet) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                super.onTouch(v, event)
                isDelegatingToBottomSheet = false
            }
            v.parent?.requestDisallowInterceptTouchEvent(false)
            return false
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            initialTouchX = event.x
            initialTouchY = event.y
            singleFingerGestureClassifier.reset()
        }

        val fullscreenGestureEnabled = PlayerHelper.isFullscreenGestureEnabled(player.context)
        val swipeDownToMiniplayerEnabled = isSwipeDownToMiniplayerEnabled()

        super.onTouch(v, event)

        if (event.actionMasked == MotionEvent.ACTION_MOVE &&
            shouldDelegateDownwardSwipeToBottomSheet(
                playerUi.isFullscreen,
                swipeDownToMiniplayerEnabled,
                event.x - initialTouchX,
                event.y - initialTouchY
            )
        ) {
            isDelegatingToBottomSheet = true
            isMoving = false
            isPendingFullscreenSwipe = false
            singleFingerGestureClassifier.reset()
            v.parent?.requestDisallowInterceptTouchEvent(false)
            return false
        }

        val gestureEnded = event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        if (gestureEnded && isMoving) {
            isMoving = false
            onScrollEnd(event)
        }
        if (gestureEnded) {
            singleFingerGestureClassifier.reset()
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                v.parent?.requestDisallowInterceptTouchEvent(
                    playerUi.isFullscreen ||
                        fullscreenGestureEnabled ||
                        !swipeDownToMiniplayerEnabled
                )
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.parent?.requestDisallowInterceptTouchEvent(false)
                false
            }

            else -> true
        }
    }

    private fun handleTwoFingerSpeedGesture(v: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
            event.pointerCount == 2 &&
            PlayerHelper.isTwoFingerSpeedGestureEnabled(player.context) &&
            !player.exoPlayerIsNull() &&
            player.currentState != Player.STATE_COMPLETED
        ) {
            cancelSingleFingerGesture(event)
            restoreHoldToSpeed()
            isMoving = false
            isSwipeSeeking = false
            isPendingFullscreenSwipe = false
            isDelegatingToBottomSheet = false
            singleFingerGestureClassifier.reset()
            binding.swipeSeekDisplay.isVisible = false
            binding.volumeRelativeLayout.isVisible = false
            binding.brightnessRelativeLayout.isVisible = false

            twoFingerInitialCenterX = centerX(event)
            twoFingerInitialCenterY = centerY(event)
            twoFingerInitialSpan = pointerSpan(event)
            twoFingerStartSpeed = player.playbackSpeed
            lastGestureSpeed = twoFingerStartSpeed
            twoFingerGestureState = TwoFingerGestureState.PENDING
            suppressSingleTouchUntilUp = true
            v.parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        if (!suppressSingleTouchUntilUp) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    updateTwoFingerSpeedGesture(v, event)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> finishTwoFingerSpeedGesture()

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finishTwoFingerSpeedGesture()
                suppressSingleTouchUntilUp = false
                v.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun cancelSingleFingerGesture(event: MotionEvent) {
        val cancelEvent = MotionEvent.obtain(event)
        cancelEvent.action = MotionEvent.ACTION_CANCEL
        playerUi.gestureDetector.onTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private fun updateTwoFingerSpeedGesture(v: View, event: MotionEvent) {
        val verticalMovement = twoFingerInitialCenterY - centerY(event)
        val horizontalMovement = centerX(event) - twoFingerInitialCenterX
        val spanChange = abs(pointerSpan(event) - twoFingerInitialSpan)
        val lockThreshold = TWO_FINGER_LOCK_THRESHOLD_DP *
            player.context.resources.displayMetrics.density

        if (twoFingerGestureState == TwoFingerGestureState.PENDING) {
            twoFingerGestureState = classifyTwoFingerGesture(
                verticalMovement,
                horizontalMovement,
                spanChange,
                lockThreshold
            )
            if (twoFingerGestureState == TwoFingerGestureState.SPEED) {
                binding.speedGestureDisplay.text =
                    PlayerHelper.formatSpeed(twoFingerStartSpeed.toDouble())
                binding.speedGestureDisplay.animate(
                    true,
                    150,
                    AnimationType.SCALE_AND_ALPHA
                )
            }
        }

        if (twoFingerGestureState != TwoFingerGestureState.SPEED) {
            return
        }

        val pixelsPerStep = TWO_FINGER_SPEED_STEP_DP *
            player.context.resources.displayMetrics.density
        val newSpeed = calculatePlaybackSpeed(
            twoFingerStartSpeed,
            verticalMovement,
            pixelsPerStep
        )
        if (newSpeed == lastGestureSpeed) {
            return
        }

        player.setPlaybackSpeed(newSpeed)
        binding.speedGestureDisplay.text = PlayerHelper.formatSpeed(newSpeed.toDouble())
        if (newSpeed == NORMAL_PLAYBACK_SPEED && lastGestureSpeed != NORMAL_PLAYBACK_SPEED) {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        lastGestureSpeed = newSpeed
    }

    private fun finishTwoFingerSpeedGesture() {
        if (twoFingerGestureState == TwoFingerGestureState.SPEED &&
            binding.speedGestureDisplay.isVisible
        ) {
            binding.speedGestureDisplay.animate(
                false,
                200,
                AnimationType.SCALE_AND_ALPHA,
                200
            )
        }
        twoFingerGestureState = TwoFingerGestureState.IDLE
    }

    private fun centerX(event: MotionEvent): Float = (event.getX(0) + event.getX(1)) / 2f

    private fun centerY(event: MotionEvent): Float = (event.getY(0) + event.getY(1)) / 2f

    private fun pointerSpan(event: MotionEvent): Float = hypot(
        event.getX(0) - event.getX(1),
        event.getY(0) - event.getY(1)
    )

    private fun isSwipeDownToMiniplayerEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(player.context)
            .getBoolean(player.context.getString(R.string.swipe_down_to_miniplayer_key), true)
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (DEBUG) {
            Log.d(TAG, "onSingleTapConfirmed() called with: e = [$e]")
        }

        if (isDoubleTapping) {
            return true
        }
        super.onSingleTapConfirmed(e)

        if (player.currentState != Player.STATE_BLOCKED) {
            onSingleTap()
        }
        return true
    }

    private fun onScrollVolume(distanceY: Float) {
        val bar: ProgressBar = binding.volumeProgressBar
        val audioReactor: AudioReactor = player.audioReactor

        // If we just started sliding, change the progress bar to match the system volume
        if (!binding.volumeRelativeLayout.isVisible) {
            val volumePercent: Float = audioReactor.volume / audioReactor.maxVolume.toFloat()
            bar.progress = (volumePercent * bar.max).toInt()
        }

        // Update progress bar
        binding.volumeProgressBar.incrementProgressBy(distanceY.toInt())

        // Update volume
        val currentProgressPercent: Float = bar.progress / bar.max.toFloat()
        val currentVolume = (audioReactor.maxVolume * currentProgressPercent).toInt()
        audioReactor.volume = currentVolume
        if (DEBUG) {
            Log.d(TAG, "onScroll().volumeControl, currentVolume = $currentVolume")
        }

        // Update player center image
        binding.volumeImageView.setImageDrawable(
            AppCompatResources.getDrawable(
                player.context,
                when {
                    currentProgressPercent <= 0 -> R.drawable.ic_volume_off
                    currentProgressPercent < 0.25 -> R.drawable.ic_volume_mute
                    currentProgressPercent < 0.75 -> R.drawable.ic_volume_down
                    else -> R.drawable.ic_volume_up
                }
            )
        )

        // Make sure the correct layout is visible
        if (!binding.volumeRelativeLayout.isVisible) {
            binding.volumeRelativeLayout.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
        }
        binding.brightnessRelativeLayout.isVisible = false
    }

    private fun onScrollBrightness(distanceY: Float) {
        val parent: AppCompatActivity = playerUi.parentActivity.orElse(null) ?: return
        val window = parent.window
        val layoutParams = window.attributes
        val bar: ProgressBar = binding.brightnessProgressBar

        // Update progress bar
        val oldBrightness = layoutParams.screenBrightness
        bar.progress = (bar.max * oldBrightness.coerceIn(0f, 1f)).toInt()
        bar.incrementProgressBy(distanceY.toInt())

        // Update brightness
        val currentProgressPercent = bar.progress.toFloat() / bar.max
        layoutParams.screenBrightness = currentProgressPercent
        window.attributes = layoutParams

        // Save current brightness level
        PlayerHelper.setScreenBrightness(parent, currentProgressPercent)
        if (DEBUG) {
            Log.d(
                TAG,
                "onScroll().brightnessControl, " +
                    "currentBrightness = " + currentProgressPercent
            )
        }

        // Update player center image
        binding.brightnessImageView.setImageDrawable(
            AppCompatResources.getDrawable(
                player.context,
                when {
                    currentProgressPercent < 0.25 -> R.drawable.ic_brightness_low
                    currentProgressPercent < 0.75 -> R.drawable.ic_brightness_medium
                    else -> R.drawable.ic_brightness_high
                }
            )
        )

        // Make sure the correct layout is visible
        if (!binding.brightnessRelativeLayout.isVisible) {
            binding.brightnessRelativeLayout.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
        }
        binding.volumeRelativeLayout.isVisible = false
    }

    override fun onScrollEnd(event: MotionEvent) {
        super.onScrollEnd(event)
        if (isPendingFullscreenSwipe) {
            playerUi.toggleFullscreenWithOrientation()
            isPendingFullscreenSwipe = false
            return
        }
        if (isSwipeSeeking) {
            player.seekTo(swipeSeekTargetPosition)
            binding.swipeSeekDisplay.animate(false, 200, AnimationType.SCALE_AND_ALPHA)
            isSwipeSeeking = false
        }
        if (binding.volumeRelativeLayout.isVisible) {
            binding.volumeRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
        }
        if (binding.brightnessRelativeLayout.isVisible) {
            binding.brightnessRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
        }
    }

    private fun onScrollSeek(distanceX: Float) {
        val duration = player.exoPlayer?.duration ?: 0L
        if (duration <= 0L) {
            return
        }

        if (!isSwipeSeeking) {
            isSwipeSeeking = true
            accumulatedSeek = 0f
            swipeSeekStartPosition = player.exoPlayer?.currentPosition ?: 0L
            swipeSeekTargetPosition = swipeSeekStartPosition
            binding.swipeSeekDisplay.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
            binding.volumeRelativeLayout.isVisible = false
            binding.brightnessRelativeLayout.isVisible = false
        }

        accumulatedSeek -= distanceX
        val thresholdPx = SEEK_SWIPE_FAST_THRESHOLD_MS / SEEK_SWIPE_FACTOR
        val deltaMs = if (abs(accumulatedSeek) <= thresholdPx) {
            (accumulatedSeek * SEEK_SWIPE_FACTOR).toLong()
        } else {
            val beyond = abs(accumulatedSeek) - thresholdPx
            (
                sign(accumulatedSeek) *
                    (
                        SEEK_SWIPE_FAST_THRESHOLD_MS +
                            beyond * SEEK_SWIPE_FACTOR * SEEK_SWIPE_FAST_MULTIPLIER
                        )
                ).toLong()
        }

        swipeSeekTargetPosition = (swipeSeekStartPosition + deltaMs).coerceIn(0L, duration)

        val delta = swipeSeekTargetPosition - swipeSeekStartPosition
        val deltaText = (if (delta >= 0) "+" else "-") +
            Localization.getDurationString(abs(delta) / 1000L)
        val targetText = Localization.getDurationString(swipeSeekTargetPosition / 1000L)
        binding.swipeSeekDisplay.text = "$deltaText ($targetText)"
    }

    override fun onScroll(
        initialEvent: MotionEvent?,
        movingEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (initialEvent == null) {
            return false
        }

        // Calculate heights of status and navigation bars
        val statusBarHeight = getAndroidDimenPx(player.context, "status_bar_height")
        val navigationBarHeight = getAndroidDimenPx(player.context, "navigation_bar_height")

        // Do not handle this event if initially it started from status or navigation bars
        val isTouchingStatusBar = initialEvent.y < statusBarHeight
        val isTouchingNavigationBar = initialEvent.y > (binding.root.height - navigationBarHeight)
        if (isTouchingStatusBar || isTouchingNavigationBar) {
            return false
        }

        if (player.currentState == Player.STATE_COMPLETED) {
            return false
        }

        val totalDeltaX = movingEvent.x - initialEvent.x
        val totalDeltaY = movingEvent.y - initialEvent.y
        val gestureState = singleFingerGestureClassifier.update(
            totalDeltaX,
            totalDeltaY,
            isFullscreenSwipeEligible(initialEvent, totalDeltaY)
        )
        if (gestureState == SingleFingerGestureClassifier.State.PENDING) {
            return false
        }

        isMoving = true

        when (gestureState) {
            SingleFingerGestureClassifier.State.HORIZONTAL_SEEK -> {
                if (!playerUi.isFullscreen) {
                    return false
                }
                if (PlayerHelper.isSwipeSeekGestureEnabled(player.context)) {
                    onScrollSeek(distanceX)
                }
                return true
            }

            SingleFingerGestureClassifier.State.FULLSCREEN_SWIPE -> {
                isPendingFullscreenSwipe = true
                return true
            }

            SingleFingerGestureClassifier.State.VERTICAL_ADJUSTMENT -> {
                if (!playerUi.isFullscreen) {
                    return false
                }

                // -- Brightness and Volume control --
                if (getDisplayHalfPortion(initialEvent) == DisplayPortion.RIGHT_HALF) {
                    when (PlayerHelper.getActionForRightGestureSide(player.context)) {
                        player.context.getString(R.string.volume_control_key) ->
                            onScrollVolume(distanceY)

                        player.context.getString(R.string.brightness_control_key) ->
                            onScrollBrightness(distanceY)
                    }
                } else {
                    when (PlayerHelper.getActionForLeftGestureSide(player.context)) {
                        player.context.getString(R.string.volume_control_key) ->
                            onScrollVolume(distanceY)

                        player.context.getString(R.string.brightness_control_key) ->
                            onScrollBrightness(distanceY)
                    }
                }
                return true
            }

            SingleFingerGestureClassifier.State.PENDING -> return false
        }
    }

    private fun isFullscreenSwipeEligible(initialEvent: MotionEvent, totalDeltaY: Float): Boolean {
        if (!PlayerHelper.isFullscreenGestureEnabled(player.context)) {
            return false
        }
        return if (playerUi.isFullscreen) {
            totalDeltaY > 0 && getDisplayPortion(initialEvent) == DisplayPortion.MIDDLE
        } else {
            totalDeltaY < 0
        }
    }

    override fun getDisplayPortion(e: MotionEvent): DisplayPortion {
        return when {
            e.x < binding.root.width / 3.0 -> DisplayPortion.LEFT
            e.x > binding.root.width * 2.0 / 3.0 -> DisplayPortion.RIGHT
            else -> DisplayPortion.MIDDLE
        }
    }

    override fun getDisplayHalfPortion(e: MotionEvent): DisplayPortion {
        return when {
            e.x < binding.root.width / 2.0 -> DisplayPortion.LEFT_HALF
            else -> DisplayPortion.RIGHT_HALF
        }
    }

    companion object {
        private val TAG = MainPlayerGestureListener::class.java.simpleName
        private val DEBUG = MainActivity.DEBUG
        private const val MOVEMENT_THRESHOLD = 40
        private const val SEEK_SWIPE_FACTOR = 100f // ms per pixel
        private const val SEEK_SWIPE_FAST_MULTIPLIER = 10f
        private const val SEEK_SWIPE_FAST_THRESHOLD_MS = 60_000L
        private const val TWO_FINGER_LOCK_THRESHOLD_DP = 12f
        private const val TWO_FINGER_SPEED_STEP_DP = 24f
        private const val PLAYBACK_SPEED_STEP = 0.05f
        private const val MIN_PLAYBACK_SPEED = 0.10f
        private const val MAX_PLAYBACK_SPEED = 3.00f
        private const val NORMAL_PLAYBACK_SPEED = 1.00f

        @JvmStatic
        fun classifyTwoFingerGesture(
            verticalMovement: Float,
            horizontalMovement: Float,
            spanChange: Float,
            threshold: Float
        ): TwoFingerGestureState {
            val vertical = abs(verticalMovement)
            val horizontal = abs(horizontalMovement)
            if (vertical < threshold && horizontal < threshold && spanChange < threshold) {
                return TwoFingerGestureState.PENDING
            }
            return if (vertical > horizontal * 1.25f && vertical > spanChange * 1.25f) {
                TwoFingerGestureState.SPEED
            } else {
                TwoFingerGestureState.IGNORED
            }
        }

        @JvmStatic
        fun calculatePlaybackSpeed(
            startSpeed: Float,
            verticalMovement: Float,
            pixelsPerStep: Float
        ): Float {
            if (pixelsPerStep <= 0f) {
                return startSpeed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
            }
            val rawSpeed = startSpeed +
                verticalMovement / pixelsPerStep * PLAYBACK_SPEED_STEP
            return (round(rawSpeed / PLAYBACK_SPEED_STEP) * PLAYBACK_SPEED_STEP)
                .coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        }

        @JvmStatic
        fun shouldDelegateDownwardSwipeToBottomSheet(
            isFullscreen: Boolean,
            swipeDownToMiniplayerEnabled: Boolean,
            deltaX: Float,
            deltaY: Float
        ): Boolean {
            return swipeDownToMiniplayerEnabled &&
                !isFullscreen &&
                deltaY > MOVEMENT_THRESHOLD &&
                abs(deltaY) > abs(deltaX)
        }
    }

    enum class TwoFingerGestureState {
        IDLE,
        PENDING,
        SPEED,
        IGNORED
    }
}
