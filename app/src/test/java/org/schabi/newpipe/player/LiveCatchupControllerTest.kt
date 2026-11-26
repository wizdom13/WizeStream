/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import android.view.MotionEvent
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener
import org.schabi.newpipe.player.gesture.DisplayPortion
import org.schabi.newpipe.player.helper.ChannelPlaybackProfileManager
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.recovery.QueueRecoveryController
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.ui.PlayerUiList
import org.schabi.newpipe.player.ui.VideoPlayerUi
import org.schabi.newpipe.player.video.VideoAdjustmentController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveCatchupControllerTest {
    @Test
    fun acceleratedDvrCatchesLiveAtNormalSpeedWithoutChangingPreferencesOrPitch() {
        for (speed in listOf(1.25f, 1.5f, 2f)) {
            val fixture = Fixture(speed)
            val savedPreferences = fixture.preferences.all.toMap()

            fixture.progress.trigger()
            fixture.assertSpeed(speed)
            verify(fixture.engine, never()).seekToDefaultPosition()

            fixture.position = 119_500
            fixture.progress.trigger()

            fixture.assertSpeed(1f)
            assertEquals(1.2f, fixture.appliedParameters.pitch, 0f)
            assertEquals(savedPreferences, fixture.preferences.all)
            verify(fixture.engine).seekToDefaultPosition()
            verify(fixture.player, never()).reloadPlayQueueManager()
        }
    }

    @Test
    fun bufferingNearLiveCompletesThroughTheRealListenerButAStallBehindLiveDoesNot() {
        val fixture = Fixture()
        fixture.state = Media3Player.STATE_BUFFERING

        fixture.listener.onEvents(fixture.engine)
        fixture.assertSpeed(2f)
        verify(fixture.engine, never()).seekToDefaultPosition()

        fixture.position = 120_000
        fixture.listener.onEvents(fixture.engine)

        fixture.assertSpeed(1f)
        verify(fixture.engine).seekToDefaultPosition()
    }

    @Test
    fun movingWindowAndRepeatedMetadataDoNotRestartCatchupOrSeekRepeatedly() {
        val fixture = Fixture()
        // The position and safe live default are both relative to the moving window's start.
        fixture.window.defaultPositionUs = 60_000_000
        fixture.window.durationUs = 70_000_000
        fixture.position = 40_000
        fixture.listener.onTimelineChanged(fixture.timeline, Media3Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        fixture.assertSpeed(2f)

        fixture.position = 59_500
        fixture.listener.onTimelineChanged(fixture.timeline, Media3Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        fixture.assertSpeed(1f)
        repeat(3) {
            fixture.window.defaultPositionUs += 5_000_000
            fixture.window.durationUs += 5_000_000
            fixture.parameters.applySpeedProfile(fixture.info)
            fixture.listener.onTimelineChanged(fixture.timeline, Media3Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            fixture.progress.trigger()
            fixture.assertSpeed(1f)
        }
        verify(fixture.engine, times(1)).seekToDefaultPosition()
        assertEquals(2f, ChannelPlaybackProfileManager.getSpeed(fixture.context, fixture.item)!!, 0f)
    }

    @Test
    fun liveTimelineKeepsTheCompletionLatchWhenExtractorLabelsItOrdinaryVideo() {
        val fixture = Fixture()
        `when`(fixture.item.streamType).thenReturn(StreamType.VIDEO_STREAM)
        fixture.info.streamType = StreamType.VIDEO_STREAM
        fixture.parameters.setStream(fixture.item)
        fixture.position = 120_000
        fixture.parameters.updateLiveCatchup()
        fixture.assertSpeed(1f)

        repeat(3) {
            fixture.window.defaultPositionUs += 5_000_000
            fixture.window.durationUs += 5_000_000
            fixture.parameters.applySpeedProfile(fixture.item)
            fixture.parameters.applySpeedProfile(fixture.info)
            fixture.listener.onTimelineChanged(fixture.timeline, Media3Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            fixture.assertSpeed(1f)
        }

        verify(fixture.engine, times(1)).seekToDefaultPosition()
    }

    @Test
    fun emptyReplacementTimelineAndHoldReleaseKeepTheCompletedLiveSpeed() {
        val fixture = Fixture()
        `when`(fixture.item.streamType).thenReturn(StreamType.VIDEO_STREAM)
        fixture.info.streamType = StreamType.VIDEO_STREAM
        fixture.parameters.setStream(fixture.item)
        val savedPreferences = fixture.preferences.all.toMap()
        val gesture = fixture.startHold()
        fixture.position = 120_000
        fixture.parameters.updateLiveCatchup()
        fixture.assertSpeed(1f)

        // Engine/source rebuilds can clear the playing item and timeline before metadata
        // reapplies the profile. The retained logical queue item is still the same live.
        `when`(fixture.player.currentItem).thenReturn(null)
        `when`(fixture.engine.currentTimeline).thenReturn(Timeline.EMPTY)
        fixture.parameters.applySpeedProfile(fixture.item)
        fixture.parameters.applySpeedProfile(fixture.info)
        gesture.release()
        fixture.assertSpeed(1f)

        `when`(fixture.player.currentItem).thenReturn(fixture.item)
        `when`(fixture.engine.currentTimeline).thenReturn(fixture.timeline)
        fixture.parameters.applySpeedProfile(fixture.info)
        fixture.listener.onTimelineChanged(fixture.timeline, Media3Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        fixture.assertSpeed(1f)
        verify(fixture.engine, times(1)).seekToDefaultPosition()
        assertEquals(savedPreferences, fixture.preferences.all)
    }

    @Test
    fun placeholderAndIncompleteWindowBoundsCannotDiscardCompletion() {
        val changes: List<(Fixture) -> Unit> = listOf(
            {
                it.window.isPlaceholder = true
                it.window.isDynamic = false
            },
            {
                it.window.durationUs = C.TIME_UNSET
                it.window.isDynamic = false
            },
            {
                it.window.defaultPositionUs = C.TIME_UNSET
                it.window.isDynamic = false
            },
            {
                `when`(it.engine.currentMediaItem).thenReturn(null)
                it.window.isDynamic = false
            }
        )
        changes.forEach { change ->
            val fixture = Fixture()
            `when`(fixture.item.streamType).thenReturn(StreamType.VIDEO_STREAM)
            fixture.info.streamType = StreamType.VIDEO_STREAM
            fixture.parameters.setStream(fixture.item)
            fixture.position = 120_000
            fixture.parameters.updateLiveCatchup()
            change(fixture)

            fixture.parameters.applySpeedProfile(fixture.info)
            fixture.parameters.restoreSpeedAfterTemporaryChange(2f)

            fixture.assertSpeed(1f)
            verify(fixture.engine, times(1)).seekToDefaultPosition()
        }
    }

    @Test
    fun anExplicitSpeedSelectionAfterRewindStartsAnotherCatchup() {
        val fixture = Fixture()
        fixture.position = 120_000
        fixture.parameters.updateLiveCatchup()
        fixture.assertSpeed(1f)

        fixture.position = 20_000
        fixture.parameters.applySpeedProfile(fixture.item)
        fixture.assertSpeed(1f)
        fixture.parameters.setParameters(1.5f, 1.2f, false)
        fixture.parameters.updateLiveCatchup()
        fixture.assertSpeed(1.5f)

        fixture.position = 119_900
        fixture.parameters.updateLiveCatchup()
        fixture.assertSpeed(1f)
        verify(fixture.engine, times(2)).seekToDefaultPosition()
        assertEquals(1.5f, ChannelPlaybackProfileManager.getSpeed(fixture.context, fixture.item)!!, 0f)
    }

    @Test
    fun goLiveAlsoEndsAccelerationWhilePausedWithoutResumingPlayback() {
        val fixture = Fixture()
        fixture.playWhenReady = false

        PlayerSeekController(fixture.player).seekToDefault()

        fixture.assertSpeed(1f)
        verify(fixture.engine).seekToDefaultPosition()
        verify(fixture.engine, never()).play()
        assertEquals(2f, ChannelPlaybackProfileManager.getSpeed(fixture.context, fixture.item)!!, 0f)
    }

    @Test
    fun behindLiveWindowRecoveryRestoresLiveSpeedWithoutReplacingTheDvrSource() {
        val fixture = Fixture()
        val errors = PlayerErrorController(
            fixture.player,
            mock(PlayerEventDispatcher::class.java),
            mock(VideoPlaybackResolver::class.java)
        )

        errors.onPlayerError(PlaybackException("Behind live window", null, PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW))

        fixture.assertSpeed(1f)
        verify(fixture.engine).seekToDefaultPosition()
        verify(fixture.engine).prepare()
        verify(fixture.player, never()).reloadPlayQueueManager()
        errors.resetRecovery()
    }

    @Test
    fun normalAndSlowerSpeedsDoNotSeekAtLive() {
        for (speed in listOf(0.75f, 1f)) {
            val fixture = Fixture(speed)
            fixture.position = 120_000
            fixture.parameters.updateLiveCatchup()
            fixture.assertSpeed(speed)
            verify(fixture.engine, never()).seekToDefaultPosition()
        }
    }

    @Test
    fun pausedIdleEndedAdsAndNonDvrWindowsDoNotAutoSeek() {
        val changes: List<(Fixture) -> Unit> = listOf(
            { it.playWhenReady = false },
            { it.state = Media3Player.STATE_IDLE },
            { it.state = Media3Player.STATE_ENDED },
            { `when`(it.engine.isPlayingAd).thenReturn(true) },
            { it.window.isSeekable = false },
            { it.window.isDynamic = false },
            { it.window.liveConfiguration = null }
        )
        changes.forEach { change ->
            val fixture = Fixture()
            fixture.position = 120_000
            change(fixture)
            fixture.parameters.updateLiveCatchup()
            fixture.assertSpeed(2f)
            verify(fixture.engine, never()).seekToDefaultPosition()
        }
    }

    @Test
    fun unknownOrInvalidTimelineBoundsNeverTriggerCatchup() {
        val changes: List<(Fixture) -> Unit> = listOf(
            { it.window.defaultPositionUs = C.TIME_UNSET },
            { it.window.durationUs = C.TIME_UNSET },
            { it.window.defaultPositionUs = it.window.durationUs + 1_000_000 },
            { `when`(it.engine.currentTimeline).thenReturn(Timeline.EMPTY) },
            { `when`(it.engine.currentMediaItemIndex).thenReturn(-1) },
            { `when`(it.engine.currentMediaItemIndex).thenReturn(1) },
            { it.position = -1 }
        )
        changes.forEach { change ->
            val fixture = Fixture()
            fixture.position = 120_000
            change(fixture)
            fixture.parameters.updateLiveCatchup()
            fixture.assertSpeed(2f)
            verify(fixture.engine, never()).seekToDefaultPosition()
        }
    }

    @Test
    fun profileForTheNextItemCannotSeekTheStillPlayingPreviousItem() {
        val fixture = Fixture()
        val next = mock(PlayQueueItem::class.java)
        `when`(next.url).thenReturn("https://example.org/next")
        `when`(next.streamType).thenReturn(StreamType.LIVE_STREAM)
        fixture.position = 120_000
        fixture.parameters.setStream(next)
        `when`(fixture.player.currentItem).thenReturn(next)

        fixture.parameters.updateLiveCatchup()

        fixture.assertSpeed(2f)
        verify(fixture.engine, never()).seekToDefaultPosition()
    }

    @Test
    fun confirmedStaticWindowsAndNewPlaybackSessionsUseTheirOwnPreferredSpeed() {
        for (type in listOf(StreamType.POST_LIVE_STREAM, StreamType.VIDEO_STREAM)) {
            val fixture = Fixture()
            fixture.position = 120_000
            fixture.parameters.updateLiveCatchup()
            fixture.assertSpeed(1f)

            fixture.window.isDynamic = false
            fixture.window.liveConfiguration = null
            fixture.info.streamType = type
            `when`(fixture.item.streamType).thenReturn(type)
            fixture.parameters.applySpeedProfile(fixture.info)
            fixture.parameters.updateLiveCatchup()
            fixture.parameters.restoreSpeedAfterTemporaryChange(2f)
            fixture.assertSpeed(2f)

            fixture.parameters.clearLiveCatchup()
            fixture.parameters.applySpeedProfile(fixture.item)
            fixture.assertSpeed(2f)
            verify(fixture.engine, times(1)).seekToDefaultPosition()
        }
    }

    @Test
    fun releasingAHoldAfterCatchupCannotRestoreAccelerationEvenIfTheWindowMoved() {
        val fixture = Fixture()
        val savedPreferences = fixture.preferences.all.toMap()
        val gesture = fixture.startHold()
        fixture.assertSpeed(3f)
        fixture.position = 120_000
        fixture.parameters.updateLiveCatchup()
        fixture.assertSpeed(1f)

        // Pausing or a new manifest can move the default outside the proximity guard before
        // the finger lifts. Release must still respect the catch-up completion.
        fixture.window.defaultPositionUs += 5_000_000
        fixture.window.durationUs += 5_000_000
        fixture.playWhenReady = false
        gesture.release()
        fixture.assertSpeed(1f)
        fixture.parameters.applySpeedProfile(fixture.item)
        fixture.assertSpeed(1f)
        verify(fixture.engine, times(1)).seekToDefaultPosition()
        assertEquals(savedPreferences, fixture.preferences.all)

        // A new hold after rewinding remains a deliberate, temporary acceleration.
        fixture.playWhenReady = true
        fixture.position = 20_000
        val nextHold = fixture.startHold()
        fixture.assertSpeed(3f)
        nextHold.release()
        fixture.assertSpeed(1f)
    }

    @Test
    fun temporarySpeedRestorationPreservesSlowerLiveSpeedAndOnDemandSpeed() {
        val slower = Fixture(0.75f)
        val slowerHold = slower.startHold()
        slower.position = 120_000
        slower.parameters.updateLiveCatchup()
        slower.assertSpeed(1f)
        slowerHold.release()
        slower.assertSpeed(0.75f)

        val onDemand = Fixture()
        onDemand.window.liveConfiguration = null
        onDemand.window.isDynamic = false
        `when`(onDemand.item.streamType).thenReturn(StreamType.VIDEO_STREAM)
        onDemand.parameters.setStream(onDemand.item)
        val videoHold = onDemand.startHold()
        onDemand.position = 120_000
        onDemand.parameters.updateLiveCatchup()
        onDemand.assertSpeed(3f)
        videoHold.release()
        onDemand.assertSpeed(2f)
        verify(onDemand.engine, never()).seekToDefaultPosition()
    }

    @Test
    fun teardownWithAnAbsentEngineDoesNotSeekOrChangeSavedPreferences() {
        val fixture = Fixture()
        val preferences = fixture.preferences.all.toMap()
        `when`(fixture.player.getExoPlayer()).thenReturn(null)

        fixture.parameters.updateLiveCatchup()
        fixture.parameters.prepareForLiveSeek()
        fixture.parameters.clearLiveCatchup()

        assertEquals(preferences, fixture.preferences.all)
        verify(fixture.engine, never()).seekToDefaultPosition()
    }

    private class Fixture(speed: Float = 2f) {
        val context: Context = RuntimeEnvironment.getApplication()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val player = mock(Player::class.java)
        val engine = mock(ExoPlayer::class.java)
        val item = mock(PlayQueueItem::class.java)
        val queue = mock(PlayQueue::class.java)
        val timeline = mock(Timeline::class.java)
        val window = Timeline.Window().apply {
            isSeekable = true
            isDynamic = true
            liveConfiguration = MediaItem.LiveConfiguration.Builder().build()
            defaultPositionUs = 120_000_000
            durationUs = 130_000_000
        }
        val info = StreamInfo(0, "live", "https://example.org/live", "Live").apply {
            streamType = StreamType.LIVE_STREAM
            uploaderUrl = "https://example.org/channel"
        }
        var appliedParameters = PlaybackParameters(speed, 1.2f)
        var position = 20_000L
        var playWhenReady = true
        var state = Media3Player.STATE_READY
        val parameters = PlaybackParametersController(player)
        val progress = PlayerProgressController(
            player,
            mock(PlayerHistoryController::class.java),
            mock(SponsorBlockPlaybackController::class.java),
            mock(PlayerEventDispatcher::class.java)
        )
        val listener = PlayerMedia3ListenerController(
            player,
            mock(PlayerAudioController::class.java),
            mock(PlayerMetadataController::class.java),
            parameters,
            mock(SponsorBlockPlaybackController::class.java),
            mock(PlayerErrorController::class.java),
            mock(SleepTimerPlaybackController::class.java)
        )

        init {
            preferences.edit().clear()
                .putFloat(context.getString(R.string.playback_speed_key), 1.75f)
                .putFloat(context.getString(R.string.playback_pitch_key), 1.2f)
                .putString(context.getString(R.string.hold_to_speed_key), "3.0")
                .apply()
            `when`(player.context).thenReturn(context)
            `when`(player.prefs).thenReturn(preferences)
            `when`(player.exoPlayer).thenReturn(engine)
            `when`(player.getExoPlayer()).thenReturn(engine)
            `when`(player.currentItem).thenReturn(item)
            `when`(player.playQueue).thenReturn(queue)
            `when`(queue.item).thenReturn(item)
            `when`(player.currentStreamInfo).thenReturn(Optional.of(info))
            `when`(player.playbackSpeed).thenAnswer { appliedParameters.speed }
            `when`(player.playbackPitch).thenAnswer { appliedParameters.pitch }
            `when`(player.UIs()).thenReturn(PlayerUiList())
            `when`(player.channelSkipController).thenReturn(mock(ChannelSkipPlaybackController::class.java))
            `when`(player.queueRecovery).thenReturn(mock(QueueRecoveryController::class.java))
            `when`(player.abRepeat).thenReturn(mock(ABRepeat::class.java))
            `when`(player.videoAdjustments).thenReturn(mock(VideoAdjustmentController::class.java))
            `when`(item.serviceId).thenReturn(0)
            `when`(item.url).thenReturn(info.url)
            `when`(item.uploaderUrl).thenReturn(info.uploaderUrl)
            `when`(item.streamType).thenReturn(StreamType.LIVE_STREAM)
            val tag = mock(MediaItemTag::class.java)
            `when`(tag.serviceId).thenReturn(info.serviceId)
            `when`(tag.streamUrl).thenReturn(info.url)
            `when`(engine.currentMediaItem).thenReturn(
                MediaItem.fromUri(info.url).buildUpon().setTag(tag).build()
            )
            `when`(engine.currentTimeline).thenReturn(timeline)
            `when`(timeline.windowCount).thenReturn(1)
            `when`(timeline.getWindow(eq(0), any(Timeline.Window::class.java))).thenReturn(window)
            `when`(engine.currentPosition).thenAnswer { position }
            `when`(engine.playWhenReady).thenAnswer { playWhenReady }
            `when`(engine.playbackState).thenAnswer { state }
            `when`(engine.playbackParameters).thenAnswer { appliedParameters }
            doAnswer {
                appliedParameters = it.getArgument(0)
                null
            }.`when`(engine).setPlaybackParameters(any(PlaybackParameters::class.java))
            doAnswer {
                parameters.updateLiveCatchup()
                null
            }.`when`(player).updateLiveCatchup()
            doAnswer {
                parameters.prepareForLiveSeek()
                null
            }.`when`(player).prepareForLiveSeek()
            doAnswer {
                parameters.setSpeedTemporarily(it.getArgument(0))
                null
            }.`when`(player).setPlaybackSpeedTemporarily(anyFloat())
            doAnswer {
                parameters.restoreSpeedAfterTemporaryChange(it.getArgument(0))
                null
            }.`when`(player).restorePlaybackSpeedAfterTemporaryChange(anyFloat())
            parameters.setStream(item)
            parameters.setParameters(speed, 1.2f, false)
        }

        fun assertSpeed(expected: Float) = assertEquals(expected, appliedParameters.speed, 0f)

        fun startHold(): HoldGesture {
            val ui = mock(VideoPlayerUi::class.java)
            `when`(ui.player).thenReturn(player)
            `when`(ui.binding).thenReturn(mock(PlayerBinding::class.java))
            val gesture = HoldGesture(ui)
            val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            try {
                gesture.onLongPress(event)
            } finally {
                event.recycle()
            }
            return gesture
        }
    }

    private class HoldGesture(ui: VideoPlayerUi) : BasePlayerGestureListener(ui) {
        fun release() = restoreHoldToSpeed()

        override fun getDisplayPortion(e: MotionEvent) = DisplayPortion.MIDDLE

        override fun getDisplayHalfPortion(e: MotionEvent) = DisplayPortion.MIDDLE
    }
}
