package org.schabi.newpipe.player

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.preference.PreferenceManager
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.mediaitem.MediaItemTag.AudioTrack
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.video.VideoAdjustmentController

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28, 35])
class AudioDecoderOutputRecoveryTest {
    @Test
    fun audioCodecOutputFailureRebuildsOnceWithoutDiscardingTheQueue() {
        val f = Fixture()
        `when`(f.player.videoAdjustments).thenReturn(mock(VideoAdjustmentController::class.java))

        f.controller.onPlayerError(f.error())

        verify(f.player).saveStreamProgressState()
        verify(f.player).setRecovery()
        verify(f.player).onBuffering()
        verify(f.player).restartForAudioTrackRecovery()
        verify(f.player, never()).onPlaybackShutdown()
        verify(f.queue, never()).error()
        verify(f.queue, never()).dispose()
        assertFalse(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))

        f.controller.resetRecovery()
        assertTrue(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
        verify(f.player, times(2)).restartForAudioTrackRecovery()
    }

    @Test
    fun classificationRequiresAudioDecodingCodeAndMediaCodecOutputFrame() {
        val error = Fixture().error()
        assertTrue(
            PlayerHttpErrorRecovery.isRecoverableAudioDecoderOutputFailure(
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                C.TRACK_TYPE_AUDIO,
                error
            )
        )
        assertTrue(
            PlayerHttpErrorRecovery.isRecoverableAudioDecoderOutputFailure(
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                C.TRACK_TYPE_AUDIO,
                Fixture().error("getOutputBuffer")
            )
        )
        assertFalse(
            PlayerHttpErrorRecovery.isRecoverableAudioDecoderOutputFailure(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                C.TRACK_TYPE_AUDIO,
                error
            )
        )
        assertFalse(
            PlayerHttpErrorRecovery.isRecoverableAudioDecoderOutputFailure(
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                C.TRACK_TYPE_VIDEO,
                error
            )
        )

        assertTrue(
            PlayerHttpErrorRecovery.isRecoverableAudioDecoderOutputFailure(
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                C.TRACK_TYPE_AUDIO,
                Fixture().error("dequeueOutputBuffer")
            )
        )

        val unrelated = IllegalStateException("codec state").apply {
            stackTrace = arrayOf(
                StackTraceElement(
                    "android.media.MediaCodec",
                    "flush",
                    "MediaCodec.java",
                    -2
                )
            )
        }
        assertFalse(
            PlayerHttpErrorRecovery.isRecoverableAudioDecoderOutputFailure(
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                C.TRACK_TYPE_AUDIO,
                unrelated
            )
        )
    }

