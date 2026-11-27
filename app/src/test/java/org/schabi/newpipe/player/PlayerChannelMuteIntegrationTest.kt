/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import java.util.Optional
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.mediaitem.ExceptionTag
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediaitem.PlaceholderTag
import org.schabi.newpipe.player.mediasource.LoadedMediaSource
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.ui.PlayerUi
import org.schabi.newpipe.player.ui.PlayerUiList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlayerChannelMuteIntegrationTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun savedMuteIsAppliedWhenQueueSelectsTheItemAndSurvivesMetadataAndEngineReplacement() {
        saveMute("channel-a")
        val fixture = Fixture()
        val item = queueItem("video-a", "channel-a")

        fixture.audio.beginItem(item)
        assertTrue(fixture.audio.isMuted)
        assertTrue(fixture.appliedVolumes.last())
        verify(fixture.focus).abandonAudioFocus()
        fixture.mediaItem = mediaItem("video-a", "channel-a")
        fixture.audio.onMediaItemTransition(fixture.mediaItem)
        fixture.audio.onMetadata(info("video-a", "channel-a"))
        assertTrue(fixture.audio.isMuted)
        fixture.audio.onAudioSessionChanged(12)
        assertTrue(fixture.appliedVolumes.last())
        verify(fixture.ui).onMuteUnmuteChanged(true)
        fixture.audio.releaseAudioSession()
    }

    @Test
    fun media3TransitionAppliesTheNextChannelsMuteBeforeBatchedMetadataUpdates() {
        saveMute("channel-b")
        val fixture = Fixture()
        fixture.audio.beginItem(queueItem("video-a", "channel-a"))
        fixture.mediaItem = mediaItem("video-b", "channel-b")
        val metadata = mock(PlayerMetadataController::class.java)
        val listener = PlayerMedia3ListenerController(
            fixture.player,
            fixture.audio,
            metadata,
            mock(PlaybackParametersController::class.java),
            mock(SponsorBlockPlaybackController::class.java),
            mock(PlayerErrorController::class.java),
            mock(SleepTimerPlaybackController::class.java)
        )
        clearInvocations(fixture.player, metadata)

        listener.onEvents(fixture.engine)

        assertTrue(fixture.audio.isMuted)
        inOrder(fixture.player, metadata).apply {
            verify(fixture.player).applyPlayerVolume()
            verify(metadata).onEvents(fixture.engine)
        }
        fixture.mediaItem = mediaItem("video-c", "channel-c")
        fixture.audio.onMediaItemTransition(fixture.mediaItem)
        assertFalse(fixture.audio.isMuted)
        assertFalse(fixture.appliedVolumes.last())
    }

    @Test
    fun aClickDuringAQueueTransitionWritesTheNewChannelAndDoesNotChangePausedState() {
        val fixture = Fixture()
        fixture.audio.beginItem(queueItem("video-a", "channel-a"))
        fixture.playWhenReady = false
        fixture.mediaItem = mediaItem("video-b", "channel-b")
        fixture.audio.toggleMute()
        assertTrue(fixture.audio.isMuted)
        clearInvocations(fixture.focus)
        fixture.audio.toggleMute()
        assertFalse(fixture.audio.isMuted)
        verify(fixture.focus, never()).requestAudioFocus()
        verify(fixture.engine, never()).play()
        fixture.audio.toggleMute()

        ChannelPlaybackMuteController(context) {}.also {
            it.select(0, "another-a", "channel-a")
            assertFalse(it.isMuted)
            it.select(0, "another-b", "channel-b")
            assertTrue(it.isMuted)
        }
    }

    @Test
    fun delayedTransitionFromTheReleasedSourceCannotOverwriteTheNewQueueChoice() {
        saveMute("channel-b")
        val fixture = Fixture()
        val oldSource = mediaItem("video-a", "channel-a")
        fixture.mediaItem = oldSource
        fixture.audio.beginItem(queueItem("video-a", "channel-a"))
        // initPlayback releases the old engine before selecting the next queue item.
        fixture.mediaItem = null
        fixture.audio.beginItem(queueItem("video-b", "channel-b"))
        fixture.audio.onMediaItemTransition(oldSource)
        fixture.audio.onMetadata(info("video-a", "channel-a"))
        assertTrue(fixture.audio.isMuted)
        assertTrue(fixture.appliedVolumes.last())
    }

    @Test
    fun clicksDuringPlaceholderLoadingSurviveCanonicalizedStreamMetadata() {
        val fixture = Fixture()
        val queueItem = queueItem("original-url", "")
        fixture.audio.beginItem(queueItem)
        fixture.mediaItem = PlaceholderTag.EMPTY.asMediaItem()
        fixture.audio.toggleMute()
        assertTrue(fixture.audio.isMuted)
        val source = mock(LoadedMediaSource::class.java)
        `when`(source.stream).thenReturn(queueItem)
        fixture.mediaItem = mediaItem("canonical-url", "channel-a", source)

        fixture.audio.onMediaItemTransition(fixture.mediaItem)
        fixture.audio.onMetadata(info("canonical-url", "channel-a"))

        assertTrue(fixture.audio.isMuted)
        assertTrue(fixture.audio.hasChannelMuteIdentity)
        ChannelPlaybackMuteController(context) {}.also {
            it.select(0, "another-video", "channel-a")
            assertTrue(it.isMuted)
        }
    }

    @Test
    fun failedSourceExtrasCannotCrashOrClearTheSelectedChannelsMute() {
        saveMute("channel-a")
        val fixture = Fixture()
        val item = queueItem("video-a", "channel-a")
        fixture.audio.beginItem(item)
        // ExceptionTag casts its extras directly, and a failure does not carry LoadedMediaSource.
        val failedTag = ExceptionTag.of(item, listOf(IllegalStateException("Unavailable"))).withExtras(Any())
        fixture.mediaItem = MediaItem.fromUri("video-a").buildUpon().setTag(failedTag).build()

        fixture.audio.onMediaItemTransition(fixture.mediaItem)

        assertTrue(fixture.audio.isMuted)
        assertTrue(fixture.audio.hasChannelMuteIdentity)
    }

    private fun saveMute(channel: String) = ChannelPlaybackMuteController(context) {}.apply {
        select(0, "saved-video", channel)
        toggle()
    }

    private inner class Fixture {
        val player = mock(Player::class.java)
        val engine = mock(ExoPlayer::class.java)
        val focus = mock(AudioReactor::class.java)
        val ui = mock(PlayerUi::class.java)
        var mediaItem: MediaItem? = null
        var playWhenReady = true
        val appliedVolumes = mutableListOf<Boolean>()
        val audio: PlayerAudioController

        init {
            `when`(player.context).thenReturn(context)
            `when`(player.prefs).thenReturn(PreferenceManager.getDefaultSharedPreferences(context))
            `when`(player.exoPlayer).thenReturn(engine)
            `when`(player.getExoPlayer()).thenReturn(engine)
            `when`(player.audioReactor).thenReturn(focus)
            `when`(player.playWhenReady).thenAnswer { playWhenReady }
            `when`(engine.currentMediaItem).thenAnswer { mediaItem }
            `when`(player.UIs()).thenReturn(PlayerUiList(ui))
            audio = PlayerAudioController(player)
            doAnswer {
                appliedVolumes += audio.isMuted
                null
            }.`when`(player).applyPlayerVolume()
        }
    }

    private fun queueItem(url: String, channel: String) = mock(PlayQueueItem::class.java).also {
        `when`(it.serviceId).thenReturn(0)
        `when`(it.url).thenReturn(url)
        `when`(it.uploaderUrl).thenReturn(channel)
    }

    private fun info(url: String, channel: String) = StreamInfo(0, url, url, url).apply { uploaderUrl = channel }

    private fun mediaItem(url: String, channel: String, source: LoadedMediaSource? = null): MediaItem {
        val tag = mock(MediaItemTag::class.java)
        `when`(tag.serviceId).thenReturn(0)
        `when`(tag.streamUrl).thenReturn(url)
        `when`(tag.uploaderUrl).thenReturn(channel)
        `when`(tag.maybeStreamInfo).thenReturn(Optional.of(info(url, channel)))
        `when`(tag.getMaybeExtras(Any::class.java)).thenReturn(Optional.ofNullable(source))
        return MediaItem.fromUri(url).buildUpon().setTag(tag).build()
    }
}
