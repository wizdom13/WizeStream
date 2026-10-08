package org.schabi.newpipe.player

import android.app.Application
import android.content.Context
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.ChannelSkipPreferences
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ChannelSkipPlaybackControllerTest {
    private val player = mock(Player::class.java)
    private val engine = mock(ExoPlayer::class.java)
    private val queue = mock(PlayQueue::class.java)
    private lateinit var context: Context
    private lateinit var controller: ChannelSkipPlaybackController
    private lateinit var info: StreamInfo
    private var position = 0L
    private var index = 0
    private var playing = true

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        `when`(player.context).thenReturn(context)
        `when`(player.exoPlayer).thenReturn(engine)
        `when`(player.playQueue).thenReturn(queue)
        `when`(player.isPlaying).thenAnswer { playing }
        `when`(engine.duration).thenReturn(100_000L)
        `when`(engine.currentPosition).thenAnswer { position }
        `when`(engine.currentMediaItemIndex).thenAnswer { index }
        `when`(engine.playbackState).thenReturn(Media3Player.STATE_READY)
        `when`(queue.index).thenAnswer { index }
        controller = ChannelSkipPlaybackController(player)
        info = stream("first", "channel")
        begin(info)
    }

    @Test
    fun savingSettingsAfterPlaybackStartsAppliesBothBoundaries() {
        controller.onProgress() // Initial configuration has both skips disabled.
        save(10, 20)
        controller.settingsChanged(info)
        verify(engine).seekTo(10_000L)

        position = 80_000L
        controller.onProgress()
        controller.onProgress()
        verify(engine, times(1)).seekTo(100_000L)
        verify(player, times(1)).saveStreamProgressStateCompleted()
    }

    @Test
    fun savedSettingsApplyWhenPausedPlaybackResumes() {
        controller.onProgress()
        playing = false
        save(10, 20)
        controller.settingsChanged(info)
        verify(engine, never()).seekTo(10_000L)
        playing = true
        controller.onProgress()
        verify(engine).seekTo(10_000L)
    }

    @Test
    fun disablingOutroDuringPlaybackCancelsItsOldBoundary() {
        save(0, 20)
        controller.onProgress()
        save(0, 0)
        controller.settingsChanged(info)
        position = 80_000L
        controller.onProgress()
        verify(engine, never()).seekTo(100_000L)
    }

    @Test
    fun seekingWithinTheVideoStillSkipsTheOutro() {
        save(10, 20)
        controller.onProgress()
        controller.manualSeek(40_000L)
        position = 40_000L
        controller.onProgress()
        position = 80_000L
        controller.onProgress()
        verify(engine).seekTo(100_000L)
    }

    @Test
    fun deliberateSeeksIntoIntroAndOutroArePreservedAfterSaving() {
        save(10, 20)
        controller.onProgress()
        clearInvocations(engine)
        controller.manualSeek(0)
        position = 0
        save(15, 20)
        controller.settingsChanged(info)
        verify(engine, never()).seekTo(15_000L)

        controller.manualSeek(85_000L)
        position = 85_000L
        controller.onProgress()
        verify(engine, never()).seekTo(100_000L)
        verify(player, never()).saveStreamProgressStateCompleted()
    }

    @Test
    fun playlistTransitionLoadsTheNextChannelsSettings() {
        save(10, 20)
        controller.onProgress()
        position = 80_000L
        controller.onProgress()
        val next = stream("second", "other-channel")
        ChannelSkipPreferences.save(context, next, 5, 10)
        index = 1
        position = 0
        controller.automaticTransition()
        begin(next)
        controller.onProgress()
        verify(engine).seekTo(5_000L)
        position = 90_000L
        controller.onProgress()
        verify(engine, times(2)).seekTo(100_000L)
    }

    @Test
    fun repeatTransitionResetsManualOverridesForTheSameVideo() {
        save(10, 20)
        controller.manualSeek(85_000L)
        position = 85_000L
        controller.onProgress()
        verify(engine, never()).seekTo(100_000L)
        controller.automaticTransition()
        position = 0
        controller.onProgress()
        verify(engine).seekTo(10_000L)
        position = 80_000L
        controller.onProgress()
        verify(engine).seekTo(100_000L)
    }

    @Test
    fun savingSettingsDoesNotSkipAnIntroOnRestoredPlayback() {
        begin(info, restoring = true)
        controller.onProgress()
        save(10, 20)
        controller.settingsChanged(info)
        verify(engine, never()).seekTo(10_000L)
        position = 80_000L
        controller.onProgress()
        verify(engine).seekTo(100_000L)
    }

    @Test
    fun settingsFromAnOldDialogDoNotReconfigureAnotherChannel() {
        save(10, 20)
        controller.onProgress()
        val previous = info
        info = stream("second", "other-channel")
        begin(info)
        controller.onProgress()
        ChannelSkipPreferences.save(context, previous, 15, 25)
        controller.settingsChanged(previous)
        verify(engine, never()).seekTo(15_000L)
    }

    private fun save(start: Int, end: Int) = ChannelSkipPreferences.save(context, info, start, end)

    private fun begin(stream: StreamInfo, restoring: Boolean = false) {
        val item = PlayQueueItem(stream)
        `when`(player.currentItem).thenReturn(item)
        `when`(player.currentStreamInfo).thenReturn(Optional.of(stream))
        controller.begin(item, restoring)
    }

    private fun stream(video: String, channel: String): StreamInfo = StreamInfo(0, video, "https://example.test/$video", video).apply {
        streamType = StreamType.VIDEO_STREAM
        uploaderName = channel
        uploaderUrl = "https://example.test/$channel"
    }
}
