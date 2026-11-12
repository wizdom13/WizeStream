package org.schabi.newpipe.player.resolver

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediasource.FullLiveDvrUnavailableException
import org.schabi.newpipe.player.mediasource.YoutubeLiveMediaSource

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class ProtectedLiveSourceTest {
    @Test
    fun missingDashReportsProtectedDvrFailureWithoutConstructingHls() {
        val info = liveInfo()
        val data = mock(PlayerDataSource::class.java)
        val source = requireNotNull(PlaybackResolver.maybeBuildLiveMediaSource(data, info, false, true))
        assertThrows(FullLiveDvrUnavailableException::class.java) { source.maybeThrowSourceInfoRefreshError() }
        assertEquals(info, MediaItemTag.from(source.mediaItem).get().maybeStreamInfo.get())
        verify(data, never()).getLiveHlsMediaSourceFactory()
        verify(data, never()).getLiveYoutubeDashMediaSourceFactory()
    }

    @Test
    fun directFormatsDoNotBypassProtectionWhenDashDisappears() {
        val info = liveInfo().apply { videoStreams = listOf(mock(VideoStream::class.java)) }
        val data = mock(PlayerDataSource::class.java)
        val source = requireNotNull(PlaybackResolver.maybeBuildLiveMediaSource(data, info, false, true))
        assertThrows(FullLiveDvrUnavailableException::class.java) { source.maybeThrowSourceInfoRefreshError() }
        verify(data, never()).getLiveHlsMediaSourceFactory()
    }

    @Test
    fun staleHlsPreferenceCannotReplaceProtectedDash() {
        val info = liveInfo().apply { dashMpdUrl = "https://example.test/live.mpd" }
        val data = dataSource()
        assertTrue(PlaybackResolver.maybeBuildLiveMediaSource(data, info, true, true) is YoutubeLiveMediaSource)
        verify(data).getLiveYoutubeDashMediaSourceFactory()
    }

    @Test
    fun explicitNormalLiveSelectionCanStillChooseHls() {
        val info = liveInfo()
        val data = dataSource()
        `when`(data.isNormalLive(info.url)).thenReturn(true)
        assertTrue(PlaybackResolver.maybeBuildLiveMediaSource(data, info, false, true) is HlsMediaSource)
        verify(data, never()).getLiveYoutubeDashMediaSourceFactory()
    }

    @Test
    fun initialHlsOnlyBroadcastWithoutEstablishedDvrRemainsPlayable() {
        assertTrue(PlaybackResolver.maybeBuildLiveMediaSource(dataSource(), liveInfo(), false, false) is HlsMediaSource)
    }

    private fun dataSource(): PlayerDataSource = mock(PlayerDataSource::class.java).apply {
        `when`(getLiveHlsMediaSourceFactory()).thenReturn(HlsMediaSource.Factory(DefaultHttpDataSource.Factory()))
        `when`(getLiveYoutubeDashMediaSourceFactory()).thenReturn(DashMediaSource.Factory(DefaultHttpDataSource.Factory()))
    }

    private fun liveInfo() = StreamInfo(ServiceList.YouTube.serviceId, "live", "https://example.test/watch", "Live").apply {
        streamType = StreamType.LIVE_STREAM
        hlsUrl = "https://example.test/live.m3u8"
    }
}
