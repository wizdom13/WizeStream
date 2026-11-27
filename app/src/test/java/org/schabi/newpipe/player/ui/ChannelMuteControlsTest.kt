/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35], application = Application::class)
class ChannelMuteControlsTest {
    private val context get() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.LightTheme)

    @Before
    fun clearPreferences() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun overlayMuteRemainsBesideFullscreenWithOptionalToolsHiddenInBothDirections() {
        for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            for ((width, height) in listOf(320 to 180, 640 to 360)) {
                val binding = binding()
                binding.root.layoutDirection = direction
                binding.playbackControlRoot.visibility = View.VISIBLE
                binding.secondaryControls.visibility = View.GONE
                PlayerActionVisibility.save(context, setOf("mute"))
                binding.switchMute.visibility = View.GONE
                binding.screenRotationButton.visibility = View.VISIBLE
                ChannelMuteControls.show(binding, false)
                measure(binding.root, width, height)

                val mute = binding.channelMuteButton
                val fullscreen = binding.screenRotationButton
                assertEquals(View.VISIBLE, mute.visibility)
                assertEquals(View.VISIBLE, fullscreen.visibility)
                assertTrue(mute.width >= dp(48))
                assertTrue(mute.height >= dp(48))
                assertTrue(mute.right <= fullscreen.left)
                assertTrue(fullscreen.right <= binding.bottomControls.width)
                assertTrue(binding.playbackSeekBar.width > 0)
                assertEquals(mute.parent, fullscreen.parent)
            }
        }
    }

    @Test
    fun minimumPopupKeepsSoundAndFullscreenReachableWithoutShrinkingTheTimeline() {
        for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            for (width in listOf(150, 180, 320)) {
                val height = PopupVideoSizing.fit(width, 1080, 1920, 150, 16f / 9f, 96).height
                val binding = binding()
                binding.root.layoutDirection = direction
                binding.playbackControlRoot.visibility = View.VISIBLE
                binding.topControls.orientation = LinearLayout.HORIZONTAL
                binding.primaryControls.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.metadataView.visibility = View.GONE
                binding.secondaryControls.visibility = View.VISIBLE
                binding.screenRotationButton.visibility = View.GONE
                binding.bottomControls.setPadding(dp(6), 0, dp(6), 0)
                binding.playbackCurrentTime.text = "1:06:29"
                binding.playbackEndTime.text = "1:23:49"
                measure(binding.root, width, height)
                val timelineWidth = binding.playbackSeekBar.width

                ChannelMuteControls.show(binding, true)
                measure(binding.root, width, height)

                val actions = binding.popupPlaybackActions
                val mute = binding.popupMuteButton
                val fullscreen = binding.popupFullscreenButton
                assertEquals(View.VISIBLE, actions.visibility)
                assertEquals(View.GONE, binding.channelMuteButton.visibility)
                assertTrue(actions.left >= 0)
                assertTrue(actions.right <= binding.root.width)
                assertTrue(actions.bottom <= binding.root.height)
                assertTrue(actions.bottom <= binding.bottomControls.top)
                assertTrue(mute.width >= dp(48))
                assertTrue(mute.height >= dp(48))
                assertTrue(fullscreen.width >= dp(48))
                assertTrue(mute.right <= fullscreen.left || fullscreen.right <= mute.left)
                assertTrue(binding.popupToolsScroller.width > 0)
                assertTrue(binding.topControls.width >= binding.popupToolsScroller.width)
                assertEquals(timelineWidth, binding.playbackSeekBar.width)
                assertTrue(timelineWidth > 0)
                assertEquals(View.VISIBLE, binding.playbackCurrentTime.visibility)
                assertEquals(View.VISIBLE, binding.playbackEndTime.visibility)
                assertEquals(R.id.popupFullscreenButton, binding.playbackSeekBar.nextFocusUpId)
                var muteClicks = 0
                var playbackClicks = 0
                binding.popupMuteButton.setOnClickListener { muteClicks++ }
                binding.playPauseButton.setOnClickListener { playbackClicks++ }
                tap(binding, binding.popupMuteButton)
                assertEquals(1, muteClicks)
                assertEquals(0, playbackClicks)
            }
        }
    }

    @Test
    fun switchingBackToMainRestoresTheOriginalControlsParentAndFocusRoute() {
        val binding = binding()
        val originalParams = binding.topControls.layoutParams
        val originalSeekUp = binding.playbackSeekBar.nextFocusUpId
        repeat(2) {
            ChannelMuteControls.show(binding, true)
            assertSame(binding.popupToolsScroller, binding.topControls.parent)
            ChannelMuteControls.show(binding, false)
            assertSame(binding.playbackWindowRoot, binding.topControls.parent)
            assertSame(originalParams, binding.topControls.layoutParams)
            assertEquals(originalSeekUp, binding.playbackSeekBar.nextFocusUpId)
            assertEquals(View.VISIBLE, binding.channelMuteButton.visibility)
            assertEquals(View.GONE, binding.popupPlaybackActions.visibility)
            assertEquals(View.GONE, binding.popupToolsScroller.visibility)
        }
    }

    @Test
    fun clicksAndAccessibleActionReflectMutedChannelAndDetachCleanly() {
        val binding = binding()
        var muted = false
        var fullscreenClicks = 0
        ChannelMuteControls.bind(binding, {
            muted = !muted
            ChannelMuteControls.update(binding, muted, true)
        }, { fullscreenClicks++ })
        ChannelMuteControls.show(binding, true)
        ChannelMuteControls.update(binding, false, true)
        assertEquals(context.getString(R.string.channel_playback_mute), binding.channelMuteButton.contentDescription)
        binding.popupMuteButton.performClick()
        assertTrue(muted)
        assertTrue(binding.channelMuteButton.isActivated)
        assertTrue(binding.popupMuteButton.isActivated)
        assertEquals(context.getString(R.string.channel_playback_unmute), binding.channelMuteButton.contentDescription)
        assertEquals(context.getString(R.string.unmute), binding.switchMute.contentDescription)
        binding.channelMuteButton.performClick()
        binding.popupMuteButton.performClick()
        assertTrue(muted)
        binding.popupMuteButton.performClick()
        assertFalse(muted)
        binding.popupFullscreenButton.performClick()
        assertEquals(1, fullscreenClicks)
        ChannelMuteControls.bind(binding, null, null)
        binding.channelMuteButton.performClick()
        binding.popupMuteButton.performClick()
        binding.popupFullscreenButton.performClick()
        assertFalse(muted)
        assertEquals(1, fullscreenClicks)
        ChannelMuteControls.update(binding, true, false)
        assertEquals(context.getString(R.string.unmute), binding.channelMuteButton.contentDescription)
    }

    private fun binding() = PlayerBinding.inflate(LayoutInflater.from(context))

    private fun measure(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(dp(width), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(height), View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun tap(binding: PlayerBinding, target: View) {
        val width = binding.root.width
        val height = binding.root.height
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            activity.get().setContentView(binding.root)
            binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            binding.root.layout(0, 0, width, height)
            val bounds = Rect(0, 0, target.width, target.height)
            binding.root.offsetDescendantRectToMyCoords(target, bounds)
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 1, action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                binding.root.dispatchTouchEvent(event)
                event.recycle()
            }
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            activity.pause().stop().destroy()
        }
    }
}
