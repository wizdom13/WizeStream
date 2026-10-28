package org.schabi.newpipe.player

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.ui.ArchivePresentation
import org.schabi.newpipe.util.StreamCacheLifetime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArchivePlaybackTest {
    @Test fun manifestArchiveKeepsDurationAndSurfaceWithoutAnExtractorQualityList() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.DarkTheme)
        val binding = PlayerBinding.inflate(LayoutInflater.from(context))
        binding.surfaceView.visibility = View.GONE
        binding.playbackEndTime.visibility = View.GONE
        binding.playbackLiveSync.visibility = View.VISIBLE
        ArchivePresentation.show(binding)
        assertEquals(View.VISIBLE, binding.surfaceView.visibility)
        assertEquals(View.VISIBLE, binding.playbackEndTime.visibility)
        assertEquals(View.GONE, binding.playbackLiveSync.visibility)
        assertEquals(View.GONE, binding.endScreen.visibility)
    }

    @Test fun dynamicTimelinesRemainLiveUntilAnActualFiniteArchiveArrives() {
        val engine = mock(Player::class.java)
        `when`(engine.currentMediaItemIndex).thenReturn(0)
        `when`(engine.currentTimeline).thenReturn(SinglePeriodTimeline(C.TIME_UNSET, true, true, true, null, MediaItem.EMPTY))
        assertFalse(ArchivePresentation.isEnded(engine))
        `when`(engine.currentTimeline).thenReturn(SinglePeriodTimeline(60_000_000, true, false, false, null, MediaItem.EMPTY))
        assertTrue(ArchivePresentation.isEnded(engine))
    }

    @Test fun transientStreamMetadataExpiresSoonerThanOrdinaryVideos() {
        val info = mock(StreamInfo::class.java)
        for (type in listOf(StreamType.LIVE_STREAM, StreamType.POST_LIVE_STREAM, StreamType.AUDIO_LIVE_STREAM, StreamType.POST_LIVE_AUDIO_STREAM)) {
            `when`(info.streamType).thenReturn(type)
            assertEquals(30_000L, StreamCacheLifetime.expiration(info, 3_600_000))
            assertEquals(10_000L, StreamCacheLifetime.expiration(info, 10_000))
        }
        `when`(info.streamType).thenReturn(StreamType.VIDEO_STREAM)
        assertEquals(3_600_000L, StreamCacheLifetime.expiration(info, 3_600_000))
    }
}
