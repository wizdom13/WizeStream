package org.schabi.newpipe.player

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import java.util.Optional
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

class LiveSilenceSkippingTest {
    private val player = mock(Player::class.java)
    private val engine = mock(ExoPlayer::class.java)
    private val context = mock(Context::class.java)
    private val preferences = mock(SharedPreferences::class.java)
    private val editor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF)
    private lateinit var controller: PlaybackParametersController
    private var savedSkipSilence = true

    @Before
    fun setUp() {
        `when`(player.context).thenReturn(context)
        `when`(player.prefs).thenReturn(preferences)
        `when`(player.getExoPlayer()).thenReturn(engine)
        `when`(player.exoPlayer).thenReturn(engine)
        `when`(player.currentStreamInfo).thenReturn(Optional.empty())
        `when`(engine.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)
        `when`(context.packageName).thenReturn("test")
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences)
        `when`(context.getString(R.string.playback_skip_silence_key)).thenReturn("silence")
        `when`(context.getString(R.string.playback_speed_key)).thenReturn("speed")
        `when`(context.getString(R.string.playback_pitch_key)).thenReturn("pitch")
        `when`(context.getString(R.string.per_channel_playback_profiles_key)).thenReturn("channel")
        `when`(preferences.getFloat(anyString(), anyFloat())).thenReturn(1.0f)
        `when`(preferences.getBoolean(anyString(), anyBoolean())).thenAnswer { call ->
            call.getArgument<String>(0) == "silence" && savedSkipSilence
        }
        `when`(preferences.edit()).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), anyBoolean())).thenAnswer { call ->
            if (call.getArgument<String>(0) == "silence") savedSkipSilence = call.getArgument(1)
            editor
        }
        controller = PlaybackParametersController(player)
    }

    @Test
    fun liveVideoAndAudioSuppressSkippingWithoutErasingThePreference() {
        for (type in listOf(StreamType.LIVE_STREAM, StreamType.AUDIO_LIVE_STREAM)) {
            controller.applySpeedProfile(info(type))
            assertFalse(controller.skipSilenceAvailable)
            // This is the value sent by a disabled checkbox while speed or pitch is edited.
            controller.setParameters(1.0f, 1.1f, false)
            assertTrue(savedSkipSilence)
        }
        verify(editor, org.mockito.Mockito.times(2)).putBoolean("silence", true)
        verify(engine, org.mockito.Mockito.atLeastOnce()).skipSilenceEnabled = false
    }

    @Test
    fun leavingLivePlaybackRestoresSkippingForVideosAndArchives() {
        controller.applySpeedProfile(info(StreamType.LIVE_STREAM))
        for (type in listOf(StreamType.VIDEO_STREAM, StreamType.AUDIO_STREAM, StreamType.POST_LIVE_STREAM, StreamType.POST_LIVE_AUDIO_STREAM)) {
            controller.applySpeedProfile(info(type))
            assertTrue(controller.skipSilenceAvailable)
        }
        verify(engine, org.mockito.Mockito.times(4)).skipSilenceEnabled = true
        assertTrue(savedSkipSilence)
    }

    @Test
    fun disabledPreferenceRemainsDisabledAfterLivePlayback() {
        savedSkipSilence = false
        controller.applySpeedProfile(info(StreamType.LIVE_STREAM))
        controller.setParameters(1.0f, 1.0f, true)
        controller.applySpeedProfile(info(StreamType.VIDEO_STREAM))
        assertFalse(savedSkipSilence)
        verify(engine, org.mockito.Mockito.never()).skipSilenceEnabled = true
    }

    @Test
    fun liveTimelineCoversMissingOrIncorrectExtractorClassification() {
        assertTrue(PlaybackParametersController.isLivePlayback(null, true))
        assertTrue(PlaybackParametersController.isLivePlayback(StreamType.VIDEO_STREAM, true))
        assertFalse(PlaybackParametersController.isLivePlayback(StreamType.POST_LIVE_STREAM, false))
        assertFalse(PlaybackParametersController.isLivePlayback(StreamType.VIDEO_STREAM, false))
    }

    private fun info(type: StreamType): StreamInfo = StreamInfo(0, "test", "https://example.test/$type", "Test").apply {
        streamType = type
    }
}