    @Test
    fun missingQueueItemAndReleasedEngineDoNotConsumeTheRetry() {
        val f = Fixture()
        `when`(f.queue.item).thenReturn(null)
        assertFalse(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
        `when`(f.queue.item).thenReturn(f.item)
        `when`(f.player.exoPlayerIsNull()).thenReturn(true)
        assertFalse(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
        `when`(f.player.exoPlayerIsNull()).thenReturn(false)
        assertTrue(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
    }

    @Test
    fun webmRecoveryKeepsTheSelectedAudioTrackAndClearsWithPlaybackRecovery() {
        val f = Fixture()
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(context.getString(R.string.default_audio_format_key), context.getString(R.string.audio_m4a_key))
            .apply()
        val resolver = mock(AudioPlaybackResolver::class.java)
        val streamController = PlayerStreamController(
            f.player,
            context,
            resolver,
            mock(VideoPlaybackResolver::class.java),
            mock(PlayerDataSource::class.java)
        )
        val info = mock(StreamInfo::class.java)
        val aac = audio(MediaFormat.M4A, "en")
        val opus = audio(MediaFormat.WEBMA_OPUS, "en")
        val otherLanguage = audio(MediaFormat.WEBMA_OPUS, "fr")
        val url = f.item.url
        `when`(f.player.audioPlayerSelected()).thenReturn(true)
        `when`(f.player.currentStreamInfo).thenReturn(Optional.of(info))
        `when`(f.player.selectedAudioStream).thenReturn(Optional.of(aac))
        `when`(resolver.audioTrack).thenReturn("en")
        `when`(info.url).thenReturn(url)
        `when`(info.streamType).thenReturn(StreamType.VIDEO_STREAM)
        `when`(info.audioStreams).thenReturn(listOf(aac, opus, otherLanguage))

        streamController.enableAudioDecoderFallback(f.item)
        streamController.sourceOf(info)

        val selection = ArgumentCaptor.forClass(AudioTrack::class.java)
        verify(resolver).resolve(eq(info), selection.capture())
        assertEquals(opus, selection.value.selectedAudioStream)
        assertTrue(selection.value.audioStreams.contains(otherLanguage))

        streamController.clearAudioDecoderFallback()
        streamController.sourceOf(info)
        verify(resolver).resolve(info, null)
    }

    @Test
    fun audioLanguageSwitchesHaveSeparateBoundedAttempts() {
        val f = Fixture()
        val english = audio(MediaFormat.M4A, "en.original")
        val french = audio(MediaFormat.M4A, "fr.dubbed")
        `when`(f.player.selectedAudioStream).thenReturn(Optional.of(english))
        assertTrue(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
        assertFalse(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))

        `when`(f.player.selectedAudioStream).thenReturn(Optional.of(french))
        assertTrue(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
        assertFalse(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))

        // Switching back does not replenish the English track's attempt, even after using Opus.
        val englishOpus = audio(MediaFormat.WEBMA_OPUS, "en.original")
        `when`(f.player.selectedAudioStream).thenReturn(Optional.of(englishOpus))
        assertFalse(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
        `when`(f.player.selectedAudioStream).thenReturn(Optional.of(english))
        assertFalse(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
        verify(f.player, times(2)).restartForAudioTrackRecovery()

        `when`(f.item.url).thenReturn("https://www.youtube.com/watch?v=next")
        assertTrue(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
        f.controller.resetRecovery()
        assertTrue(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
    }

    @Test
    fun switchingBackRetainsEachLanguagesOpusFallbackWithoutChangingOtherTracks() {
        val f = StreamFixture()
        val frenchAac = audio(MediaFormat.M4A, "fr")
        val frenchOpus = audio(MediaFormat.WEBMA_OPUS, "fr")
        val description = audio(MediaFormat.M4A, "en.description")
        `when`(f.info.audioStreams).thenReturn(listOf(f.aac, f.opus, frenchAac, frenchOpus, description))
        f.controller.enableAudioDecoderFallback(f.item)
        f.controller.sourceOf(f.info)

        `when`(f.player.selectedAudioStream).thenReturn(Optional.of(frenchAac))
        `when`(f.resolver.audioTrack).thenReturn("fr")
        f.controller.enableAudioDecoderFallback(f.item)
        f.controller.sourceOf(f.info)

        `when`(f.resolver.audioTrack).thenReturn("en")
        f.controller.sourceOf(f.info)
        val selection = ArgumentCaptor.forClass(AudioTrack::class.java)
        verify(f.resolver, times(3)).resolve(eq(f.info), selection.capture())
        assertEquals(listOf(f.opus, frenchOpus, f.opus), selection.allValues.map { it.selectedAudioStream })
        selection.allValues.forEach { assertTrue(it.audioStreams.contains(description)) }

        f.controller.clearAudioDecoderFallback()
        f.controller.sourceOf(f.info)
        verify(f.resolver).resolve(f.info, null)
    }

    @Test
    fun missingSameTrackOpusNeverFallsBackToAnotherLanguageOrAnotherVideo() {
        val f = StreamFixture()
        val french = audio(MediaFormat.M4A, "fr")
        `when`(f.info.audioStreams).thenReturn(listOf(f.aac, f.opus, french))
        f.controller.enableAudioDecoderFallback(f.item)
        `when`(f.player.selectedAudioStream).thenReturn(Optional.of(french))
        `when`(f.resolver.audioTrack).thenReturn("fr")
        f.controller.enableAudioDecoderFallback(f.item)
        f.controller.sourceOf(f.info)
        verify(f.resolver).resolve(f.info, null)

        `when`(f.resolver.audioTrack).thenReturn("en")
        `when`(f.info.url).thenReturn("https://www.youtube.com/watch?v=other")
        f.controller.sourceOf(f.info)
        verify(f.resolver, times(2)).resolve(f.info, null)
    }

    private inner class StreamFixture {
        val player = mock(Player::class.java)
        val item = mock(PlayQueueItem::class.java)
        val info = mock(StreamInfo::class.java)
        val resolver = mock(AudioPlaybackResolver::class.java)
        val context = RuntimeEnvironment.getApplication()
        val controller = PlayerStreamController(
            player,
            context,
            resolver,
            mock(VideoPlaybackResolver::class.java),
            mock(PlayerDataSource::class.java)
        )
        val aac = audio(MediaFormat.M4A, "en")
        val opus = audio(MediaFormat.WEBMA_OPUS, "en")

        init {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.default_audio_format_key), context.getString(R.string.audio_m4a_key))
                .apply()
            `when`(item.url).thenReturn("https://www.youtube.com/watch?v=test")
            `when`(info.url).thenReturn("https://www.youtube.com/watch?v=test")
            `when`(info.streamType).thenReturn(StreamType.VIDEO_STREAM)
            `when`(info.audioStreams).thenReturn(listOf(aac, opus))
            `when`(player.audioPlayerSelected()).thenReturn(true)
            `when`(player.currentStreamInfo).thenReturn(Optional.of(info))
            `when`(player.selectedAudioStream).thenReturn(Optional.of(aac))
            `when`(resolver.audioTrack).thenReturn("en")
        }
    }

    private fun audio(format: MediaFormat, trackId: String): AudioStream = mock(AudioStream::class.java).also {
        `when`(it.format).thenReturn(format)
        `when`(it.audioTrackId).thenReturn(trackId)
    }

    private class Fixture {
        val player = mock(Player::class.java)
        val queue = mock(PlayQueue::class.java)
        val item = mock(PlayQueueItem::class.java)
        val controller = PlayerErrorController(
            player,
            mock(PlayerEventDispatcher::class.java),
            mock(VideoPlaybackResolver::class.java)
        )

        init {
            `when`(player.playQueue).thenReturn(queue)
            `when`(queue.item).thenReturn(item)
            `when`(item.url).thenReturn("https://www.youtube.com/watch?v=test")
            `when`(item.serviceId).thenReturn(0)
        }

        fun error(codecMethod: String = "releaseOutputBuffer"): PlaybackException {
            val codecState = IllegalStateException("codec output state").apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "android.media.MediaCodec",
                        codecMethod,
                        "MediaCodec.java",
                        -2
                    )
                )
            }
            return PlaybackException(
                "MediaCodecAudioRenderer error",
                RuntimeException("Decoder failed: OMX.google.aac.decoder", codecState),
                PlaybackException.ERROR_CODE_DECODING_FAILED
            )
        }
    }
}
